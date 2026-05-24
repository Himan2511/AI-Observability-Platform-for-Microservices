package com.monitor.recommendation.model;

import java.time.Instant;

public record KafkaEvent(
        String service, String event, double value, String description, Instant timestamp
) {
    public static Builder builder() { return new Builder(); }
    public static class Builder {
        private String service, event, description;
        private double value;
        private Instant timestamp;
        public Builder service(String s) { this.service = s; return this; }
        public Builder event(String e) { this.event = e; return this; }
        public Builder value(double v) { this.value = v; return this; }
        public Builder description(String d) { this.description = d; return this; }
        public Builder timestamp(Instant t) { this.timestamp = t; return this; }
        public KafkaEvent build() { return new KafkaEvent(service, event, value, description, timestamp); }
    }
}
