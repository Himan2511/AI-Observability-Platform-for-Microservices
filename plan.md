# AI Observability Platform for Microservices
### Project Plan & Implementation Roadmap

---

## Project Overview

### What Is This?

The **AI Observability Platform for Microservices** is a full-stack, production-grade monitoring system that goes beyond traditional tools like Spring Boot Admin. Instead of simply showing that a service is "UP" or "DOWN", this platform uses AI to **explain why** things went wrong — in plain English.

**Traditional monitoring tells you:** *Search service is DOWN.*

**This platform tells you:** *Search service crashed at 14:32. Heap memory grew from 400MB to 1.2GB in 4 minutes. Root cause: wildcard queries with no pagination limit caused Elasticsearch to load the entire dataset into memory. GC ran 11 times trying to recover. Recommended action: add pagination limit of 100 results per query and restrict wildcard searches to indexed fields only.*

---

### The 4 Microservices Being Monitored

These are 4 general-purpose services that exist in almost every real-world backend system — not tied to any specific domain.

| Service | Port | What It Does |
|---|---|---|
| **API Gateway Service** | 8081 | Single entry point — routes all requests, enforces rate limiting, calls downstream services |
| **Search Service** | 8082 | Full-text search — finds content across large datasets using complex queries |
| **Media/File Processing Service** | 8083 | Accepts file uploads, resizes images, generates thumbnails, stores processed files |
| **Recommendation/Feed Service** | 8084 | Generates personalised content feeds using scoring algorithms and Redis caching |

**Why these 4 together:**
- Every service has a **different resource bottleneck** — CPU, memory, I/O, threads
- They **call each other** — Gateway routes to all three; creating real inter-service traffic and distributed traces
- Each produces **different types of anomalies** — making AI explanations varied and meaningful
- These services exist in almost every app — making the project universally relatable

---

### How They Interact

```
User request comes in
        ↓
API Gateway (8081) — checks rate limit, routes request
        ↓
Recommendation Service (8084) — builds personalised feed (CPU heavy)
        ↓
Search Service (8082) — user searches within feed (memory heavy)
        ↓
Media Service (8083) — serves images/thumbnails for results (I/O heavy)
```

A slowdown in any one service cascades to the others — exactly the kind of chain reaction your AI engine is built to detect and explain.

---

### Core Capabilities

| Capability | Description |
|---|---|
| **Real-time Metrics** | CPU, memory, latency, thread count scraped from every microservice every 15s |
| **Distributed Tracing** | Follow a single HTTP request across all 4 services end-to-end |
| **Log Aggregation** | Centralised log search and filtering across all services |
| **Anomaly Detection** | Scheduled jobs detect statistical spikes and drops every 30 seconds |
| **AI Root Cause Analysis** | Gemini called with metric context to generate human-readable explanations |
| **RAG-Powered Memory** | Past incidents stored as embeddings; AI retrieves similar incidents to improve analysis |
| **Live Dashboard** | React ops dashboard with WebSocket-pushed alerts, health cards, and metric charts |
| **Event-Driven Alerts** | Kafka topics carry deploy events, error spikes, and anomaly triggers |

---

### System Architecture — 4 Layers

```
┌─────────────────────────────────────────────────────────────────┐
│  LAYER 4 — OPS DASHBOARD (React + Tailwind / Thymeleaf)         │
│  Service health cards · Metric charts · AI explanation panel    │
└────────────────────────────┬────────────────────────────────────┘
                             │ WebSocket + REST
┌────────────────────────────▼────────────────────────────────────┐
│  LAYER 3 — AI ANALYSIS ENGINE (Spring Boot 3 — Main App)        │
│  Spring AI (Gemini) · WebSocket · Spring Security (JWT)         │
│  Kafka Consumer · @Scheduled anomaly jobs · PgVector RAG        │
└───────┬──────────────────────────────────────────┬──────────────┘
        │ Scrape / Query                            │ Read/Write
┌───────▼───────────────────────────────────────────▼────────────┐
│  LAYER 2 — DATA COLLECTION & STORAGE                            │
│  Prometheus · Grafana Loki · Zipkin · PostgreSQL+TimescaleDB    │
│  Redis (metric snapshots + recommendation cache) · PgVector     │
└───────┬─────────────────────────────────────────────────────────┘
        │ Actuator endpoints
┌───────▼─────────────────────────────────────────────────────────┐
│  LAYER 1 — MICROSERVICES BEING MONITORED                        │
│  API Gateway · Search · Media Processing · Recommendation       │
│  Actuator · Micrometer · Micrometer Tracing + Zipkin            │
└─────────────────────────────────────────────────────────────────┘
```

---

### Technology Stack

