# Phase 2 Documentation — Data Infrastructure with Docker Compose

**AI Observability Platform for Microservices**
**Phase:** 2 of 5 | **Status:** Complete | **Duration:** ~1–2 days

---

## Overview

Phase 2 establishes the complete **Layer 2** of the observability platform — the data infrastructure that all 4 Phase 1 microservices feed their metrics, logs, traces, and events into. A single `docker compose up -d` command brings up all 7 infrastructure services.

By the end of Phase 2, you have:
- **Prometheus** scraping all 4 services every 15 seconds
- **Grafana Loki** receiving structured logs with trace IDs
- **Zipkin** collecting distributed traces across service calls
- **PostgreSQL + TimescaleDB + PgVector** storing incidents, AI results, and RAG embeddings
- **Redis** acting as metric snapshot cache and Recommendation feed cache
- **Kafka** with all 3 event topics ready for Phase 1 services to publish into
- A **verification script** to confirm end-to-end data flow

---

## Infrastructure Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│  HOST MACHINE — Spring Boot Services (Phase 1)                      │
│                                                                     │
│  service-gateway:8081  service-search:8082                         │
│  service-media:8083    service-recommendation:8084                 │
│         │                    │                                      │
│    /actuator/prometheus   logs via Loki4j   traces via Zipkin      │
│    Kafka events on localhost:9092           Redis on localhost:6379 │
└─────┬──────────────────────────────────────────────────────────────┘
      │  172.17.0.1 (Docker bridge gateway)
      │
┌─────▼──────────────────── Docker Network: ai-observability-network ─┐
│                                                                      │
│  ┌────────────────┐  ┌─────────────────┐  ┌───────────────────┐   │
│  │  Prometheus    │  │   Grafana Loki  │  │      Zipkin       │   │
│  │  :9090         │  │   :3100         │  │      :9411        │   │
│  │  Scrapes every │  │   Log storage   │  │   Trace storage   │   │
│  │  15s via       │  │   filesystem    │  │   In-memory       │   │
│  │  172.17.0.1    │  │   7-day TTL     │  │   (dev mode)      │   │
│  └────────────────┘  └─────────────────┘  └───────────────────┘   │
│                                                                      │
│  ┌────────────────┐  ┌─────────────────┐  ┌───────────────────┐   │
│  │  PostgreSQL    │  │      Redis      │  │      Kafka        │   │
│  │  :5432         │  │      :6379      │  │      :9092        │   │
│  │  TimescaleDB   │  │   LRU eviction  │  │   + Zookeeper     │   │
│  │  PgVector      │  │   512MB max     │  │   :2181           │   │
│  │  6 tables      │  │   AOF + RDB     │  │   3 topics        │   │
│  └────────────────┘  └─────────────────┘  └───────────────────┘   │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## Project Structure (Phase 2 Additions)

```
ai-observability-platform/
├── docker-compose.yml                     ← NEW: single file to start all infra
├── infrastructure/
│   ├── prometheus/
│   │   └── prometheus.yml                 ← NEW: 4 scrape jobs, 15s interval
│   ├── loki/
│   │   └── loki-config.yml               ← NEW: filesystem storage, 7-day retention
│   ├── postgres/
│   │   └── init.sql                      ← NEW: 6 tables + seed data
│   ├── kafka/
│   │   └── create-topics.sh              ← NEW: topic creation script
│   └── scripts/
│       └── verify-infra.sh               ← NEW: end-to-end verification
└── docs/
    ├── phase-1-documentation.md
    └── phase-2-documentation.md           ← NEW (this file)
```

---

## Task 2.1 — Docker Compose File

### Design Decisions

