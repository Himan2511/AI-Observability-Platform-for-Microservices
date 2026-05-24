package com.monitor.aiengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI Observability Platform — Main Application (AI Engine).
 *
 * This is the "brain" of the platform. Responsibilities:
 *   - Poll Prometheus metrics every 30s via @Scheduled jobs
 *   - Detect anomalies using configurable alert rules
 *   - Call Gemini AI for root cause explanations
 *   - Retrieve similar past incidents via PgVector RAG
 *   - Push live alerts via WebSocket
 *   - Expose REST API with JWT authentication
 *   - Consume Kafka events from the 4 monitored services
 */
@SpringBootApplication
@EnableScheduling
public class AiEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiEngineApplication.class, args);
    }
}
