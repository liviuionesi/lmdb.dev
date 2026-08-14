#!/usr/bin/env bash
# Exposes the local LMDB API Gateway via a secure Cloudflare Tunnel (public HTTPS).
# Works with both Podman and Docker without requiring a Cloudflare account or paid domain.
set -euo pipefail

CONTAINER_NAME="lmdb-cloudflare-tunnel"
DOCKER_CMD="podman"
if command -v docker >/dev/null 2>&1; then
  DOCKER_CMD="docker"
fi

echo "=================================================="
echo "  Starting Cloudflare Tunnel for Local Gateway"
echo "=================================================="

GATEWAY_URL="${1:-}"
if [ -z "$GATEWAY_URL" ]; then
  if curl -s -o /dev/null -m 2 http://localhost:8080/actuator/health; then
    GATEWAY_URL="http://localhost:8080"
  else
    AWS_IP="$(aws ec2 describe-instances --filters "Name=tag:Name,Values=lmdb-k3s-demo,lmdb-k3s" "Name=instance-state-name,Values=running" --query "Reservations[0].Instances[0].PublicIpAddress" --output text 2>/dev/null || echo "")"
    if [ -n "$AWS_IP" ] && [ "$AWS_IP" != "None" ] && curl -s -o /dev/null -m 3 "http://${AWS_IP}:30080/actuator/health"; then
      GATEWAY_URL="http://${AWS_IP}:30080"
    else
      GATEWAY_URL="http://localhost:8080"
    fi
  fi
fi

# 1. Stop any existing tunnel container
$DOCKER_CMD rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

# 2. Check if API Gateway is responding
if ! curl -s -o /dev/null -m 3 "${GATEWAY_URL}/actuator/health"; then
  echo "⚠️ Warning: API Gateway does not appear to be responding on ${GATEWAY_URL}."
else
  echo "✅ Target Gateway verified: ${GATEWAY_URL}"
fi

# 3. Launch cloudflared container in host network mode
echo "🚀 Launching cloudflared container targeting ${GATEWAY_URL}..."
$DOCKER_CMD run -d \
  --name "$CONTAINER_NAME" \
  --network host \
  docker.io/cloudflare/cloudflared:latest \
  tunnel --no-autoupdate --url "$GATEWAY_URL" >/dev/null

# 4. Wait for the public trycloudflare.com URL to be generated
echo "⏳ Waiting for public HTTPS tunnel URL..."
TUNNEL_URL=""
for i in {1..20}; do
  LOGS=$($DOCKER_CMD logs "$CONTAINER_NAME" 2>&1 || true)
  URL_MATCH=$(echo "$LOGS" | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com' | head -n 1 || true)
  if [ -n "$URL_MATCH" ]; then
    TUNNEL_URL="$URL_MATCH"
    break
  fi
  sleep 1
done

if [ -n "$TUNNEL_URL" ]; then
  # 5. Publish the URL so the deployed frontend can find it without any
  # manual step: cloudflared mints a new random hostname every restart, so
  # apiUrl.js's health-check fallback fetches this file (via
  # raw.githubusercontent.com) rather than trusting a hardcoded value.
  SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
  REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
  POINTER_FILE="$REPO_ROOT/infrastructure/tunnel-url.txt"
  echo "$TUNNEL_URL" > "$POINTER_FILE"
  echo "✅ Local tunnel pointer updated: $POINTER_FILE -> $TUNNEL_URL"

  echo ""
  echo "=================================================="
  echo "  🎉 Cloudflare Tunnel is LIVE!"
  echo "=================================================="
  echo "  Public URL: $TUNNEL_URL"
  echo ""
  echo "  The deployed frontend picks this up automatically (within ~30s.,"
  echo "  see apiUrl.js) whenever the cloud backend is unreachable - no"
  echo "  manual step needed. To force it immediately in one browser, open"
  echo "  the Admin Dashboard and select 'Local Tunnel'."
  echo "=================================================="
  echo "  To stop the tunnel, run: ./infrastructure/scripts/stop-tunnel.sh"
  echo "=================================================="
else
  echo "❌ Error: Could not retrieve tunnel URL. View logs with: $DOCKER_CMD logs $CONTAINER_NAME" >&2
  exit 1
fi