| Decision | Rationale |
|---|---|
| 8 services in total (7 infra + 1 init-kafka) | `init-kafka` is a one-shot service that runs once to create topics and exits. This avoids bundling topic creation into the Kafka startup script. |
| `condition: service_healthy` in `depends_on` | Ensures Kafka doesn't start before Zookeeper is ready, and topics aren't created before Kafka is ready. Avoids race conditions. |
| Named Docker volumes | Survives `docker compose restart`. Use `docker compose down -v` to wipe data when you need a clean slate. |
| `ai-observability-network` bridge | All 7 containers share this network. Services reference each other by hostname (`kafka`, `postgres`, `redis`). |
| `172.17.0.1` for Prometheus scraping | On Linux, `host.docker.internal` doesn't resolve by default. `172.17.0.1` is the Docker bridge gateway — the host's IP as seen from inside containers. |
| `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"` | Explicit topic creation (via init-kafka) ensures correct partition counts. Auto-creation would create topics with defaults (1 partition). |

### Services Summary

| Service | Container Name | Image | Port |
|---|---|---|---|
| Prometheus | ai-observability-prometheus | prom/prometheus:v2.54.1 | 9090 |
| Loki | ai-observability-loki | grafana/loki:3.3.2 | 3100 |
| Zipkin | ai-observability-zipkin | openzipkin/zipkin:3.4.3 | 9411 |
| PostgreSQL | ai-observability-postgres | timescale/timescaledb-ha:pg16-all | 5432 |
| Redis | ai-observability-redis | redis:7.4-alpine | 6379 |
| Zookeeper | ai-observability-zookeeper | confluentinc/cp-zookeeper:7.7.1 | 2181 |
| Kafka | ai-observability-kafka | confluentinc/cp-kafka:7.7.1 | 9092 |
| init-kafka | ai-observability-init-kafka | confluentinc/cp-kafka:7.7.1 | — |

### Quick Start

```bash
# Start all infrastructure
docker compose up -d

# View all container statuses
docker compose ps

# Follow combined logs
docker compose logs -f

# Follow a specific service
docker compose logs -f kafka

# Stop everything (preserve data)
docker compose down

# Stop everything AND wipe all data volumes
docker compose down -v
```

---

## Task 2.2 — Prometheus Configuration

**File:** [`infrastructure/prometheus/prometheus.yml`](../infrastructure/prometheus/prometheus.yml)

### Scrape Configuration

```
Global interval:   15s
Evaluation:        30s
Scrape timeout:    10s
```

### 4 Scrape Jobs

| Job Name | Target | Metrics Path | Labels |
|---|---|---|---|
| `gateway` | `172.17.0.1:8081` | `/actuator/prometheus` | `app=service-gateway` |
| `search` | `172.17.0.1:8082` | `/actuator/prometheus` | `app=service-search` |
| `media` | `172.17.0.1:8083` | `/actuator/prometheus` | `app=service-media` |
| `recommendation` | `172.17.0.1:8084` | `/actuator/prometheus` | `app=service-recommendation` |

### Verifying Scrape Targets

```bash
# Check target status in Prometheus UI
open http://localhost:9090/targets

# Query a specific metric
curl 'http://localhost:9090/api/v1/query?query=up'

# Check all targets via API
curl http://localhost:9090/api/v1/targets | python3 -m json.tool
```

### Key PromQL Queries for Phase 3

```promql
# CPU usage per service
rate(process_cpu_usage{job="search"}[1m]) * 100

# JVM heap usage %
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# HTTP error rate
rate(http_server_requests_seconds_count{status=~"5.."}[1m]) /
rate(http_server_requests_seconds_count[1m]) * 100

# p95 request latency
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))

# Custom: slow search queries
rate(search_queries_slow_total[1m])

# Custom: active media jobs
media_processing_active

# Custom: recommendation cache miss rate
rate(recommendation_cache_misses_total[5m]) /
(rate(recommendation_cache_hits_total[5m]) + rate(recommendation_cache_misses_total[5m])) * 100
```

### Hot-Reload Config Without Restart

```bash
# Signal Prometheus to reload config (no container restart needed)
curl -X POST http://localhost:9090/-/reload
```

---

## Task 2.3 — Grafana Loki

**File:** [`infrastructure/loki/loki-config.yml`](../infrastructure/loki/loki-config.yml)

### Configuration Highlights

