package com.monitor.gateway.model;

import java.time.Instant;

/**
 * Kafka event payload published to error-events and anomaly-events topics.
 */
public record KafkaEvent(
        String service,
        String event,
        double value,
        String description,
        Instant timestamp
) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String service;
        private String event;
        private double value;
        private String description;
        private Instant timestamp;

        public Builder service(String service) { this.service = service; return this; }
        public Builder event(String event) { this.event = event; return this; }
        public Builder value(double value) { this.value = value; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public KafkaEvent build() {
            return new KafkaEvent(service, event, value, description, timestamp);
        }
    }
}