**Backend — AI Engine (Main App)**
- Spring Boot 3 (REST APIs, scheduled jobs, Kafka consumer, WebSocket server)
- Spring AI — Google Gemini (calls Gemini with metric context for explanations)
- Spring Data JPA + Spring Data Redis
- Spring Security (JWT, Admin/Viewer roles)
- Spring WebSocket (real-time dashboard push)
- Apache Kafka (event streaming)
- PgVector (embedding storage for RAG)

**Data Infrastructure**
- Prometheus (time-series metrics scraping every 15s)
- Grafana Loki (log aggregation)
- Zipkin (distributed trace storage)
- PostgreSQL + TimescaleDB (incidents, AI results, service registry, alert rules)
- Redis (metric snapshot cache + Recommendation Service cache simulation)

**Monitored Microservices (Layer 1)**
- 4 Spring Boot apps: API Gateway, Search, Media Processing, Recommendation
- Spring Boot Actuator (`/actuator/metrics`, `/health`, `/info`, `/actuator/prometheus`)
- Micrometer (instrumentation — counters, timers, JVM metrics)
- Micrometer Tracing + Zipkin (distributed trace IDs on every request)

**Frontend**
- React + Tailwind CSS (service health cards, metric charts, AI explanation panel)
- Recharts (line graphs for CPU/memory/latency, spike visualisation)
- WebSocket client (live alert feed without polling)
- *(Alternative: Thymeleaf for a pure-Spring server-rendered dashboard)*

**DevOps**
- Docker + Docker Compose (all infrastructure services)
- Maven multi-module project structure

---

## Phases

---

## Phase 1 — Project Setup & 4 Sample Microservices

**Goal:** Get all 4 monitored Spring Boot apps running locally with metrics, health, tracing, and Kafka events fully working.

**Duration estimate:** 2–3 days

### Tasks

**1.1 — Maven Multi-Module Project Scaffold**
- Create a parent `pom.xml` with modules:
  - `service-gateway` (API Gateway Service)
  - `service-search` (Search Service)
  - `service-media` (Media/File Processing Service)
  - `service-recommendation` (Recommendation/Feed Service)
  - `ai-engine` (AI Analysis Engine — main app)
- Configure shared dependency management (Spring Boot BOM, Micrometer, Kafka, etc.)
- Set Java 21 as the baseline across all modules

**1.2 — API Gateway Service (`service-gateway` — port 8081)**

What it does: Acts as the single entry point. Calls the other 3 services internally.

Endpoints to build:
- `GET /gateway/feed/{userId}` → calls Recommendation Service, returns personalised feed
- `GET /gateway/search?q={query}` → calls Search Service, returns results
- `POST /gateway/media/upload` → calls Media Service, uploads and processes file
- `GET /gateway/health/all` → calls `/actuator/health` on all 3 services and aggregates

Simulation endpoints (for triggering anomalies):
- `POST /gateway/simulate/traffic-flood` → sends 500 concurrent requests to all downstream services
- `POST /gateway/simulate/slow-upstream` → adds artificial delay to downstream calls to simulate upstream failure
- `POST /gateway/simulate/rate-limit-breach` → exceeds rate limit threshold to trigger alert

Custom metrics to add:
- `gateway.requests.total` — counter per route
- `gateway.upstream.failures` — counter per downstream service
- `gateway.routing.latency` — timer per route

**1.3 — Search Service (`service-search` — port 8082)**

What it does: Simulates full-text search across a large in-memory dataset.

Endpoints to build:
- `GET /search?q={query}&page={n}&size={n}` → searches in-memory dataset, returns paginated results
- `GET /search/indexed?q={query}` → fast indexed search (low CPU)
- `GET /search/full?q={query}` → full scan search (high CPU — simulates expensive query)
- `GET /search/stats` → returns index size, query count, average latency

Simulation endpoints:
- `POST /search/simulate/wildcard-flood` → runs unlimited wildcard queries, causes heap pressure and GC pauses
- `POST /search/simulate/index-rebuild` → triggers a full in-memory index rebuild, causes CPU spike
- `POST /search/simulate/slow-query` → adds `Thread.sleep` to simulate slow Elasticsearch response

Custom metrics to add:
- `search.queries.total` — counter
- `search.queries.slow` — counter (queries > 500ms)
- `search.index.size` — gauge (number of indexed documents)
- `search.heap.pressure` — gauge (estimated memory used by search operations)

**1.4 — Media/File Processing Service (`service-media` — port 8083)**

What it does: Accepts file uploads and simulates image/file processing (resize, thumbnail generation, compression).

Endpoints to build:
- `POST /media/upload` → accepts multipart file, processes it, returns file metadata
- `GET /media/{fileId}` → returns processed file metadata
- `GET /media/jobs/active` → returns count of currently processing jobs
- `DELETE /media/{fileId}` → deletes file record

Simulation endpoints:
- `POST /media/simulate/large-upload` → allocates a large byte array in memory to simulate big file processing
- `POST /media/simulate/concurrent-jobs` → spawns 20 simultaneous processing threads to cause thread exhaustion
- `POST /media/simulate/memory-leak` → allocates memory across multiple calls without releasing it

