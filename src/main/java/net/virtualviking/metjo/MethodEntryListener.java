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

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.codahale.metrics.annotation.Gauge;

import java.util.List;
import java.util.Map;
import java.util.Stack;

public class MethodEntryListener {

    private static class ThreadData {
        private final Stack<Timer.Context> contextStack = new Stack<>();
        private boolean inProbe;
    }

    private static ThreadLocal<ThreadData> threadData = new ThreadLocal<>();

    private static MetricRegistry registry;

    private static Map<String, List<MetjoTransformer.CapturedParameter>> capturedParameters;

    public static void setMetricRegistry(MetricRegistry r) {
        registry = r;
    }

    public static void setCapturedParameters(Map<String, List<MetjoTransformer.CapturedParameter>> p) {
        capturedParameters = p;
    }

    public static void onMethodEntry(String method, String fullMethodName, Object[] parameters, boolean hasCapturedParameter) {
        ThreadData td = threadData.get();
        if(td == null) {
            td = new ThreadData();
            threadData.set(td);
        }

        // Are we reentering the probe because we called a probed method from within the probe?
        // Get us out of here to prevent infinite recursion!
        //
        if(td.inProbe)
            return;
        td.inProbe = true;
        try {
            // Create timer context
            //
            Timer t = registry.timer(method);
            td.contextStack.push(t.time());

            // Capture parameters if needed
            //
            if(hasCapturedParameter) {
                List<MetjoTransformer.CapturedParameter> cps = capturedParameters.get(method);
                if(cps != null) {
                    for (MetjoTransformer.CapturedParameter cp : cps) {
                        Object o = parameters[cp.getIndex()];
                        if (!(o instanceof Number)) {
                            continue;
                        }
                        cp.getReceiver().update(((Number) o).longValue());
                    }
                }
            }
        } finally {
            td.inProbe = false;
        }
    }

    public static void onMethodExit() {
        ThreadData td = threadData.get();
        if(td == null || td.contextStack.size() == 0) {
            System.err.println("WARNING: Method exit without entry");
        }
        Timer.Context t = td.contextStack.pop();
        t.stop();
    }

}