| Setting | Value | Why |
|---|---|---|
| Storage backend | Filesystem (`/loki/chunks`) | Simplest setup for dev — no S3/GCS needed |
| Schema | v13 (TSDB) | Latest Loki schema — best query performance |
| Retention | 7 days (168h) | Sufficient for dev/demo without unbounded disk growth |
| `reject_old_samples_max_age` | 1h | Prevents issues if a service restarts and replays old logs |
| `auth_enabled` | false | No auth — development mode |

### LogQL Query Examples

```logql
# All logs from Search Service
{app="service-search"}

# ERROR logs from Media Service
{app="service-media"} |= "ERROR"

# Find all logs for a specific distributed trace
{app="service-gateway"} | traceId="abc123def456"

# Slow query events across all services
{app=~"service-.*"} |= "SLOW_QUERY"

# All Kafka events published (CRITICAL events)
{app=~"service-.*"} |= "[KAFKA]" |= "CRITICAL"

# Filter by level label
{app="service-recommendation", level="WARN"}

# Rate of ERROR logs per service (for alerting)
sum by (app) (rate({app=~"service-.*"} |= "ERROR" [5m]))
```

### Sending a Test Log

```bash
# Push a test log to Loki
curl -X POST http://localhost:3100/loki/api/v1/push \
  -H "Content-Type: application/json" \
  -d '{
    "streams": [{
      "stream": {"app": "service-search", "level": "INFO"},
      "values": [["'"$(date +%s)000000000"'", "Test log entry from verify script"]]
    }]
  }'

# Query it back
curl 'http://localhost:3100/loki/api/v1/query_range?query=\{app="service-search"\}&limit=5'
```

---

## Task 2.4 — Zipkin

**Image:** `openzipkin/zipkin:3.4.3`

### Configuration

| Setting | Value |
|---|---|
| Storage | In-memory (dev mode — no persistence) |
| Max spans | 500,000 |
| JVM heap | 256MB–512MB |
| UI | http://localhost:9411 |
| Span ingestion endpoint | `http://localhost:9411/api/v2/spans` |

### How Traces Flow In

The 4 Spring Boot services (configured in Phase 1) use:
- `micrometer-tracing-bridge-brave` — creates spans from HTTP requests
- `zipkin-reporter-brave` — batches and sends spans to Zipkin

Their `application.yml` points to:
```yaml
management:
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
  tracing:
    sampling:
      probability: 1.0
```

### Verifying a Distributed Trace

```bash
# 1. Make a gateway request (creates a cross-service trace)
curl http://localhost:8081/gateway/feed/user1

# 2. Open Zipkin UI and search recent traces
open http://localhost:9411

# 3. Or query via API
curl 'http://localhost:9411/api/v2/services'
curl 'http://localhost:9411/api/v2/traces?serviceName=service-gateway&limit=5'
```

### What You'll See in Zipkin

```
Trace: abc123def456
├── service-gateway: GET /gateway/feed/user1    [245ms total]
│   └── service-recommendation: GET /recommend/user1  [198ms]
│       └── Redis: GET feed:user1               [2ms]
```

---

## Task 2.5 — PostgreSQL + TimescaleDB Schema

**File:** [`infrastructure/postgres/init.sql`](../infrastructure/postgres/init.sql)

### Connection Details

| Parameter | Value |
|---|---|
| Host | `localhost` |
| Port | `5432` |
| Database | `observability_db` |
| Username | `observability` |
| Password | `observability123` |

### Tables Overview

```sql
-- 6 tables created by init.sql:

service_registry        -- 4 monitored services (seeded)
incidents               -- anomaly events detected by ai-engine (Phase 3)
ai_analysis_results     -- Gemini root cause analysis (Phase 3)
alert_rules             -- configurable thresholds (seeded with 6 defaults)
anomaly_history         -- TimescaleDB hypertable (time-series metrics)
incident_embeddings     -- PgVector vectors for RAG retrieval
```

### Table: `service_registry` (Seeded)