Custom metrics to add:
- `media.uploads.total` — counter
- `media.processing.active` — gauge (concurrent jobs running)
- `media.processing.duration` — timer
- `media.file.size.bytes` — histogram (distribution of uploaded file sizes)

**1.5 — Recommendation/Feed Service (`service-recommendation` — port 8084)**

What it does: Generates a personalised feed for a user by running a scoring algorithm over an in-memory content dataset, with Redis caching.

Endpoints to build:
- `GET /recommend/{userId}` → checks Redis cache first; if miss, runs scoring algorithm, stores in Redis, returns feed
- `GET /recommend/{userId}/refresh` → forces cache bypass, always runs algorithm
- `GET /recommend/stats` → cache hit rate, algorithm run count, average scoring time

Simulation endpoints:
- `POST /recommend/simulate/cache-expiry` → clears all Redis cache keys simultaneously to trigger thundering herd
- `POST /recommend/simulate/algorithm-overload` → runs scoring algorithm for 1000 users simultaneously, causes CPU spike
- `POST /recommend/simulate/large-dataset` → increases dataset size 10x to make algorithm progressively slower

Custom metrics to add:
- `recommendation.cache.hits` — counter
- `recommendation.cache.misses` — counter
- `recommendation.algorithm.duration` — timer
- `recommendation.feed.size` — histogram (number of items in generated feed)

**1.6 — Add Spring Boot Actuator to Every Service**
- Add `spring-boot-starter-actuator` to all 4 services
- Expose: `health`, `info`, `metrics`, `prometheus`, `env`, `loggers`
- Configure `management.endpoints.web.exposure.include=*`
- Verify `/actuator/prometheus` returns scrape-ready output on each service

**1.7 — Add Micrometer Instrumentation**
- Add `micrometer-registry-prometheus` to each service
- Register all custom metrics listed above (1.2–1.5) using `MeterRegistry`
- Add `@Timed` annotation on key service methods for automatic timing

**1.8 — Add Distributed Tracing**
- Add `micrometer-tracing-bridge-brave` and `zipkin-reporter-brave` to each service
- Configure `management.zipkin.tracing.endpoint=http://localhost:9411/api/v2/spans`
- Set `management.tracing.sampling.probability=1.0` for development
- Use `RestTemplate` or `WebClient` (tracing-aware) for inter-service calls so trace IDs propagate
- Verify: one request to `/gateway/feed/{userId}` creates a trace spanning Gateway → Recommendation in Zipkin

**1.9 — Add Logback → Loki Appender**
- Add `com.github.loki4j:loki-logback-appender` manually to each service's `pom.xml`
- Configure `logback-spring.xml` to ship logs to Loki at `http://localhost:3100`
- Add service name as a Loki label: `app=gateway`, `app=search`, etc.
- Ensure trace ID is included in every log line via MDC: `[traceId=%X{traceId}]`

**1.10 — Add Kafka Event Publishing**
- Add `spring-kafka` to each service
- Define Kafka topics: `anomaly-events`, `deploy-events`, `error-events`
- Publish to Kafka when:
  - Gateway: upstream service returns 5xx → publish to `error-events`
  - Search: query takes > 2 seconds → publish to `anomaly-events`
  - Media: concurrent jobs exceed 10 → publish to `anomaly-events`
  - Recommendation: cache miss rate exceeds 80% → publish to `anomaly-events`
- Event payload: `{ "service": "search", "event": "SLOW_QUERY", "value": 3200, "timestamp": "..." }`

**Deliverables**
- 4 Spring Boot services running on ports 8081–8084
- `/actuator/prometheus` returning custom + JVM metrics on each
- Inter-service HTTP calls working with trace ID propagation
- Simulation endpoints working and producing visible metric changes
- Kafka events flowing when anomaly thresholds are crossed
- Logs shipping to Loki with trace IDs

---

## Phase 2 — Data Infrastructure with Docker Compose

**Goal:** Stand up all infrastructure in Docker and verify data from all 4 services is flowing into each store correctly.

**Duration estimate:** 1–2 days

### Tasks

**2.1 — Docker Compose File**
- Create `docker-compose.yml` at project root
- Services to include:
  - `prometheus` (port 9090)
  - `loki` (port 3100)
  - `zipkin` (port 9411)
  - `postgres` with TimescaleDB extension (port 5432)
  - `redis` (port 6379)
  - `zookeeper` (port 2181)
  - `kafka` (port 9092)
- All infrastructure only — application services added in Phase 5

