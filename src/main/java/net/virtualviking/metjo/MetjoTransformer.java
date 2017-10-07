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

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by prydin on 10/4/17.
 */
public class MetjoTransformer implements ClassFileTransformer {
    static public final class CapturedParameter {
        private final int index;

        private final String name;

        private Histogram receiver;

        public CapturedParameter(int index, String name, Histogram receiver) {
            this.index = index;
            this.name = name;
            this.receiver = receiver;
        }

        public int getIndex() {
            return index;
        }

        public String getName() {
            return name;
        }

        public Histogram getReceiver() {
            return receiver;
        }

    }

    private final MetricRegistry registry;
    private final WildcardFileFilter[] includes;
    private final WildcardFileFilter[] excludes;
    private final Map<String, List<CapturedParameter>> capturedParameters = new HashMap<>();

    private static final MessageFormat methodEntryProbe = new MessageFormat(
            "net.virtualviking.metjo.MethodEntryListener.onMethodEntry(\"{0}\", \"{1}\", {2}, {3});");

    private static final MessageFormat methodExitProbe = new MessageFormat(
            "net.virtualviking.metjo.MethodEntryListener.onMethodExit();");


    public MetjoTransformer(MetricRegistry registry, List<String> includes, List<String> excludes, List<Map<String, String>> parameters) {
        this.registry = registry;
        this.includes = createFilters(includes);
        this.excludes = createFilters(excludes);
        if (parameters != null) {
            for (Map<String, String> p : parameters) {
                String name = p.get("name");
                String param = p.get("parameter");
                int i = param.lastIndexOf('.');
                if (i == -1) {
                    System.err.println("WARNING: Invalid syntax of parameter metric. Skipping. Parameter: " + param);
                    continue;
                }
                try {
                    String method = param.substring(0, i);
                    String index = param.substring(i + 1);
                    int paramIndex = Integer.valueOf(index);
                    List<CapturedParameter> entry = capturedParameters.get(method);
                    if (entry == null) {
                        entry = new ArrayList<>();
                        capturedParameters.put(method, entry);
                    }
                    Histogram receiver = registry.histogram(name);
                    entry.add(new CapturedParameter(paramIndex, name, receiver));
                    MethodEntryListener.setCapturedParameters(capturedParameters);
                } catch (NumberFormatException e) {
                    System.err.println("WARNING: Last part of parameter specifier must be integer. Skipping. Parameter: " + param);
                    continue;
                }
            }
        }
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
                    touched |= instrument(behavior, absolute ? fullMethodName : behavior.getName(), fullMethodName);
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

    private boolean instrument(CtBehavior behavior, String methodName, String fullMethodName)
            throws CannotCompileException {
        Object entryArgs = new Object[]{ methodName, fullMethodName, "$args", capturedParameters.containsKey(fullMethodName) };
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