| id | name | base_url | port | active |
|---|---|---|---|---|
| 1 | service-gateway | http://localhost:8081 | 8081 | true |
| 2 | service-search | http://localhost:8082 | 8082 | true |
| 3 | service-media | http://localhost:8083 | 8083 | true |
| 4 | service-recommendation | http://localhost:8084 | 8084 | true |

### Table: `alert_rules` (Seeded)

| Service | Metric | Threshold | Operator | Severity |
|---|---|---|---|---|
| service-gateway | process_cpu_usage_percent | 80.0 | GT | WARNING |
| service-search | jvm_heap_usage_percent | 85.0 | GT | CRITICAL |
| service-media | media_processing_active | 15.0 | GT | WARNING |
| service-recommendation | recommendation_cache_miss_rate_percent | 70.0 | GT | WARNING |
| *(global)* | http_error_rate_percent | 5.0 | GT | CRITICAL |
| *(global)* | http_request_latency_p95_ms | 2000.0 | GT | WARNING |

### Table: `anomaly_history` (TimescaleDB Hypertable)

```sql
-- This is a TimescaleDB hypertable — automatically partitioned by time (1-day chunks)
-- Written to by the ai-engine every 30s per service per metric
-- Query example (Phase 3):
SELECT
    time_bucket('5 minutes', time) AS bucket,
    service_name,
    metric_name,
    AVG(metric_value) AS avg_value,
    MAX(metric_value) AS max_value
FROM anomaly_history
WHERE time > NOW() - INTERVAL '1 hour'
  AND service_name = 'service-search'
GROUP BY bucket, service_name, metric_name
ORDER BY bucket DESC;
```

### Table: `incident_embeddings` (PgVector RAG)

```sql
-- Stores 768-dimensional Gemini embeddings (text-embedding-004)
-- IVFFlat index for approximate nearest-neighbour search
-- Used by Phase 3 RAG query:
SELECT summary_text, incident_id
FROM incident_embeddings
ORDER BY embedding <=> $1::vector   -- cosine similarity
LIMIT 3;
```

### Database Management Commands

```bash
# Connect to the database
docker exec -it ai-observability-postgres psql -U observability -d observability_db

# Verify tables
\dt

# Check seed data
SELECT * FROM service_registry;
SELECT service_name, metric_name, threshold, severity FROM alert_rules ORDER BY service_name NULLS LAST;

# Check TimescaleDB hypertable
SELECT * FROM timescaledb_information.hypertables;

# Check extensions
\dx

# Exit psql
\q
```

---

## Task 2.6 — Redis

**Image:** `redis:7.4-alpine`

### Configuration

| Setting | Value |
|---|---|
| Max memory | 512MB |
| Eviction policy | `allkeys-lru` — evicts least recently used keys when full |
| Persistence | RDB snapshot (every 60s if 1+ key changed) + AOF |
| Port | 6379 |
| Auth | None (development mode) |

### Key Schema Used by Phase 1 & 3

| Key Pattern | Owner | TTL | Value |
|---|---|---|---|
| `feed:{userId}` | service-recommendation | 5 min | Comma-separated content IDs |
| `metrics:{service}:cpu` | ai-engine (Phase 3) | 60s | Latest CPU % |
| `metrics:{service}:heap` | ai-engine (Phase 3) | 60s | Latest heap % |
| `metrics:{service}:latency_p95` | ai-engine (Phase 3) | 60s | p95 latency in ms |
| `metrics:{service}:error_rate` | ai-engine (Phase 3) | 60s | HTTP error % |

### Redis CLI Commands

```bash
# Connect to Redis
docker exec -it ai-observability-redis redis-cli

# Check a key
GET feed:user123

# View all keys
KEYS *

# Check memory usage
INFO memory

# Flush all data (dangerous — wipes everything)
FLUSHALL

# Check TTL on a key
TTL feed:user123

# Manual metric snapshot set (for testing Phase 3)
SET metrics:service-search:heap 72.5 EX 60
```

---

## Task 2.7 — Kafka Topics

**Images:** `confluentinc/cp-kafka:7.7.1` + `confluentinc/cp-zookeeper:7.7.1`

