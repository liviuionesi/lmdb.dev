#!/usr/bin/env bash
# Inactivity Watchdog: inspects gateway activity and stops cloud compute if idle for > 1 hour.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GATEWAY_URL="${GATEWAY_URL:-https://filmpire-api.duckdns.org}"
IDLE_THRESHOLD_SECONDS="${IDLE_THRESHOLD_SECONDS:-3600}" # 1 hour
TARGET_CLOUD="${TARGET_CLOUD:-auto}" # azure, aws, or auto

echo "🔍 [Watchdog] Checking backend activity at ${GATEWAY_URL}..."

# 1. Check if backend is reachable at all
HTTP_CODE="$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 "${GATEWAY_URL}/actuator/health" 2>/dev/null || echo "000")"

if [ "$HTTP_CODE" != "200" ]; then
  echo "ℹ️ [Watchdog] Backend is already unreachable / stopped (HTTP ${HTTP_CODE}). Nothing to stop."
  exit 0
fi

# 2. Query activity endpoint
ACTIVITY_JSON="$(curl -s --max-time 5 "${GATEWAY_URL}/actuator/activity" 2>/dev/null || echo "{}")"
IDLE_SECONDS="$(echo "$ACTIVITY_JSON" | grep -o '"idleSeconds":[0-9]*' | grep -o '[0-9]*' || echo "")"

if [ -z "$IDLE_SECONDS" ]; then
  echo "⚠️ [Watchdog] Activity endpoint did not report idleSeconds. Fallback to 0."
  exit 0
fi

echo "📊 [Watchdog] Current idle time: ${IDLE_SECONDS}s / ${IDLE_THRESHOLD_SECONDS}s"

# 3. Check threshold
if [ "$IDLE_SECONDS" -ge "$IDLE_THRESHOLD_SECONDS" ]; then
  echo "🛑 [Watchdog] Backend has been idle for ${IDLE_SECONDS}s (>= 3600s threshold). Triggering auto-stop..."
  
  if [ "$TARGET_CLOUD" == "azure" ]; then
    "$SCRIPT_DIR/stop-azure.sh"
  elif [ "$TARGET_CLOUD" == "aws" ]; then
    "$SCRIPT_DIR/stop-aws.sh"
  else
    # Try detecting which cloud is currently running
    if [ -f "$SCRIPT_DIR/../terraform/azure/terraform.tfstate" ]; then
      "$SCRIPT_DIR/stop-azure.sh" || true
    fi
    if [ -f "$SCRIPT_DIR/../terraform/aws/terraform.tfstate" ]; then
      "$SCRIPT_DIR/stop-aws.sh" || true
    fi
  fi
  echo "✅ [Watchdog] Auto-stop executed successfully."
else
  REMAINING=$((IDLE_THRESHOLD_SECONDS - IDLE_SECONDS))
  echo "⏳ [Watchdog] Backend active. ${REMAINING}s remaining until auto-sleep."
fi
