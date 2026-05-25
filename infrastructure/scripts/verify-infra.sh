#!/bin/bash
# ============================================================
# Infrastructure Verification Script
# AI Observability Platform — Phase 2
#
# Checks that all 7 infrastructure services are running and
# reachable, and that the 4 Spring Boot services are being
# scraped by Prometheus.
#
# Usage:
#   ./infrastructure/scripts/verify-infra.sh
#
# Requirements:
#   - docker compose up -d must have been run first
#   - curl, psql (or docker exec) for database checks
# ============================================================

set -e

# Colour codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Colour

PASS=0
FAIL=0

pass() { echo -e "  ${GREEN}[PASS]${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "  ${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }
info() { echo -e "  ${BLUE}[INFO]${NC} $1"; }
warn() { echo -e "  ${YELLOW}[WARN]${NC} $1"; }

header() {
    echo ""
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BLUE}  $1${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
}

# ── Helpers ────────────────────────────────────────────────────────────────

http_check() {
    local name="$1"
    local url="$2"
    local expected_pattern="$3"

    local response
    response=$(curl -s --max-time 5 "$url" 2>/dev/null || echo "UNREACHABLE")

    if [ "$response" = "UNREACHABLE" ]; then
        fail "$name — unreachable at $url"
    elif [ -n "$expected_pattern" ] && ! echo "$response" | grep -q "$expected_pattern"; then
        fail "$name — unexpected response (expected: '$expected_pattern')"
        info "Response: ${response:0:200}"
    else
        pass "$name — $url"
    fi
}

echo ""
echo -e "${BLUE}╔════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║   AI Observability Platform — Phase 2          ║${NC}"
echo -e "${BLUE}║   Infrastructure Verification                  ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════╝${NC}"

# ════════════════════════════════════════════════════════════
# 1. Prometheus
# ════════════════════════════════════════════════════════════
header "1. Prometheus (port 9090)"

http_check "Prometheus UI" "http://localhost:9090/-/healthy" "Prometheus Server is Healthy"
http_check "Prometheus API" "http://localhost:9090/api/v1/status/config" "scrape_configs"

# Check all 4 scrape targets
info "Checking scrape targets..."
TARGETS=$(curl -s "http://localhost:9090/api/v1/targets" 2>/dev/null || echo "")
for svc in gateway search media recommendation; do
    if echo "$TARGETS" | grep -q "\"job\":\"$svc\""; then
        # Check if UP
        if echo "$TARGETS" | python3 -c "
import sys, json
data = json.load(sys.stdin)
targets = data.get('data', {}).get('activeTargets', [])
svc_targets = [t for t in targets if t.get('labels', {}).get('job') == '$svc']
for t in svc_targets:
    if t.get('health') == 'up':
        print('UP')
        break
" 2>/dev/null | grep -q "UP"; then
            pass "Prometheus scraping service-$svc — health: UP"
        else
            warn "Prometheus target for service-$svc exists but may not be UP yet (service may not be running)"
        fi
    else
        warn "Prometheus target 'job=$svc' not found — is service-$svc running on localhost?"
    fi
done

# ════════════════════════════════════════════════════════════
# 2. Grafana Loki
# ════════════════════════════════════════════════════════════
header "2. Grafana Loki (port 3100)"

http_check "Loki ready" "http://localhost:3100/ready" "ready"
http_check "Loki metrics" "http://localhost:3100/metrics" "loki_build_info"

# Send a test log entry
info "Sending test log to Loki..."
TEST_LOG=$(curl -s --max-time 5 -X POST \
    "http://localhost:3100/loki/api/v1/push" \
    -H "Content-Type: application/json" \
    -d "{\"streams\":[{\"stream\":{\"app\":\"verify-script\",\"level\":\"INFO\"},\"values\":[[\"$(date +%s)000000000\",\"Phase 2 verification test log entry\"]]}]}" \
    2>/dev/null || echo "ERROR")

if [ "$TEST_LOG" = "" ] || [ "$TEST_LOG" = "204" ]; then
    pass "Loki — test log accepted"
else
    # 204 No Content is success
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 -X POST \
        "http://localhost:3100/loki/api/v1/push" \
        -H "Content-Type: application/json" \
        -d "{\"streams\":[{\"stream\":{\"app\":\"verify-script\"},\"values\":[[\"$(date +%s)000000000\",\"test\"]]}]}" \
        2>/dev/null || echo "0")
    if [ "$HTTP_CODE" = "204" ] || [ "$HTTP_CODE" = "200" ]; then
        pass "Loki — test log accepted (HTTP $HTTP_CODE)"
    else
        fail "Loki — test log push returned HTTP $HTTP_CODE"
    fi
