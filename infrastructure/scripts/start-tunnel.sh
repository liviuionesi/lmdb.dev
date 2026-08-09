#!/usr/bin/env bash
# Exposes the local Filmpire API Gateway via a secure Cloudflare Tunnel (public HTTPS).
# Works with both Podman and Docker without requiring a Cloudflare account or paid domain.
set -euo pipefail

CONTAINER_NAME="filmpire-cloudflare-tunnel"
DOCKER_CMD="podman"
if command -v docker >/dev/null 2>&1; then
  DOCKER_CMD="docker"
fi

echo "=================================================="
echo "  Starting Cloudflare Tunnel for Local Gateway"
echo "=================================================="

# 1. Stop any existing tunnel container
$DOCKER_CMD rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

# 2. Check if API Gateway is responding locally
if ! curl -s -o /dev/null -m 2 http://localhost:8080/actuator/health; then
  echo "⚠️ Warning: API Gateway does not appear to be running on http://localhost:8080."
  echo "Make sure to run ./infrastructure/scripts/start-infrastructure.sh first."
fi

# 3. Launch cloudflared container in host network mode
echo "🚀 Launching cloudflared container..."
$DOCKER_CMD run -d \
  --name "$CONTAINER_NAME" \
  --network host \
  docker.io/cloudflare/cloudflared:latest \
  tunnel --no-autoupdate --url http://localhost:8080 >/dev/null

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
  echo ""
  echo "=================================================="
  echo "  🎉 Cloudflare Tunnel is LIVE!"
  echo "=================================================="
  echo "  Public URL: $TUNNEL_URL"
  echo ""
  echo "  To connect your Vercel frontend:"
  echo "  1. Open the Admin Dashboard on Vercel"
  echo "  2. In Deploy Control, select 'Local Tunnel' & paste: $TUNNEL_URL"
  echo "  Or set VITE_API_URL=$TUNNEL_URL in Vercel settings."
  echo "=================================================="
  echo "  To stop the tunnel, run: ./infrastructure/scripts/stop-tunnel.sh"
  echo "=================================================="
else
  echo "❌ Error: Could not retrieve tunnel URL. View logs with: $DOCKER_CMD logs $CONTAINER_NAME" >&2
  exit 1
fi
