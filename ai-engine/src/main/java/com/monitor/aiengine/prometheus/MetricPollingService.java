package com.monitor.aiengine.prometheus;

import com.monitor.aiengine.entity.ServiceRegistry;
import com.monitor.aiengine.repository.ServiceRegistryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class MetricPollingService {
    private static final Logger log = LoggerFactory.getLogger(MetricPollingService.class);

    private final PrometheusClient prometheusClient;
    private final MetricCacheService metricCacheService;
    private final ServiceRegistryRepository serviceRegistryRepository;

    public MetricPollingService(PrometheusClient prometheusClient, 
                                MetricCacheService metricCacheService, 
                                ServiceRegistryRepository serviceRegistryRepository) {
        this.prometheusClient = prometheusClient;
        this.metricCacheService = metricCacheService;
        this.serviceRegistryRepository = serviceRegistryRepository;
    }

    @Scheduled(fixedRate = 30000)
    public void pollMetrics() {
        log.info("Polling metrics from Prometheus...");
        List<ServiceRegistry> services = serviceRegistryRepository.findAll();
        
        for (ServiceRegistry service : services) {
            if (!service.getActive()) continue;
            String name = service.getName();
            
            // Extract the simple app name (e.g., 'gateway' from 'service-gateway') for queries
            String appName = name.replace("service-", "");
            
            MetricSnapshot snapshot = new MetricSnapshot(name, Instant.now().toEpochMilli());
            
            // 1. CPU Usage %
            String cpuQuery = String.format("rate(process_cpu_usage{job=\"%s\"}[1m]) * 100", appName);
            snapshot.addMetric("process_cpu_usage_percent", prometheusClient.queryMetric(cpuQuery));
            
            // 2. Heap Memory %
            String heapQuery = String.format("jvm_memory_used_bytes{area=\"heap\", job=\"%s\"} / jvm_memory_max_bytes{area=\"heap\", job=\"%s\"} * 100", appName, appName);
            snapshot.addMetric("jvm_heap_usage_percent", prometheusClient.queryMetric(heapQuery));
            
            // 3. HTTP Error Rate %
            String errorQuery = String.format("rate(http_server_requests_seconds_count{status=~\"5..\", job=\"%s\"}[1m]) / rate(http_server_requests_seconds_count{job=\"%s\"}[1m]) * 100", appName, appName);
            snapshot.addMetric("http_error_rate_percent", prometheusClient.queryMetric(errorQuery));
            
            // 4. Request Latency p95 ms
            String latencyQuery = String.format("histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{job=\"%s\"}[5m])) * 1000", appName);
            snapshot.addMetric("http_request_latency_p95_ms", prometheusClient.queryMetric(latencyQuery));

            // Custom Metrics based on service
            if ("search".equals(appName)) {
                snapshot.addMetric("search_queries_slow_total", prometheusClient.queryMetric("rate(search_queries_slow_total[1m])"));
            } else if ("media".equals(appName)) {
                snapshot.addMetric("media_processing_active", prometheusClient.queryMetric("media_processing_active"));
            } else if ("recommendation".equals(appName)) {
                String cacheMissQuery = "rate(recommendation_cache_misses_total[5m]) / (rate(recommendation_cache_hits_total[5m]) + rate(recommendation_cache_misses_total[5m])) * 100";
                snapshot.addMetric("recommendation_cache_miss_rate_percent", prometheusClient.queryMetric(cacheMissQuery));
            }
            
            metricCacheService.saveSnapshot(snapshot);
            log.debug("Saved metrics snapshot for {}", name);
        }
    }
}