fi

# ════════════════════════════════════════════════════════════
# 3. Zipkin
# ════════════════════════════════════════════════════════════
header "3. Zipkin (port 9411)"

http_check "Zipkin health" "http://localhost:9411/health" "\"status\":\"UP\""
http_check "Zipkin services API" "http://localhost:9411/api/v2/services" ""

# ════════════════════════════════════════════════════════════
# 4. PostgreSQL + TimescaleDB
# ════════════════════════════════════════════════════════════
header "4. PostgreSQL + TimescaleDB (port 5432)"

# Check via docker exec (avoids needing local psql)
PG_CHECK=$(docker exec ai-observability-postgres \
    psql -U observability -d observability_db -c "SELECT COUNT(*) FROM service_registry;" \
    -t -A 2>/dev/null || echo "ERROR")

# Also verify the host port mapping works (5433 = remapped from 5432)
if nc -z localhost 5433 2>/dev/null; then
    pass "PostgreSQL — reachable on host port 5433"
else
    fail "PostgreSQL — not reachable on host port 5433 (check docker compose ps)"
fi

if [ "$PG_CHECK" = "4" ]; then
    pass "PostgreSQL — service_registry seeded with 4 services"
elif echo "$PG_CHECK" | grep -qE "^[0-9]+$"; then
    warn "PostgreSQL — service_registry has $PG_CHECK rows (expected 4)"
else
    fail "PostgreSQL — could not query service_registry: $PG_CHECK"
fi

# Check alert rules
RULES_COUNT=$(docker exec ai-observability-postgres \
    psql -U observability -d observability_db -c "SELECT COUNT(*) FROM alert_rules;" \
    -t -A 2>/dev/null || echo "ERROR")

if echo "$RULES_COUNT" | grep -qE "^[0-9]+$" && [ "$RULES_COUNT" -ge 6 ]; then
    pass "PostgreSQL — alert_rules seeded with $RULES_COUNT rules"
else
    fail "PostgreSQL — alert_rules check failed: $RULES_COUNT"
fi

# Check TimescaleDB
TS_CHECK=$(docker exec ai-observability-postgres \
    psql -U observability -d observability_db -c "SELECT COUNT(*) FROM timescaledb_information.hypertables WHERE hypertable_name='anomaly_history';" \
    -t -A 2>/dev/null || echo "0")

if [ "$TS_CHECK" = "1" ]; then
    pass "PostgreSQL — anomaly_history TimescaleDB hypertable exists"
else
    fail "PostgreSQL — anomaly_history is NOT a hypertable (check TimescaleDB extension)"
fi

# Check PgVector
VEC_CHECK=$(docker exec ai-observability-postgres \
    psql -U observability -d observability_db -c "SELECT COUNT(*) FROM pg_extension WHERE extname='vector';" \
    -t -A 2>/dev/null || echo "0")

if [ "$VEC_CHECK" = "1" ]; then
    pass "PostgreSQL — PgVector extension installed"
else
    fail "PostgreSQL — PgVector extension NOT found"
fi

# ════════════════════════════════════════════════════════════
# 5. Redis
# ════════════════════════════════════════════════════════════
header "5. Redis (port 6379)"

REDIS_PING=$(docker exec ai-observability-redis redis-cli ping 2>/dev/null || echo "ERROR")
if [ "$REDIS_PING" = "PONG" ]; then
    pass "Redis — PING → PONG (container internal)"