**2.2 — Prometheus Configuration**
- Write `prometheus.yml` scrape config:
```yaml
scrape_configs:
  - job_name: 'gateway'
    static_configs:
      - targets: ['host.docker.internal:8081']
    metrics_path: '/actuator/prometheus'
  - job_name: 'search'
    static_configs:
      - targets: ['host.docker.internal:8082']
    metrics_path: '/actuator/prometheus'
  - job_name: 'media'
    static_configs:
      - targets: ['host.docker.internal:8083']
    metrics_path: '/actuator/prometheus'
  - job_name: 'recommendation'
    static_configs:
      - targets: ['host.docker.internal:8084']
    metrics_path: '/actuator/prometheus'
```
- Scrape interval: 15 seconds
- Verify all 4 services appear as targets in Prometheus UI (`http://localhost:9090/targets`)

**2.3 — Grafana Loki Setup**
- Configure Loki with filesystem storage
- Verify logs from all 4 services appear and are queryable
- Test queries: `{app="search"}`, `{app="media"} |= "ERROR"`, `{app="gateway"} | traceId="abc123"`

**2.4 — Zipkin Setup**
- Start Zipkin with in-memory storage (development mode)
- Verify distributed traces appear at `http://localhost:9411`
- Test: hit `GET /gateway/feed/user1` → find a trace in Zipkin showing Gateway → Recommendation spans

**2.5 — PostgreSQL + TimescaleDB Schema**

Create the following tables:

```sql
-- Monitored services registry
CREATE TABLE service_registry (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  base_url VARCHAR(255) NOT NULL,
  port INT NOT NULL,
  active BOOLEAN DEFAULT TRUE,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Detected incidents
CREATE TABLE incidents (
  id BIGSERIAL PRIMARY KEY,
  service_name VARCHAR(100) NOT NULL,
  anomaly_type VARCHAR(100) NOT NULL,   -- CPU_SPIKE, MEMORY_SURGE, LATENCY_SPIKE, ERROR_RATE, THREAD_EXHAUSTION
  severity VARCHAR(20) NOT NULL,         -- INFO, WARNING, CRITICAL
  status VARCHAR(20) DEFAULT 'OPEN',     -- OPEN, ACKNOWLEDGED, INVESTIGATING, RESOLVED
  metric_value DOUBLE PRECISION,
  threshold_value DOUBLE PRECISION,
  detected_at TIMESTAMPTZ DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);

-- AI analysis results per incident
CREATE TABLE ai_analysis_results (
  id BIGSERIAL PRIMARY KEY,
  incident_id BIGINT REFERENCES incidents(id),
  root_cause TEXT,
  contributing_factors TEXT,
  recommended_action TEXT,
  preventive_measures TEXT,
  similar_incidents_found INT DEFAULT 0,
  model_used VARCHAR(100),
  tokens_used INT,
  created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Alert rules (configurable thresholds)
CREATE TABLE alert_rules (
  id BIGSERIAL PRIMARY KEY,
  service_name VARCHAR(100),             -- NULL = applies to all services
  metric_name VARCHAR(100) NOT NULL,
  threshold DOUBLE PRECISION NOT NULL,
  operator VARCHAR(10) NOT NULL,         -- GT, LT, EQ
  severity VARCHAR(20) NOT NULL,
  enabled BOOLEAN DEFAULT TRUE
);

-- Anomaly history (time-series — TimescaleDB hypertable)
CREATE TABLE anomaly_history (
  time TIMESTAMPTZ NOT NULL,
  service_name VARCHAR(100) NOT NULL,
  metric_name VARCHAR(100) NOT NULL,
  metric_value DOUBLE PRECISION NOT NULL,
  baseline_value DOUBLE PRECISION,
  deviation_percent DOUBLE PRECISION
);
SELECT create_hypertable('anomaly_history', 'time');

-- Incident embeddings for RAG
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE incident_embeddings (
  id BIGSERIAL PRIMARY KEY,
  incident_id BIGINT REFERENCES incidents(id),
  summary_text TEXT NOT NULL,
  embedding vector(768),   -- Gemini embedding dimension
  created_at TIMESTAMPTZ DEFAULT NOW()
);
```

Seed `service_registry` with all 4 services and seed `alert_rules` with sensible defaults:

| Service | Metric | Threshold | Severity |
|---|---|---|---|
| gateway | CPU usage % | 80 | WARNING |
| search | heap memory % | 85 | CRITICAL |
| media | active processing jobs | 15 | WARNING |
| recommendation | cache miss rate % | 70 | WARNING |
| all | HTTP error rate % | 5 | CRITICAL |
| all | request latency p95 ms | 2000 | WARNING |

**2.6 — Redis Setup**
- Configure Redis container
- Key schema for metric snapshots:
  - `metrics:gateway:cpu` → latest CPU value
  - `metrics:search:heap` → latest heap value
  - `metrics:media:active_jobs` → current active job count
  - `metrics:recommendation:cache_hit_rate` → rolling cache hit rate
- TTL: 60 seconds per key (refreshed every polling cycle)
- Recommendation Service also uses Redis for its feed cache: `feed:{userId}` → JSON feed, TTL 5 minutes

