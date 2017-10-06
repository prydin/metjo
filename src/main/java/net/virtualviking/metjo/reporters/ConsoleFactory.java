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

package net.virtualviking.metjo.reporters;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import net.virtualviking.metjo.MetjoException;
import net.virtualviking.metjo.ReporterFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public class ConsoleFactory implements ReporterFactory {
    @Override
    public Reporter makeReporter(MetricRegistry registry, Map<Object, Object> config) throws MetjoException {
        try {
            ConsoleReporter.Builder bld = ConsoleReporter.forRegistry(registry);
            String tz = (String) config.get("timezone");
            if (tz != null) {
                bld.formattedFor(TimeZone.getTimeZone(tz));
            }
            String locale = (String) config.get("locale");
            if (locale != null) {
                bld.formattedFor(Locale.forLanguageTag(locale));
            }
            String timeUnit = (String) config.get("durationTimeUnit");
            if (timeUnit != null) {
                bld.convertDurationsTo(TimeUnit.valueOf(timeUnit));
            }
            timeUnit = (String) config.get("rateTimeUnit");
            if (timeUnit != null) {
                bld.convertRatesTo(TimeUnit.valueOf(timeUnit));
            }
            PrintStream ps;
            String output = (String) config.get("output");
            if (output == null || "stderr".equals(output)) {
                ps = System.err;
            } else if ("stdout".equals(output)) {
                ps = System.out;
            } else {
                ps = new PrintStream(new FileOutputStream(output));
            }
            bld.outputTo(ps);
            Integer period = (Integer) config.get("period");
            if (period == null) {
                period = 20;
            }
            ConsoleReporter cr = bld.build();
            cr.start(period, TimeUnit.SECONDS);
            return cr;
        } catch(IOException e) {
            throw new MetjoException("Error creating ConsoleReporter", e);
        }
    }
}