### Topics Created

| Topic | Partitions | Retention | Published By | Consumed By |
|---|---|---|---|---|
| `anomaly-events` | 3 | 7 days | All 4 services | ai-engine (Phase 3) |
| `error-events` | 3 | 7 days | service-gateway | ai-engine (Phase 3) |
| `deploy-events` | 1 | 30 days | External CI/CD | ai-engine (Phase 3) |

### Kafka Listener Configuration

| Listener | Port | Used By |
|---|---|---|
| `PLAINTEXT://kafka:29092` | Internal Docker | init-kafka, ai-engine (when containerised in Phase 5) |
| `PLAINTEXT_HOST://localhost:9092` | Host machine | All 4 Spring Boot services (Phase 1) |

> **Why two listeners?** Docker containers reach Kafka via `kafka:29092` (internal hostname). The Spring Boot services running on your host machine reach it via `localhost:9092`.

### Kafka CLI Commands

```bash
# List topics
docker exec ai-observability-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list

# Describe a topic (partitions, replicas)
docker exec ai-observability-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 --describe --topic anomaly-events

# Consume messages from a topic (watch events in real-time)
docker exec ai-observability-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic anomaly-events \
  --from-beginning

# Produce a test message
docker exec -it ai-observability-kafka kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic anomaly-events

# Check consumer group offsets (Phase 3)
docker exec ai-observability-kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --list
```

### Expected Kafka Event Payload (from Phase 1)

```json
{
  "service": "search",
  "event": "SLOW_QUERY",
  "value": 3500,
  "description": "Search query took 3500ms — exceeds 2s threshold",
  "timestamp": "2026-05-25T14:32:05Z"
}
```

---

## Running Phase 2 — Step by Step

### Step 1: Start the Docker daemon

```bash
# Linux (systemd)
sudo systemctl start docker

# Verify it's running
docker info
```

### Step 2: Start all infrastructure

```bash
cd /path/to/ai-observability-platform

# Pull images and start all 7 services
docker compose up -d

# Watch startup progress
docker compose ps
```

Expected output after ~60 seconds:
```
NAME                              STATUS
ai-observability-prometheus       running (healthy)
ai-observability-loki             running (healthy)
ai-observability-zipkin           running (healthy)
ai-observability-postgres         running (healthy)
ai-observability-redis            running (healthy)
ai-observability-zookeeper        running (healthy)
ai-observability-kafka            running (healthy)
ai-observability-init-kafka       exited (0)          ← OK: one-shot, exit 0 = success
```

### Step 3: Verify infrastructure

```bash
./infrastructure/scripts/verify-infra.sh
```

### Step 4: Start the 4 Spring Boot services (on host)

```bash
# Terminal 1 — Gateway
java -jar service-gateway/target/service-gateway-1.0.0-SNAPSHOT.jar

# Terminal 2 — Search
java -jar service-search/target/service-search-1.0.0-SNAPSHOT.jar

# Terminal 3 — Media
java -jar service-media/target/service-media-1.0.0-SNAPSHOT.jar

# Terminal 4 — Recommendation
java -jar service-recommendation/target/service-recommendation-1.0.0-SNAPSHOT.jar
```

### Step 5: Trigger anomalies and verify data flow

```bash
# Trigger Search wildcard flood
curl -X POST http://localhost:8082/search/simulate/wildcard-flood

# Observe in Prometheus (wait ~15s for scrape)
curl 'http://localhost:9090/api/v1/query?query=search_queries_slow_total'

# Trigger slow query (publishes Kafka event)
curl -X POST http://localhost:8082/search/simulate/slow-query

# Watch Kafka events
docker exec ai-observability-kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic anomaly-events --from-beginning

# Check Loki logs
curl 'http://localhost:3100/loki/api/v1/query_range?query={app="service-search"}&limit=10'
```

---

## End-to-End Data Flow Verification