**2.7 — Kafka Topic Setup**
- Create topics on startup:
  - `anomaly-events` (3 partitions)
  - `error-events` (3 partitions)
  - `deploy-events` (1 partition)
- Verify all 4 microservices can produce to their respective topics

**2.8 — Verify End-to-End Data Flow**
- Hit `/gateway/feed/user1` → check trace in Zipkin
- Hit `/search/simulate/wildcard-flood` → check metrics spike in Prometheus → check logs in Loki
- Confirm all 4 services appear as healthy Prometheus targets

**Deliverables**
- Single `docker-compose up` starts all infrastructure
- All 4 services scraped by Prometheus (visible in targets)
- Logs from all 4 services queryable in Loki
- Distributed traces visible in Zipkin
- PostgreSQL schema created and seeded with default alert rules
- Redis and Kafka running and reachable

---

## Phase 3 — AI Analysis Engine (Core Spring Boot App)

**Goal:** Build the main backend — the brain of the platform. It polls metrics, detects anomalies, calls Gemini for AI explanations, and pushes live alerts to the dashboard.

**Duration estimate:** 4–6 days

### Tasks

**3.1 — Spring Boot 3 Project Setup**
- Dependencies: Web, JPA, Security, WebSocket, Kafka, Redis, Spring AI (Gemini), Actuator, Prometheus
- Configure in `application.yml`:
  - PostgreSQL datasource
  - Redis connection
  - Kafka broker
  - Gemini API key (`spring.ai.google.gemini.api-key`)
  - Prometheus scrape URL per service
- Set up Flyway migrations for schema from Phase 2

**3.2 — Prometheus Query Client**
- Write `PrometheusClient` service that queries Prometheus HTTP API
- Queries to run per service every 30 seconds:

| Metric | PromQL Query |
|---|---|
| CPU usage % | `rate(process_cpu_usage{job="gateway"}[1m]) * 100` |
| Heap memory % | `jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100` |
| HTTP error rate % | `rate(http_server_requests_seconds_count{status=~"5.."}[1m]) / rate(http_server_requests_seconds_count[1m]) * 100` |
| Request latency p95 | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))` |
| Active threads | `jvm_threads_live_threads` |
| Custom metrics | `search_queries_slow_total`, `media_processing_active`, `recommendation_cache_misses_total` |

- Parse PromQL JSON response into typed `MetricSnapshot` objects
- Store latest snapshot in Redis

**3.3 — Anomaly Detection Engine**
- `@Scheduled(fixedRate = 30_000)` polling job
- For each service, compare current metric values against `alert_rules` table
- Detection logic:

```
For each alert rule:
  fetch current metric value from Redis snapshot
  compare against threshold using rule operator
  if breached:
    check if incident already OPEN for this service + metric
    if not: create new Incident, trigger AI analysis pipeline
    if yes: update metric_value on existing incident
```

- Service-specific anomaly patterns:

| Service | Anomaly | Detection Logic |
|---|---|---|
| API Gateway | Upstream failure cascade | Error rate > 10% AND latency > 3000ms simultaneously |
| Search | GC pressure | Heap > 85% AND GC pause count increasing |
| Media | Thread exhaustion | Active jobs > 15 AND thread count > 80% of max |
| Recommendation | Thundering herd | Cache miss rate > 70% AND CPU > 75% simultaneously |

**3.4 — Gemini AI Integration**
- Configure `spring-ai-google-gemini` with API key
- Build `AiAnalysisService` with the following prompt template:

```
System: You are a senior site reliability engineer (SRE) with expertise in 
Java microservices, JVM performance, and distributed systems. 
Analyse the following incident and respond in JSON format only.

User:
Service: {serviceName}
Anomaly Type: {anomalyType}
Current Metrics:
  - CPU: {cpu}%
  - Heap Memory: {heap}%
  - Request Latency p95: {latency}ms
  - Error Rate: {errorRate}%
  - Active Threads: {threads}
  - {serviceSpecificMetrics}

Metric trend (last 5 minutes): {trendData}

Similar past incidents: {ragContext}

