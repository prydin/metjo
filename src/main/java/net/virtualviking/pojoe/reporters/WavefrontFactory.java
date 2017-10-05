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

package net.virtualviking.pojoe.reporters;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import com.wavefront.integrations.metrics.WavefrontReporter;
import net.virtualviking.pojoe.MetjoException;
import net.virtualviking.pojoe.ReporterFactory;

import java.sql.Time;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class WavefrontFactory implements ReporterFactory {
    @Override
    public Reporter makeReporter(MetricRegistry registry, Map<Object, Object> config) throws MetjoException {
        WavefrontReporter.Builder bld = WavefrontReporter.forRegistry(registry);
        if(config.get("jvmMetrics") == Boolean.TRUE) {
            bld.withJvmMetrics();
        }
        String proxy = (String) config.get("proxy");
        if(proxy == null) {
            throw new MetjoException("Proxy must be specified");
        }
        Integer port = (Integer) config.get("port");
        if(port == null) {
            port = 2878; // Default
        }
        Integer period = (Integer) config.get("period");
        if(period == null) {
            period = 20;
        }
        Map<Object, Object> pts = (Map<Object, Object>) config.get("pointtags");
        if(pts != null) {
            for(Map.Entry<Object, Object> e : pts.entrySet()) {
                bld.withPointTag(e.getKey().toString(), e.getValue().toString());
            }
        }
        WavefrontReporter rep = bld.build(proxy, port);
        rep.start(period, TimeUnit.SECONDS);
        return rep;
    }
}
