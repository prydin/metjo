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

import com.codahale.metrics.MetricRegistry;
import net.virtualviking.metjo.reporters.ConsoleFactory;
import net.virtualviking.metjo.reporters.WavefrontFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.lang.instrument.Instrumentation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class Agent {
    private static final HashMap<String, ReporterFactory> factories = new HashMap<>();

    static {
        factories.put("wavefront", new WavefrontFactory());
        factories.put("console", new ConsoleFactory());
    }

    public static void premain(String agentArgs, Instrumentation inst) throws Exception {
        String configFile = System.getenv("METJO_CONFIG");
        if(configFile == null) {
            System.err.println("INFO: Property metjo.config was not specified. Profiling is disabled.");
            return;
        }
        FileInputStream in = new FileInputStream(configFile);
        Yaml y = new Yaml();
        Map<Object, Object> config = (Map<Object, Object>) y.load(in);
        in.close();
        String reporter = (String) config.get("reporter");
        ReporterFactory rf = factories.get(reporter);
        if(rf == null) {
            System.err.println("WARNING: Unknown reporter. Profiling is disabled.");
            return;
        }
        MetricRegistry registry = new MetricRegistry();
        rf.makeReporter(registry, (Map<Object, Object>) config.get("properties"));

        List<String> inc = (List<String>) config.get("includes");
        if(inc == null) {
            inc = Collections.EMPTY_LIST;
        }
        List<String> ex = (List<String>) config.get("excludes");
        if(ex == null) {
            ex = Collections.EMPTY_LIST;
        }

        inst.addTransformer(new MetjoTransformer(inc, ex));
        MethodEntryListener.setMetricRegistry(registry);
    }
}
