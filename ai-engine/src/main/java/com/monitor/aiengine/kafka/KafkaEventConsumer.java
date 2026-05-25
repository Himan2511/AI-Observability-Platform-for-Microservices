package com.monitor.aiengine.kafka;

import com.monitor.aiengine.dto.ServiceEventDto;
import com.monitor.aiengine.entity.Incident;
import com.monitor.aiengine.repository.IncidentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class KafkaEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventConsumer.class);

    private final IncidentRepository incidentRepository;

    public KafkaEventConsumer(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @KafkaListener(topics = "anomaly-events", groupId = "ai-engine-group")
    public void consumeAnomalyEvent(ServiceEventDto event) {
        log.info("Received Anomaly Event: {} for service {}", event.getEvent(), event.getService());
        
        // Check if an open incident already exists for this service and anomaly type
        Optional<Incident> existingIncident = incidentRepository.findTopByServiceNameAndAnomalyTypeAndStatusOrderByDetectedAtDesc(
                event.getService(), event.getEvent(), "OPEN");

        if (existingIncident.isPresent()) {
            Incident incident = existingIncident.get();
            incident.setMetricValue(event.getValue());
            incidentRepository.save(incident);
            log.info("Updated existing incident ID {}", incident.getId());
        } else {
            Incident incident = new Incident();
            incident.setServiceName(event.getService());
            incident.setAnomalyType(event.getEvent());
            incident.setSeverity("WARNING"); // Default to WARNING based on Kafka events
            incident.setStatus("OPEN");
            incident.setMetricValue(event.getValue());
            incident.setDescription("Anomaly detected via Kafka event stream");
            
            incident = incidentRepository.save(incident);
            log.info("Created new Incident ID {} from anomaly event", incident.getId());
            
            // AI Analysis will be triggered asynchronously or scheduled job picks up OPEN incidents without analysis
        }
    }

    @KafkaListener(topics = "error-events", groupId = "ai-engine-group")
    public void consumeErrorEvent(ServiceEventDto event) {
        log.info("Received Error Event: {} for service {}", event.getEvent(), event.getService());
        
        Optional<Incident> existingIncident = incidentRepository.findTopByServiceNameAndAnomalyTypeAndStatusOrderByDetectedAtDesc(
                event.getService(), "HTTP_ERROR", "OPEN");

        if (existingIncident.isEmpty()) {
            Incident incident = new Incident();
            incident.setServiceName(event.getService());
            incident.setAnomalyType("HTTP_ERROR");
            incident.setSeverity("CRITICAL");
            incident.setStatus("OPEN");
            incident.setMetricValue(event.getValue());
            incident.setDescription(event.getEvent());
            
            incidentRepository.save(incident);
            log.info("Created new Incident for HTTP_ERROR from error event");
        }
    }

    @KafkaListener(topics = "deploy-events", groupId = "ai-engine-group")
    public void consumeDeployEvent(ServiceEventDto event) {
        log.info("Received Deploy Event for service {}. Timestamp: {}", event.getService(), event.getTimestamp());
        // In a real app, record to deployments table
    }
}
