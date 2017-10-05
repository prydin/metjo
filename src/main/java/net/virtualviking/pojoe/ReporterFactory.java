package net.virtualviking.pojoe;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;

import java.util.Map;

public interface ReporterFactory {
    Reporter makeReporter(MetricRegistry registry, Map<Object, Object> config) throws MetjoException;
}
