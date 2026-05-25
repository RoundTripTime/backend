#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/roundtrip"
JAR_NAME="roundtrip.jar"
NEW_JAR="roundtrip-new.jar"
OLD_JAR="roundtrip-old.jar"
SERVICE_NAME="roundtrip"
HEALTH_URL="http://localhost:8080/actuator/health"
MAX_RETRIES=30
RETRY_INTERVAL=2

cd "$APP_DIR"

echo "=== Deploying RoundTrip Backend ==="

# ------------------------------------------------
# 1. Backup current JAR
# ------------------------------------------------
if [ -f "$JAR_NAME" ]; then
  echo "[1/4] Backing up current JAR..."
  cp "$JAR_NAME" "$OLD_JAR"
else
  echo "[1/4] No existing JAR to back up (first deploy)"
fi

# ------------------------------------------------
# 2. Replace JAR
# ------------------------------------------------
echo "[2/4] Replacing JAR..."
mv "$NEW_JAR" "$JAR_NAME"

# ------------------------------------------------
# 3. Restart service
# ------------------------------------------------
echo "[3/4] Restarting $SERVICE_NAME service..."
sudo systemctl restart "$SERVICE_NAME"

# ------------------------------------------------
# 4. Health check
# ------------------------------------------------
echo "[4/4] Waiting for health check..."
for i in $(seq 1 $MAX_RETRIES); do
  if curl -sf "$HEALTH_URL" > /dev/null 2>&1; then
    echo "Health check passed! (attempt $i/$MAX_RETRIES)"
    echo "=== Deploy complete ==="
    exit 0
  fi
  echo "  Attempt $i/$MAX_RETRIES - waiting ${RETRY_INTERVAL}s..."
  sleep "$RETRY_INTERVAL"
done

# ------------------------------------------------
# Rollback on failure
# ------------------------------------------------
echo "Health check failed after $MAX_RETRIES attempts. Rolling back..."
if [ -f "$OLD_JAR" ]; then
  mv "$OLD_JAR" "$JAR_NAME"
  sudo systemctl restart "$SERVICE_NAME"
  echo "Rolled back to previous version."
fi
exit 1
