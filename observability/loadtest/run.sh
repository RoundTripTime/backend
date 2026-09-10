#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

REDIS_NAME="${REDIS_NAME:-roundtrip-ai-loadtest-redis}"
REDIS_PORT="${REDIS_PORT:-16379}"
HARNESS_PORT="${HARNESS_PORT:-18080}"
DURATION="${DURATION:-20s}"
BASE_URL="http://127.0.0.1:${HARNESS_PORT}"
export PATH="${HOME}/.local/bin:${PATH}"
export BASE_URL DURATION

if ! command -v k6 >/dev/null 2>&1; then
  echo "k6가 PATH에 없다. 설치한 뒤 다시 실행한다." >&2
  exit 1
fi

cleanup() {
  if [[ -n "${HARNESS_PID:-}" ]]; then
    kill "${HARNESS_PID}" >/dev/null 2>&1 || true
    wait "${HARNESS_PID}" >/dev/null 2>&1 || true
  fi
  pkill -f roundtrip.loadtest.AiConcurrencyLoadHarness >/dev/null 2>&1 || true
  docker rm -f "${REDIS_NAME}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker rm -f "${REDIS_NAME}" >/dev/null 2>&1 || true
docker run -d --name "${REDIS_NAME}" -p "${REDIS_PORT}:6379" redis:7-alpine >/dev/null

for _ in $(seq 1 50); do
  if docker exec "${REDIS_NAME}" redis-cli ping 2>/dev/null | grep -q PONG; then
    break
  fi
  sleep 0.1
done

./gradlew -q aiConcurrencyHarness \
  -PredisHost=127.0.0.1 \
  -PredisPort="${REDIS_PORT}" \
  -PharnessPort="${HARNESS_PORT}" \
  -PmockLatencyMs="${MOCK_LATENCY_MS:-500}" \
  -PmockCapacity="${MOCK_CAPACITY:-4}" \
  -PacquireTimeoutSeconds="${ACQUIRE_TIMEOUT_SECONDS:-10}" &
HARNESS_PID=$!

for _ in $(seq 1 120); do
  if curl -sf "${BASE_URL}/actuator/health" >/dev/null; then
    break
  fi
  if ! kill -0 "${HARNESS_PID}" >/dev/null 2>&1; then
    echo "harness process exited before becoming ready" >&2
    exit 1
  fi
  sleep 0.25
done

if ! curl -sf "${BASE_URL}/actuator/health" >/dev/null; then
  echo "harness did not become ready on ${BASE_URL}" >&2
  exit 1
fi

python3 "${ROOT}/observability/loadtest/run_experiment.py"
