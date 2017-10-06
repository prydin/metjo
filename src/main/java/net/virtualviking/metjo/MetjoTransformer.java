/*
 *  Copyright 2017 Pontus Rydin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package net.virtualviking.metjo;

import com.codahale.metrics.annotation.Timed;
import javassist.*;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.List;

/**
 * Created by prydin on 10/4/17.
 */
public class MetjoTransformer implements ClassFileTransformer {
    private WildcardFileFilter[] includes;
    private WildcardFileFilter[] excludes;

    private static final MessageFormat methodEntryProbe = new MessageFormat(
            "net.virtualviking.metjo.MethodEntryListener.onMethodEntry(\"{0}\");");

    private static final MessageFormat methodExitProbe = new MessageFormat(
            "net.virtualviking.metjo.MethodEntryListener.onMethodExit();");


    public MetjoTransformer(List<String> includes, List<String> excludes) {
        this.includes = createFilters(includes);
        this.excludes = createFilters(excludes);
    }

    private static WildcardFileFilter[] createFilters(List<String> strings) {
        WildcardFileFilter[] result = new WildcardFileFilter[strings.size()];
        for(int idx = 0; idx < strings.size(); ++idx) {
            result[idx] = new WildcardFileFilter(strings.get(idx));
        }
        return result;
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass clazz = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
            //System.err.println(clazz.getName());
            boolean touched = false;
            for (CtBehavior behavior : clazz.getDeclaredBehaviors()) {
                if (behavior.isEmpty()
                        || Modifier.isNative(behavior.getModifiers())
                        || behavior.getName().equals("<clinit>")
                        || behavior.getName().startsWith("access$")) {
                    continue;
                }
                boolean absolute = true;
                boolean annotated = behavior.hasAnnotation(Timed.class);
                if(annotated) {
                    absolute = ((Timed) behavior.getAnnotation(Timed.class)).absolute();
                }
                String fullMethodName = className.replace('/', '.') + "." + behavior.getName();
                //System.err.println(fullMethodName + " " + annotated);
                if(!(annotated
                        || (match(includes, fullMethodName)
                        && !match(excludes, fullMethodName)))) {
                    continue;
                }

                System.err.println("Instrumenting method: " + fullMethodName + " mods=" + behavior.getModifiers());
                try {
                    touched |= instrument(behavior, absolute ? fullMethodName : behavior.getName());
                } catch (CannotCompileException e) {
                    System.err.println("Instrumentation failed: " + e.getMessage());
                }
            }
            return touched ? clazz.toBytecode() : null;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error instrumenting class " + className);
        }
        catch (CannotCompileException e) {
            System.err.println("Instrumentation failed: " + e.getMessage());
            throw new RuntimeException("Error instrumenting class " + className);
        }
        catch (ClassNotFoundException e) {
            System.err.println("Instrumentation failed: " + e.getMessage());
            throw new RuntimeException("Error instrumenting class " + className);
        }
    }

    private boolean instrument(CtBehavior behavior, String methodName)
            throws CannotCompileException {
        Object entryArgs = new Object[]{ methodName };
        behavior.insertBefore(methodEntryProbe.format(entryArgs));

        Object exitArgs = new Object[0];
        behavior.insertAfter(methodExitProbe.format(exitArgs), true);

        return true;
    }

    private boolean match(WildcardFileFilter[] patterns, String name) {
        File f = new File(name);
        for(WildcardFileFilter p : patterns) {
            if(p.accept(f))
                return true;
        }
        return false;
    }
}