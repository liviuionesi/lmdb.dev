#!/usr/bin/env bash
# Stops and removes the local Cloudflare Tunnel container.
set -euo pipefail

CONTAINER_NAME="lmdb-cloudflare-tunnel"
DOCKER_CMD="podman"
if command -v docker >/dev/null 2>&1; then
  DOCKER_CMD="docker"
fi

echo "🛑 Stopping Cloudflare Tunnel..."
$DOCKER_CMD stop "$CONTAINER_NAME" >/dev/null 2>&1 || true
$DOCKER_CMD rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
echo "✓ Cloudflare Tunnel stopped and removed."