| Verification | Command | Expected |
|---|---|---|
| Prometheus healthy | `curl http://localhost:9090/-/healthy` | `Prometheus Server is Healthy` |
| All 4 scrape targets | `http://localhost:9090/targets` | 4 targets, all UP |
| Loki ready | `curl http://localhost:3100/ready` | `ready` |
| Zipkin healthy | `curl http://localhost:9411/health` | `{"status":"UP"}` |
| PostgreSQL seeded | `docker exec ai-observability-postgres psql -U observability -d observability_db -c "SELECT COUNT(*) FROM service_registry;"` | `4` |
| Alert rules seeded | `... -c "SELECT COUNT(*) FROM alert_rules;"` | `6` |
| Redis ping | `docker exec ai-observability-redis redis-cli ping` | `PONG` |
| Kafka topics | `docker exec ai-observability-kafka kafka-topics.sh --bootstrap-server localhost:9092 --list` | `anomaly-events, deploy-events, error-events` |

---

## Troubleshooting

### `init-kafka` exited with code 1

```bash
# Check logs
docker compose logs init-kafka

# Most common cause: Kafka wasn't ready yet
# Solution: Run topic creation manually
docker exec ai-observability-kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic anomaly-events --partitions 3 --replication-factor 1
```

### PostgreSQL init.sql not running

```bash
# Check if it ran (look for "init.sql" in logs)
docker compose logs postgres | grep -i "init\|sql\|error"

# The init script ONLY runs on first start (empty volume)
# To re-run: wipe the volume
docker compose down
docker volume rm ai-observability-postgres-data
docker compose up -d postgres
```

### Prometheus targets show as DOWN

```bash
# Most likely the Spring Boot services aren't running
# Or 172.17.0.1 isn't the correct Docker bridge IP

# Find your Docker bridge IP
docker network inspect bridge --format='{{range .IPAM.Config}}{{.Gateway}}{{end}}'

# Update prometheus.yml with the correct IP and reload
curl -X POST http://localhost:9090/-/reload
```

### Kafka services can't connect from Spring Boot

```bash
# Spring Boot config should point to localhost:9092
spring:
  kafka:
    bootstrap-servers: localhost:9092

# Verify Kafka is reachable from host
nc -z localhost 9092 && echo "Kafka reachable"
```

### Loki pushing logs fails from Spring Boot services

```bash
# Spring Boot logback-spring.xml should point to:
# http://localhost:3100/loki/api/v1/push

# Test push manually
curl -X POST http://localhost:3100/loki/api/v1/push \
  -H "Content-Type: application/json" \
  -d '{"streams":[{"stream":{"app":"test"},"values":[["'"$(date +%s)000000000"'","test"]]}]}'

# 204 = success
```

---

## What's Built in Phase 2 vs What Comes Later

| Feature | Phase 2 | Phase 3+ |
|---|---|---|
| Prometheus metrics scraping | ✅ Every 15s from all 4 services | Phase 3: AI engine queries Prometheus HTTP API |
| Log aggregation | ✅ Loki receiving logs | Phase 3: AI engine queries Loki for log context |
| Distributed trace storage | ✅ Zipkin collecting spans | Phase 4: Dashboard links to Zipkin traces |
| PostgreSQL schema | ✅ All 6 tables created + seeded | Phase 3: AI engine reads/writes incidents, results |
| Redis cache | ✅ Running and reachable | Phase 3: AI engine stores metric snapshots with TTL |
| Kafka topics | ✅ 3 topics ready | Phase 3: AI engine consumes anomaly/error events |
| Anomaly detection | ❌ | Phase 3: `@Scheduled` polling jobs in ai-engine |
| AI root cause analysis | ❌ | Phase 3: Gemini integration |
| Dashboard | ❌ | Phase 4: React + WebSocket |
| Full Dockerization | ❌ | Phase 5: All 4 services in Docker Compose |

---

*Documentation generated: Phase 2 — AI Observability Platform*
*Previous: [Phase 1 — Project Setup & 4 Sample Microservices](phase-1-documentation.md)*
*Next: [Phase 3 — AI Analysis Engine](phase-3-documentation.md)*
