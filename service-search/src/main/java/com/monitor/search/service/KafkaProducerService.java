package com.monitor.search.service;

import com.monitor.search.model.KafkaEvent;
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

    public void publishSlowQuery(long durationMs) {
        KafkaEvent event = KafkaEvent.builder()
                .service("search").event("SLOW_QUERY").value(durationMs)
                .description("Search query took " + durationMs + "ms — exceeds 2s threshold")
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "search", event);
        log.warn("[KAFKA] Published SLOW_QUERY event: durationMs={}", durationMs);
    }

    public void publishHeapPressure(double heapPercent) {
        KafkaEvent event = KafkaEvent.builder()
                .service("search").event("HEAP_PRESSURE").value(heapPercent)
                .description(String.format("Search service heap at %.1f%% — wildcard flood causing GC pressure", heapPercent))
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "search", event);
        log.warn("[KAFKA] Published HEAP_PRESSURE event: heapPercent={}", heapPercent);
    }

    public void publishIndexRebuild() {
        KafkaEvent event = KafkaEvent.builder()
                .service("search").event("INDEX_REBUILD").value(0)
                .description("Full index rebuild triggered — CPU spike expected")
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "search", event);
        log.warn("[KAFKA] Published INDEX_REBUILD event");
    }
}