Respond with JSON:
{
  "rootCause": "...",
  "contributingFactors": ["...", "..."],
  "recommendedAction": "...",
  "preventiveMeasures": ["...", "..."],
  "estimatedRecoveryTime": "..."
}
```

- Parse JSON response into `AiAnalysisResult` entity
- Persist to `ai_analysis_results` table

**3.5 — Service-Specific AI Prompt Context**

Each service gets additional context injected into the Gemini prompt:

- **API Gateway:** upstream service response times, route-level error breakdown, active connections count
- **Search Service:** query type distribution (wildcard vs indexed), GC pause frequency, index size, slow query count
- **Media Service:** concurrent job count, average file size being processed, thread pool queue length
- **Recommendation Service:** cache hit/miss ratio, algorithm execution time, dataset size, number of users requesting simultaneously

**3.6 — RAG — Retrieval-Augmented Generation**
- On each resolved incident: generate text embedding of the incident summary using Gemini Embeddings API
- Store embedding in `incident_embeddings` (PgVector)
- Before calling Gemini for a new incident: query PgVector for top-3 similar past incidents using cosine similarity:
```sql
SELECT summary_text, incident_id
FROM incident_embeddings
ORDER BY embedding <=> $1::vector
LIMIT 3;
```
- Inject retrieved summaries into the AI prompt as `{ragContext}`
- Example RAG context: *"Similar past incident on Search Service (2025-03-14): wildcard flood caused GC thrashing. Resolved by adding query pagination. Recovery took 8 minutes."*

**3.7 — Kafka Consumer**
- `@KafkaListener` consuming `anomaly-events`, `error-events`, `deploy-events`
- On `anomaly-events`: enrich event with current metric snapshot, create Incident, trigger AI analysis
- On `error-events`: check if existing incident covers this; if not, create new one
- On `deploy-events`: record deployment in a `deployments` table, mark timestamp on charts, include deployment info in next AI prompt for that service

**3.8 — Incident Management REST API**

```
GET    /api/incidents                      paginated, filter by service/severity/status
GET    /api/incidents/{id}                 full detail with AI analysis
PUT    /api/incidents/{id}/status          update status (OPEN → INVESTIGATING → RESOLVED)
GET    /api/incidents/{id}/similar         returns RAG-retrieved similar past incidents

GET    /api/services                       all monitored services + current health status
GET    /api/services/{name}/metrics        latest metric snapshot for a service
GET    /api/services/{name}/metrics/history  time-series data (last N minutes) for charting
GET    /api/services/{name}/incidents      all incidents for a specific service

GET    /api/alert-rules                    list all alert rules
POST   /api/alert-rules                    create new rule (Admin only)
PUT    /api/alert-rules/{id}               update rule (Admin only)
DELETE /api/alert-rules/{id}               delete rule (Admin only)

