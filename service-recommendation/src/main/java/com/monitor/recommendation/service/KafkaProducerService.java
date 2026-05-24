package com.monitor.recommendation.service;

import com.monitor.recommendation.model.KafkaEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class KafkaProducerService {

    private static final Logger log = LoggerFactory.getLogger(KafkaProducerService.class);
    private static final String ANOMALY_EVENTS_TOPIC = "anomaly-events";
    private final KafkaTemplate<String, KafkaEvent> kafkaTemplate;

    public KafkaProducerService(KafkaTemplate<String, KafkaEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishThunderingHerd(double missRate) {
        KafkaEvent event = KafkaEvent.builder()
                .service("recommendation").event("THUNDERING_HERD").value(missRate)
                .description(String.format("Cache miss rate=%.1f%% — Redis flush caused thundering herd", missRate * 100))
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "recommendation", event);
        log.warn("[KAFKA] Published THUNDERING_HERD: missRate={}%", missRate * 100);
    }

    public void publishAlgorithmOverload(int concurrentUsers) {
        KafkaEvent event = KafkaEvent.builder()
                .service("recommendation").event("ALGORITHM_OVERLOAD").value(concurrentUsers)
                .description("Recommendation algorithm ran for " + concurrentUsers + " users simultaneously — CPU spike")
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "recommendation", event);
        log.warn("[KAFKA] Published ALGORITHM_OVERLOAD: concurrentUsers={}", concurrentUsers);
    }
}
