package com.monitor.media.service;

import com.monitor.media.model.KafkaEvent;
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

    public void publishConcurrentJobOverflow(int activeJobs) {
        KafkaEvent event = KafkaEvent.builder()
                .service("media").event("CONCURRENT_JOB_OVERFLOW").value(activeJobs)
                .description("Media processing active jobs=" + activeJobs + " exceeds threshold")
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "media", event);
        log.warn("[KAFKA] Published CONCURRENT_JOB_OVERFLOW: activeJobs={}", activeJobs);
    }

    public void publishMemoryLeakDetected(long allocatedMB) {
        KafkaEvent event = KafkaEvent.builder()
                .service("media").event("MEMORY_LEAK_DETECTED").value(allocatedMB)
                .description("Memory leak simulation: " + allocatedMB + "MB allocated without release")
                .timestamp(Instant.now()).build();
        kafkaTemplate.send(ANOMALY_EVENTS_TOPIC, "media", event);
        log.warn("[KAFKA] Published MEMORY_LEAK_DETECTED: allocatedMB={}", allocatedMB);
    }
}
