package com.monitor.gateway.service;

import com.monitor.gateway.model.KafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service responsible for publishing events to Kafka topics.
 */
@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String ERROR_EVENTS_TOPIC    = "error-events";
    private static final String ANOMALY_EVENTS_TOPIC  = "anomaly-events";

    private final KafkaTemplate<String, KafkaEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, KafkaEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishUpstreamError(String downstreamService, int statusCode) {
        KafkaEvent event = KafkaEvent.builder()
                .service("gateway")
                .event("UPSTREAM_5XX")
                .value(statusCode)
                .description("Downstream service [" + downstreamService + "] returned HTTP " + statusCode)
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(ERROR_EVENTS_TOPIC, downstreamService, event);
        log.warn("[KAFKA] Published UPSTREAM_5XX event for service={}, statusCode={}", downstreamService, statusCode);
    }

    public void publishTrafficFloodEvent(int requestCount) {
        KafkaEvent event = KafkaEvent.builder()
                .service("gateway")
                .event("TRAFFIC_FLOOD")
                .value(requestCount)
                .description("Traffic flood simulation: " + requestCount + " concurrent requests dispatched")
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "gateway", event);
        log.warn("[KAFKA] Published TRAFFIC_FLOOD event, requestCount={}", requestCount);
    }

    public void publishSlowUpstreamEvent(String service, long latencyMs) {
        KafkaEvent event = KafkaEvent.builder()
                .service("gateway")
                .event("SLOW_UPSTREAM")
                .value(latencyMs)
                .description("Slow upstream simulation for service [" + service + "]: " + latencyMs + "ms delay")
                .timestamp(Instant.now())
                .build();

        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, service, event);
        log.warn("[KAFKA] Published SLOW_UPSTREAM event for service={}, latencyMs={}", service, latencyMs);
    }
}
