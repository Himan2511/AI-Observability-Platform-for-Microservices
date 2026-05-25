package com.monitor.aiengine.anomaly;

import com.monitor.aiengine.entity.AlertRule;
import com.monitor.aiengine.entity.Incident;
import com.monitor.aiengine.prometheus.MetricCacheService;
import com.monitor.aiengine.repository.AlertRuleRepository;
import com.monitor.aiengine.repository.IncidentRepository;
import com.monitor.aiengine.websocket.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AnomalyDetectionEngine {

    private static final Logger log = LoggerFactory.getLogger(AnomalyDetectionEngine.class);

    private final AlertRuleRepository alertRuleRepository;
    private final IncidentRepository incidentRepository;
    private final MetricCacheService metricCacheService;
    private final AiAnalysisService aiAnalysisService;
    private final NotificationService notificationService;

    public AnomalyDetectionEngine(AlertRuleRepository alertRuleRepository, 
                                  IncidentRepository incidentRepository, 
                                  MetricCacheService metricCacheService,
                                  AiAnalysisService aiAnalysisService,
                                  NotificationService notificationService) {
        this.alertRuleRepository = alertRuleRepository;
        this.incidentRepository = incidentRepository;
        this.metricCacheService = metricCacheService;
        this.aiAnalysisService = aiAnalysisService;
        this.notificationService = notificationService;
    }

    @Scheduled(fixedRate = 30000)
    public void detectAnomalies() {
        log.debug("Running anomaly detection engine...");
        
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();

        for (AlertRule rule : rules) {
            String serviceName = rule.getServiceName();
            
            // If rule is global, we should check against all services.
            // For simplicity in this implementation, we handle service-specific rules here.
            // (A fully robust system would loop through all registered services if serviceName is null).
            if (serviceName == null) continue; 
            
            Double currentValue = metricCacheService.getMetric(serviceName, rule.getMetricName());
            
            if (currentValue != null && isBreached(currentValue, rule)) {
                handleBreach(serviceName, rule, currentValue);
            }
        }
    }

    private boolean isBreached(Double currentValue, AlertRule rule) {
        return switch (rule.getOperator().toUpperCase()) {
            case "GT" -> currentValue > rule.getThreshold();
            case "LT" -> currentValue < rule.getThreshold();
            case "GTE" -> currentValue >= rule.getThreshold();
            case "LTE" -> currentValue <= rule.getThreshold();
            case "EQ" -> currentValue.equals(rule.getThreshold());
            default -> false;
        };
    }

    private void handleBreach(String serviceName, AlertRule rule, Double currentValue) {
        String anomalyType = extractAnomalyType(rule);
        
        Optional<Incident> existingIncident = incidentRepository.findTopByServiceNameAndAnomalyTypeAndStatusOrderByDetectedAtDesc(
                serviceName, anomalyType, "OPEN");

        if (existingIncident.isPresent()) {
            // Update existing incident with latest metric
            Incident incident = existingIncident.get();
            incident.setMetricValue(currentValue);
            incidentRepository.save(incident);
        } else {
            // Create new incident
            Incident incident = new Incident();
            incident.setServiceName(serviceName);
            incident.setAnomalyType(anomalyType);
            incident.setSeverity(rule.getSeverity());
            incident.setStatus("OPEN");
            incident.setMetricValue(currentValue);
            incident.setThresholdValue(rule.getThreshold());
            incident.setDescription(rule.getDescription());
            
            Incident savedIncident = incidentRepository.save(incident);
            log.warn("New Anomaly Detected! Service: {}, Rule: {}, Value: {}", serviceName, rule.getMetricName(), currentValue);
            
            // Push WebSocket Notification
            notificationService.notifyNewIncident(savedIncident);
            
            // Trigger AI root cause analysis asynchronously
            aiAnalysisService.analyzeIncidentAsync(savedIncident);
        }
    }

    private String extractAnomalyType(AlertRule rule) {
        if (rule.getMetricName().contains("cpu")) return "CPU_SPIKE";
        if (rule.getMetricName().contains("heap")) return "HEAP_PRESSURE";
        if (rule.getMetricName().contains("error")) return "ERROR_RATE";
        if (rule.getMetricName().contains("latency")) return "LATENCY_SPIKE";
        if (rule.getMetricName().contains("cache")) return "CACHE_MISS_SPIKE";
        return "GENERIC_ANOMALY";
    }
}
