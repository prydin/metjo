package net.virtualviking.pojoe;

import com.codahale.metrics.MetricRegistry;
import com.wavefront.integrations.metrics.WavefrontReporter;
import net.virtualviking.pojoe.reporters.WavefrontFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.lang.instrument.Instrumentation;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by prydin on 10/4/17.
 */
public class Agent {
    private static final HashMap<String, ReporterFactory> factories = new HashMap<>();

    static {
        factories.put("wavefront", new WavefrontFactory());
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
        inst.addTransformer(new MetjoTransformer(new String[] { "com.ebberod.*", "java.util.HashMap.*" }, new String[0] ));
        MethodEntryListener.setMetricRegistry(registry);
    }
}
