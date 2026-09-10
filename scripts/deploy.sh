#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/roundtrip}"
JAR_NAME="${JAR_NAME:-roundtrip.jar}"
NEW_JAR="${NEW_JAR:-roundtrip-new.jar}"
OLD_JAR="${OLD_JAR:-roundtrip-old.jar}"
SERVICE_NAME="${SERVICE_NAME:-roundtrip}"
HEALTH_URL="${HEALTH_URL:-http://localhost:8080/actuator/health}"
SMOKE_URL="${SMOKE_URL:-http://localhost:8080/actuator/health/smoke}"
MAX_RETRIES="${MAX_RETRIES:-30}"
RETRY_INTERVAL="${RETRY_INTERVAL:-2}"
SYSTEMCTL_BIN="${SYSTEMCTL_BIN:-systemctl}"
DEPLOY_USE_SUDO="${DEPLOY_USE_SUDO:-true}"

cd "$APP_DIR"

echo "=== Deploying RoundTrip Backend ==="

restart_service() {
  if [ "$DEPLOY_USE_SUDO" = "true" ]; then
    sudo "$SYSTEMCTL_BIN" restart "$SERVICE_NAME"
  else
    "$SYSTEMCTL_BIN" restart "$SERVICE_NAME"
  fi
}

wait_for_health() {
  local label="$1"
  echo "$label"
  local i
  for i in $(seq 1 "$MAX_RETRIES"); do
    if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
      echo "Health check passed! (attempt $i/$MAX_RETRIES)"
      return 0
    fi
    echo "  Attempt $i/$MAX_RETRIES - waiting ${RETRY_INTERVAL}s..."
    sleep "$RETRY_INTERVAL"
  done
  echo "Health check failed after $MAX_RETRIES attempts."
  return 1
}

check_smoke() {
  echo "Running application smoke check..."
  local body
  if ! body="$(curl -sf "$SMOKE_URL" 2>/dev/null)"; then
    echo "Smoke check failed: HTTP error."
    return 1
  fi
  if ! printf '%s' "$body" | python3 -c '
import json
import sys

try:
    payload = json.load(sys.stdin)
except Exception:
    sys.stderr.write("Smoke check failed: response is not JSON.\n")
    sys.exit(1)
if payload.get("status") != "UP":
    sys.stderr.write("Smoke check failed: status is not UP.\n")
    sys.exit(1)
components = payload.get("components") or {}
for name in ("db", "redis"):
    status = (components.get(name) or {}).get("status")
    if status != "UP":
        sys.stderr.write("Smoke check failed: %s is not UP.\n" % name)
        sys.exit(1)
'; then
    return 1
  fi
  echo "Smoke check passed."
}

rollback() {
  if [ ! -f "$OLD_JAR" ]; then
    echo "No previous JAR to roll back to."
    return 1
  fi
  echo "Rolling back to previous JAR..."
  mv "$OLD_JAR" "$JAR_NAME"
  restart_service
  if wait_for_health "Waiting for health check after rollback..."; then
    echo "Rolled back to previous version."
    return 0
  fi
  echo "Rollback health check failed."
  return 1
}

fail_deploy() {
  local reason="$1"
  echo "$reason"
  rollback || true
  exit 1
}

# ------------------------------------------------
# 1. Backup current JAR
# ------------------------------------------------
if [ -f "$JAR_NAME" ]; then
  echo "[1/5] Backing up current JAR..."
  cp "$JAR_NAME" "$OLD_JAR"
else
  echo "[1/5] No existing JAR to back up (first deploy)"
fi

# ------------------------------------------------
# 2. Replace JAR
# ------------------------------------------------
echo "[2/5] Replacing JAR..."
mv "$NEW_JAR" "$JAR_NAME"

# ------------------------------------------------
# 3. Restart service
# ------------------------------------------------
echo "[3/5] Restarting $SERVICE_NAME service..."
restart_service

# ------------------------------------------------
# 4. Health check
# ------------------------------------------------
if ! wait_for_health "[4/5] Waiting for health check..."; then
  fail_deploy "Health check failed. Starting rollback."
fi

# ------------------------------------------------
# 5. Smoke check
# ------------------------------------------------
echo "[5/5] Verifying application smoke..."
if ! check_smoke; then
  fail_deploy "Smoke check failed. Starting rollback."
fi

echo "=== Deploy complete ==="
exit 0
