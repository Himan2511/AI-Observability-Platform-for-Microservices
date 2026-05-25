# Phase 3 Documentation: AI Analysis Engine

## Overview
Phase 3 establishes the **AI Engine**, which serves as the "brain" of the AI Observability Platform. Built as a Spring Boot application (`ai-engine`), it continuously monitors the health of all registered microservices, detects anomalies, and leverages Google's Gemini AI to explain incidents in human-readable terms.

## Core Capabilities

### 1. Metric Polling & Anomaly Detection
- **`PrometheusClient`:** Interrogates the Prometheus HTTP API (`/api/v1/query`) every 30 seconds to fetch live metrics (CPU, Heap, Latencies, Error Rates, etc.) for all monitored services.
- **`MetricCacheService`:** Caches the most recent metric snapshots into Redis for rapid access and threshold comparisons.
- **`AnomalyDetectionEngine`:** A `@Scheduled` job that compares live metrics against predefined `AlertRules`. If a threshold is breached, an `Incident` is generated automatically.

### 2. Event-Driven Subsystems
- **Kafka Consumers:** The `KafkaEventConsumer` listens for anomalies or errors pushed directly from the downstream services (e.g., `error-events`, `anomaly-events`) and registers incidents proactively, supplementing the polling mechanism.

### 3. AI Root Cause Analysis (Gemini)
- **`AiAnalysisService`:** When a new incident occurs, this service is invoked asynchronously. It fetches the latest system context and prompts **Google Gemini-1.5-Pro** to perform a root cause analysis.
- **Structured Explanations:** The AI outputs structured JSON encompassing:
  - Root Cause
  - Contributing Factors
  - Recommended Actions
  - Preventive Measures
  - Estimated Recovery Time

### 4. RAG (Retrieval-Augmented Generation) Memory
- **`RagService`:** Connects to **PgVector** via `JdbcTemplate` to execute cosine-similarity searches (`ORDER BY embedding <=> ?`) against past incidents.
- **Knowledge Injection:** When the AI constructs its prompt, it injects summaries of similar historical incidents, allowing it to provide context-aware recommendations (e.g., "This looks like the cache-eviction issue from last week.").
- **Self-Healing Knowledge Base:** When an incident is marked as `RESOLVED`, an embedding of its summary is generated and stored in PgVector automatically.

### 5. WebSocket Integration
- **`NotificationService`:** Uses STOMP over WebSockets to broadcast critical events to the React Dashboard in real-time.
- **Channels:**
  - `/topic/alerts`: Fired when a new incident is detected.
  - `/topic/incidents/{id}/analysis`: Fired when Gemini completes its analysis.
  - `/topic/metrics`: Streams metric updates.

### 6. Security and API
- **Stateless JWT:** Secured with Spring Security OAuth2 Resource Server. `AuthController` issues mocked JWTs, providing Role-Based Access Control (`ADMIN` vs `VIEWER`).
- **REST APIs:** `/api/incidents` and `/api/services` endpoints provide the frontend with all necessary data.

## Configuration Keys Used
- `GOOGLE_GEMINI_API_KEY`: API key for Google AI Studio (Gemini).

## Next Steps
With the backend AI analysis operational, Phase 4 will introduce the React-based **Ops Dashboard** to visualize these insights and real-time alerts.