else
    fail "Redis — ping failed: $REDIS_PING"
fi

# Verify host port 6380 (remapped from 6379)
if nc -z localhost 6380 2>/dev/null; then
    pass "Redis — reachable on host port 6380"
else
    warn "Redis — not reachable on host port 6380 (may still be starting)"
fi

REDIS_INFO=$(docker exec ai-observability-redis redis-cli info server 2>/dev/null | grep "redis_version" || echo "")
if [ -n "$REDIS_INFO" ]; then
    pass "Redis — $REDIS_INFO"
fi

# ════════════════════════════════════════════════════════════
# 6. Kafka + Zookeeper
# ════════════════════════════════════════════════════════════
header "6. Kafka (port 9092) + Zookeeper (port 2181)"

TOPICS=$(docker exec ai-observability-kafka \
    kafka-topics.sh --bootstrap-server localhost:9092 --list 2>/dev/null || echo "ERROR")

if echo "$TOPICS" | grep -q "anomaly-events"; then
    pass "Kafka — topic 'anomaly-events' exists"
else
    fail "Kafka — topic 'anomaly-events' not found"
fi

if echo "$TOPICS" | grep -q "error-events"; then
    pass "Kafka — topic 'error-events' exists"
else
    fail "Kafka — topic 'error-events' not found"
fi

if echo "$TOPICS" | grep -q "deploy-events"; then
    pass "Kafka — topic 'deploy-events' exists"
else
    fail "Kafka — topic 'deploy-events' not found"
fi

# Check partition counts
ANOMALY_PARTITIONS=$(docker exec ai-observability-kafka \
    kafka-topics.sh --bootstrap-server localhost:9092 \
    --describe --topic anomaly-events 2>/dev/null | grep -c "^Topic:" || echo "0")

# ════════════════════════════════════════════════════════════
# 7. Spring Boot Services (optional — must be running on host)
# ════════════════════════════════════════════════════════════
header "7. Spring Boot Services (host — optional)"

for svc_info in "service-gateway:8081" "service-search:8082" "service-media:8083" "service-recommendation:8084"; do
    svc_name="${svc_info%%:*}"
    svc_port="${svc_info##*:}"
    HEALTH=$(curl -s --max-time 3 "http://localhost:$svc_port/actuator/health" 2>/dev/null || echo "")
    if echo "$HEALTH" | grep -q '"status":"UP"'; then
        pass "$svc_name (port $svc_port) — health: UP"
        PROMETHEUS_DATA=$(curl -s --max-time 3 "http://localhost:$svc_port/actuator/prometheus" 2>/dev/null | head -5 || echo "")
        if [ -n "$PROMETHEUS_DATA" ]; then
            pass "$svc_name — /actuator/prometheus responding"
        else
            warn "$svc_name — /actuator/prometheus empty or unreachable"
        fi
    else
        warn "$svc_name (port $svc_port) — not running (start manually for full verification)"
    fi
done

# ════════════════════════════════════════════════════════════
# Summary
# ════════════════════════════════════════════════════════════
echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "  Verification Complete"
echo -e "  ${GREEN}Passed: $PASS${NC}   ${RED}Failed: $FAIL${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

if [ $FAIL -eq 0 ]; then
    echo -e "\n  ${GREEN}✓ All checks passed! Phase 2 infrastructure is ready.${NC}"
    echo -e "  Next: Start all 4 Spring Boot services and hit endpoints."
else
    echo -e "\n  ${RED}✗ Some checks failed. Review the output above.${NC}"
    echo -e "  Tip: Run 'docker compose ps' to see container status."
    exit 1
fi

echo ""
echo "  Useful URLs:"
echo "  • Prometheus:  http://localhost:9090"
echo "  • Prometheus Targets: http://localhost:9090/targets"
echo "  • Zipkin:      http://localhost:9411"
echo "  • Loki:        http://localhost:3100"
echo "  • pgAdmin hint: docker exec -it ai-observability-postgres psql -U observability -d observability_db"
echo ""
