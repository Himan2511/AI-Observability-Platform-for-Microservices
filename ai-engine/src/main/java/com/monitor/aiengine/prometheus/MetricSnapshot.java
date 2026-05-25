package com.monitor.aiengine.prometheus;

import java.util.HashMap;
import java.util.Map;

public class MetricSnapshot {
    private String serviceName;
    private long timestamp;
    private Map<String, Double> metrics = new HashMap<>();

    public MetricSnapshot() {}

    public MetricSnapshot(String serviceName, long timestamp) {
        this.serviceName = serviceName;
        this.timestamp = timestamp;
    }

    public void addMetric(String name, Double value) {
        metrics.put(name, value);
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, Double> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Double> metrics) {
        this.metrics = metrics;
    }
}