POST   /api/auth/login                     returns JWT
POST   /api/auth/refresh                   refresh token
```

**3.9 — Spring Security — JWT Auth**
- Stateless JWT authentication using `OAuth2 Resource Server`
- Roles: `ADMIN` (full access) and `VIEWER` (read-only — no PUT/POST/DELETE)
- Pre-seed two users in DB: `admin / admin123` and `viewer / viewer123`
- All `/api/**` endpoints protected; return 401/403 appropriately

**3.10 — Spring WebSocket — Live Push**
- Configure WebSocket endpoint at `/ws`
- Push to frontend on these events:
  - `/topic/alerts` — new incident created (all services)
  - `/topic/metrics` — refreshed metric snapshot for all 4 services (every 30s)
  - `/topic/incidents/{id}/analysis` — AI analysis complete for a specific incident
- Payload example for `/topic/alerts`:
```json
{
  "incidentId": 42,
  "service": "search",
  "severity": "CRITICAL",
  "anomalyType": "HEAP_MEMORY_SURGE",
  "summary": "Heap reached 91%. Wildcard query flood suspected.",
  "detectedAt": "2025-05-24T14:32:05Z"
}
```

**Deliverables**
- Anomaly detection running every 30 seconds across all 4 services
- Gemini generating root cause explanations with service-specific context
- RAG retrieving similar past incidents and injecting them into prompts
- Full REST API with JWT authentication
- WebSocket pushing live alerts and metric updates

---

## Phase 4 — Ops Dashboard (Frontend)

**Goal:** Build the React dashboard where engineers monitor all 4 services, see live alerts, and read AI explanations.

**Duration estimate:** 3–4 days

### Tasks

**4.1 — React + Tailwind Project Setup**
- Create `dashboard` with Vite + React + TypeScript
- Install: Tailwind CSS, Recharts, `@stomp/stompjs`, `sockjs-client`, Axios, React Router
- Configure dev proxy to `ai-engine` at `http://localhost:8080`

**4.2 — Authentication**
- Login page → calls `POST /api/auth/login` → stores JWT in memory (not localStorage)
- Axios interceptor adds `Authorization: Bearer <token>` to every request
- Protected routes — redirect to `/login` if no token
- Role-based UI: Admin sees acknowledge/resolve buttons; Viewer sees read-only

**4.3 — Service Health Dashboard (Main View)**
- 4 health cards — one per service — laid out in a 2x2 grid
- Each card shows:
  - Service name + description
  - Status badge: HEALTHY (green) / WARNING (amber) / CRITICAL (red)
  - CPU % with mini spark line
  - Heap memory %
  - Requests/min
  - Error rate %
  - Active incident count (badge)
- Cards update in real time via WebSocket subscription to `/topic/metrics`
- Click any card → navigate to service detail view

**4.4 — Service Detail View**
- Line charts (Recharts) for each service showing last 1h of data:
  - CPU usage over time
  - Heap memory over time
  - Request latency p95 over time
  - Error rate over time
  - Service-specific chart:
    - Gateway: requests per route (stacked bar)
    - Search: slow query count over time
    - Media: active processing jobs over time
    - Recommendation: cache hit vs miss ratio over time
- Time range selector: last 15m / 1h / 6h / 24h
- List of recent incidents for this service at the bottom

**4.5 — Incidents Feed**
- Chronological list of all incidents across all 4 services
- Columns: timestamp, service badge, severity badge, anomaly type, one-line AI summary, status
- Filter bar: by service, severity, status, date range
- Pagination (20 per page)
- Click row → expand AI explanation panel inline

**4.6 — AI Explanation Panel**
- Full AI analysis display:
  - Incident header: service, anomaly type, severity, detected time
  - Root Cause (Gemini generated) — highlighted in a coloured box
  - Contributing Factors — bulleted list
  - Recommended Immediate Action — highlighted in amber
  - Preventive Measures — bulleted list
  - Estimated Recovery Time
  - Similar Past Incidents section — cards linking to past incidents (from RAG)
- Admin only: "Acknowledge", "Investigating", "Resolve" status buttons
- Timestamp trail: when opened, when acknowledged, when resolved

**4.7 — Live Alert Toasts**
- WebSocket subscription to `/topic/alerts`
- New incidents appear as toast notifications (top-right)
- Toast colour: amber for WARNING, red for CRITICAL
- Toast content: service name, anomaly type, one-line summary
- Auto-dismiss after 8 seconds or click to navigate to incident
- Maximum 3 toasts visible simultaneously (oldest auto-dismissed)

**4.8 — Metric Spike Visualisation**
- On line charts: shade the anomaly time range with a translucent red overlay
- Place a vertical marker line at the exact incident detection timestamp
- Tooltip on marker shows: incident ID, severity, anomaly type
- Makes the correlation between metric spike and detected incident visually obvious

**4.9 — Simulation Control Panel (Dev/Demo Tool)**
- A hidden `/simulate` page (Admin only) with buttons to trigger all simulation endpoints:
  - Gateway: Traffic Flood, Slow Upstream
  - Search: Wildcard Flood, Index Rebuild
  - Media: Large Upload, Concurrent Jobs, Memory Leak
  - Recommendation: Cache Expiry, Algorithm Overload
- Each button shows a spinner while running and a success/failure toast on completion
- Makes live demos easy — trigger anomaly with one click, watch the dashboard react

**(Optional) 4.10 — Thymeleaf Alternative**
- Server-rendered Thymeleaf dashboard covering the same views
- No React needed — simpler to deploy for a pure-Spring preference

**Deliverables**
- 4 service health cards updating live via WebSocket
- Service detail views with metric charts and spike visualisation
- Incidents feed with AI explanation panel
- Simulation control panel for easy demo
- Role-based access working end-to-end

---

## Phase 5 — Docker Packaging & Integration Testing

**Goal:** Package every component into Docker, verify the complete system works end-to-end, and write the README.

**Duration estimate:** 1–2 days

### Tasks

**5.1 — Dockerfiles**
- `service-gateway/Dockerfile` — multi-stage Maven build → JRE 21 slim image
- `service-search/Dockerfile` — same pattern
- `service-media/Dockerfile` — same pattern
- `service-recommendation/Dockerfile` — same pattern
- `ai-engine/Dockerfile` — same pattern
- `dashboard/Dockerfile` — Vite build → Nginx serving static files on port 80

**5.2 — Update Docker Compose**
- Add all 6 application containers to `docker-compose.yml`
- Full port mapping:

| Container | Port |
|---|---|
| service-gateway | 8081 |
| service-search | 8082 |
| service-media | 8083 |
| service-recommendation | 8084 |
| ai-engine | 8080 |
| dashboard | 3000 |
| prometheus | 9090 |
| loki | 3100 |
| zipkin | 9411 |
| postgres | 5432 |
| redis | 6379 |
| kafka | 9092 |

- `depends_on` ordering: postgres/redis/kafka start before ai-engine; all 4 services start before ai-engine begins scraping
- Environment variables: `GEMINI_API_KEY`, `DB_URL`, `REDIS_URL`, `KAFKA_BROKER`
- Health checks on postgres, kafka, redis so dependent services wait properly

**5.3 — End-to-End Integration Test Checklist**

Run through this flow after `docker-compose up`:

1. Open dashboard at `http://localhost:3000` — all 4 cards show HEALTHY
2. Login as `admin` — confirm role-based buttons appear
3. Trigger **Search wildcard flood** via simulation panel
4. Observe: Search card turns WARNING/CRITICAL within 30 seconds
5. Observe: Toast notification appears with AI-generated one-liner
6. Open incident → confirm full Gemini explanation with root cause
7. Resolve the incident → confirm status updates on dashboard
8. Trigger **Search wildcard flood** again → confirm RAG context appears in AI explanation referencing the prior incident
9. Trigger **Recommendation cache expiry** → observe thundering herd explanation
10. Trigger **Media concurrent jobs** → observe thread exhaustion explanation
11. Open Zipkin at `http://localhost:9411` → confirm cross-service traces exist
12. Check Prometheus at `http://localhost:9090/targets` → all 4 services UP

**5.4 — Environment Configuration**
- `application-dev.yml` — local development (services on localhost, no Docker)
- `application-prod.yml` — Docker environment (services referenced by container name)
- All secrets via environment variables — no API keys in code

**5.5 — README.md**
- Project overview with architecture diagram
- What each of the 4 services simulates
- Prerequisites: Docker Desktop, Java 21, Node 20, Gemini API key
- Quick start: `GEMINI_API_KEY=your_key docker-compose up`
- How to trigger each type of anomaly manually
- How to add a new microservice to monitoring (step-by-step)
- Environment variable reference table
- Screenshots of the dashboard

**Deliverables**
- `docker-compose up` starts the entire platform (12 containers)
- All 10 integration test steps pass
- README sufficient for a new developer to run the project from scratch

---

## Phase 6 — Polish, Edge Cases & Stretch Goals

**Goal:** Harden the system and add advanced features that elevate the project further.

**Duration estimate:** 2–3 days

### Core Hardening Tasks

**6.1 — Alert Rules UI**
- Admin page to create/edit/delete alert rules without touching code
- Fields: service (dropdown of 4 services), metric name, threshold, operator, severity, enabled toggle
- Changes hot-reloaded by the anomaly detector within one polling cycle

**6.2 — Incident Lifecycle & MTTR Tracking**
- Full status trail: OPEN → ACKNOWLEDGED → INVESTIGATING → RESOLVED
- Timestamp stored at each transition
- MTTR (Mean Time to Resolution) calculated per service and displayed on dashboard
- Audit log: which user changed the status and when

**6.3 — Resilience**
- Circuit breaker around Gemini API calls (Resilience4j) — if Gemini fails, store incident with `AI_PENDING` status
- Async retry: background job retries `AI_PENDING` incidents every 5 minutes
- Dead letter topic for failed Kafka messages
- Dashboard remains functional even if AI engine is temporarily down

**6.4 — Observability of the Observer**
- `ai-engine` exposes its own `/actuator/prometheus` endpoint
- Add `ai-engine` as a Prometheus scrape target
- Monitor: AI analysis latency, Gemini API call count, anomaly detection job duration, Kafka consumer lag

### Stretch Goal Features

**6.5 — Predictive Alerting**
- Before a threshold is breached, detect the *trend* toward it
- Example: *"Search heap memory growing at 12MB/minute. At this rate, OOM threshold will be hit in approximately 11 minutes."*
- Uses linear regression over the last 10 data points per metric

**6.6 — Deployment Correlation**
- When `deploy-events` Kafka topic receives an event, draw a vertical line on all charts at that timestamp
- AI prompt includes: *"Search service was redeployed 4 minutes before this anomaly"*
- Gemini can then correlate the deployment with the incident — very realistic SRE scenario

**6.7 — Slack Notifications**
- On CRITICAL incident: POST to a Slack Incoming Webhook
- Message format: service name, anomaly type, one-line AI summary, link to dashboard incident
- Configurable per service/severity in `alert_rules` table

**6.8 — Service Dependency Map**
- Visual graph on the dashboard showing which services call which
- Gateway → Search, Gateway → Media, Gateway → Recommendation
- When an incident occurs, highlight the affected node and its downstream dependents
- Shows cascade risk visually

---

## Summary — Phase Completion Checklist

| Phase | Description | Key Output |
|---|---|---|
| Phase 1 | 4 microservices (Gateway, Search, Media, Recommendation) fully instrumented | Services running with metrics, tracing, Kafka, simulation endpoints |
| Phase 2 | Docker Compose infrastructure | Prometheus, Loki, Zipkin, PostgreSQL, Redis, Kafka running; schema seeded |
| Phase 3 | AI Analysis Engine | Anomaly detection, Gemini explanations, RAG, REST API, WebSocket, JWT |
| Phase 4 | Ops Dashboard | React dashboard with health cards, charts, AI panel, simulation controls |
| Phase 5 | Docker packaging + integration testing | Full 12-container system, end-to-end verified, README complete |
| Phase 6 | Polish + stretch goals | Alert rules UI, resilience, predictive alerting, Slack notifications |

**Completing Phases 1–5 delivers a fully functional, end-to-end AI observability platform.**
Phase 6 takes it from impressive to exceptional.

---

*Updated: May 2026*
