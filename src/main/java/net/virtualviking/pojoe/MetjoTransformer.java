package net.virtualviking.pojoe;

import javassist.*;
import org.apache.commons.io.filefilter.WildcardFileFilter;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.text.MessageFormat;

/**
 * Created by prydin on 10/4/17.
 */
public class MetjoTransformer implements ClassFileTransformer {
    private WildcardFileFilter[] includes;
    private WildcardFileFilter[] excludes;

    private static final MessageFormat methodEntryProbe = new MessageFormat(
            "net.virtualviking.pojoe.MethodEntryListener.onMethodEntry(\"{0}\", {1}, {2});");

    private static final MessageFormat methodExitProbe = new MessageFormat(
            "net.virtualviking.pojoe.MethodEntryListener.onMethodExit({0});");


    public MetjoTransformer(String[] includes, String[] excludes) {
        this.includes = createFilters(includes);
        this.excludes = createFilters(excludes);
    }

    private static WildcardFileFilter[] createFilters(String[] strings) {
        WildcardFileFilter[] result = new WildcardFileFilter[strings.length];
        for(int idx = 0; idx < strings.length; ++idx) {
            result[idx] = new WildcardFileFilter(strings[idx]);
        }
        return result;
    }

    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
        try {
            ClassPool pool = ClassPool.getDefault();
            CtClass clazz = pool.makeClass(new ByteArrayInputStream(classfileBuffer));
            boolean touched = false;
            for (CtBehavior behavior : clazz.getDeclaredBehaviors()) {
                if (behavior.isEmpty()
                       || Modifier.isNative(behavior.getModifiers())) {
                    continue;
                }
                String fullMethodName = className.replace('/', '.') + "." + behavior.getName();
                if(!match(includes, fullMethodName) || match(excludes, fullMethodName)) {
                    continue;
                }
                System.err.println("Instrumenting method: " + fullMethodName);
                touched |= instrument(behavior, fullMethodName);
            }
            return touched ? clazz.toBytecode() : null;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during bytecode instrumentation");
        } catch (CannotCompileException e) {
            e.printStackTrace();
            throw new RuntimeException("Error during bytecode instrumentation");
        }
    }

    private boolean instrument(CtBehavior behavior, String methodName)
            throws CannotCompileException {
        boolean dollarZeroAvailable = (behavior instanceof CtMethod)
                && !Modifier.isStatic(behavior.getModifiers());
        Object entryArgs = new Object[]{methodName, dollarZeroAvailable ? "$0" : "null", "$args"};
        behavior.insertBefore(methodEntryProbe.format(entryArgs));

        Object exitArgs = new Object[] {"($w)$_"};
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