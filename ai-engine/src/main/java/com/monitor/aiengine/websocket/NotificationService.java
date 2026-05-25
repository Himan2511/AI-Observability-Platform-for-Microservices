package com.monitor.aiengine.websocket;

import com.monitor.aiengine.dto.IncidentDetailDto;
import com.monitor.aiengine.entity.AiAnalysisResult;
import com.monitor.aiengine.entity.Incident;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final SimpMessagingTemplate messagingTemplate;

    public NotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Pushes a new incident alert to connected clients.
     */
    public void notifyNewIncident(Incident incident) {
        log.info("Pushing new incident notification for ID: {}", incident.getId());
        
        Map<String, Object> payload = Map.of(
                "incidentId", incident.getId(),
                "service", incident.getServiceName(),
                "severity", incident.getSeverity(),
                "anomalyType", incident.getAnomalyType(),
                "summary", incident.getDescription(),
                "detectedAt", incident.getDetectedAt() != null ? incident.getDetectedAt().toString() : "Now"
        );
        
        messagingTemplate.convertAndSend("/topic/alerts", payload);
    }

    /**
     * Pushes completed AI analysis to connected clients.
     */
    public void notifyAiAnalysisComplete(Incident incident, AiAnalysisResult analysis) {
        log.info("Pushing AI analysis complete notification for incident ID: {}", incident.getId());
        
        IncidentDetailDto payload = new IncidentDetailDto(incident, analysis);
        messagingTemplate.convertAndSend("/topic/incidents/" + incident.getId() + "/analysis", payload);
    }
    
    /**
     * Pushes refreshed metrics to connected clients.
     */
    public void notifyMetricsRefresh(Map<String, Object> metricsSnapshot) {
        messagingTemplate.convertAndSend("/topic/metrics", metricsSnapshot);
    }
}
