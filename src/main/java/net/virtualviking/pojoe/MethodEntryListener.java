package net.virtualviking.pojoe;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import java.util.Stack;

/**
 * Created by prydin on 10/4/17.
 */
public class MethodEntryListener {

    private static class ThreadData {
        private final Stack<Timer.Context> contextStack = new Stack<>();
        private boolean inProbe;
    }

    private static ThreadLocal<ThreadData> threadData = new ThreadLocal<>();

    private static MetricRegistry registry;

    public static void setMetricRegistry(MetricRegistry r) {
        registry = r;
    }

    public static void onMethodEntry(String method, Object context, Object[] arguments) {
        ThreadData td = threadData.get();
        if(td == null) {
            td = new ThreadData();
            threadData.set(td);
        }

        // Are we reentering the probe because we called a probed method from within the probe?
        // Get us out of here to prevent infinite recursion!
        if(td.inProbe)
            return;
        td.inProbe = true;
        try {
           Timer t = registry.timer(method);
            td.contextStack.push(t.time());
        } finally {
            td.inProbe = false;
        }
    }

    public static void onMethodExit(Object returnValue) {
        ThreadData td = threadData.get();
        if(td == null || td.contextStack.size() == 0) {
            System.err.println("WARNING: Method exit without entry");
        }
        Timer.Context t = td.contextStack.pop();
        t.stop();
    }

}
