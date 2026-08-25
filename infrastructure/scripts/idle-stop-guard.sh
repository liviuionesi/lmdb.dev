#!/usr/bin/env bash
# Tunnel Reconnect Watchdog — monitors the Cloudflare Tunnel container and
# restarts it whenever it exits unexpectedly. Updates tunnel-url.txt and
# optionally pushes it to Git so the deployed frontend picks up the new URL.
#
# Usage:
#   ./infrastructure/scripts/idle-stop-guard.sh [--no-push]
#
#   --no-push   Skip the `git commit && git push` step (useful for quick local
#               demos where GitHub raw serving isn't needed).
#
# Run this in a background terminal alongside start-infrastructure.sh:
#   nohup ./infrastructure/scripts/idle-stop-guard.sh &
set -euo pipefail

CONTAINER_NAME="lmdb-cloudflare-tunnel"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POINTER_FILE="$REPO_ROOT/infrastructure/tunnel-url.txt"
CHECK_INTERVAL_SECONDS=15  # how often to poll the container status
URL_WAIT_ATTEMPTS=40       # up to 40s waiting for cloudflared URL on each start
GIT_PUSH="${GIT_PUSH:-true}"

for arg in "$@"; do
  case "$arg" in
    --no-push) GIT_PUSH="false" ;;
  esac
done

DOCKER_CMD="podman"
if command -v docker >/dev/null 2>&1; then
  DOCKER_CMD="docker"
fi

# ─── helpers ──────────────────────────────────────────────────────────────────

log() { echo "[watchdog] $(date '+%H:%M:%S') $*"; }

# Start (or restart) the cloudflared container and return the tunnel URL.
# Prints the URL to stdout; logs go to stderr.
start_tunnel() {
  log "Launching cloudflared container..." >&2

  # 1. Tear down any existing (dead) container
  $DOCKER_CMD rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true

  # 2. Warn if the API Gateway isn't up yet
  if ! curl -s -o /dev/null -m 2 http://localhost:8080/actuator/health 2>/dev/null; then
    log "⚠️  API Gateway not responding on :8080 — tunnel will start anyway." >&2
  fi

  # 3. Start cloudflared in detached mode
  $DOCKER_CMD run -d \
    --name "$CONTAINER_NAME" \
    --network host \
    docker.io/cloudflare/cloudflared:latest \
    tunnel --no-autoupdate --url http://localhost:8080 >/dev/null

  # 4. Wait up to URL_WAIT_ATTEMPTS seconds for the public URL
  local tunnel_url=""
  for i in $(seq 1 "$URL_WAIT_ATTEMPTS"); do
    local logs
    logs=$($DOCKER_CMD logs "$CONTAINER_NAME" 2>&1 || true)
    tunnel_url=$(echo "$logs" | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com' | head -n 1 || true)
    if [ -n "$tunnel_url" ]; then
      break
    fi
    sleep 1
  done

  if [ -z "$tunnel_url" ]; then
    log "❌ Timed out waiting for tunnel URL after ${URL_WAIT_ATTEMPTS}s." >&2
    return 1
  fi

  echo "$tunnel_url"
}

# Publish the tunnel URL: write tunnel-url.txt, optionally git-push it.
publish_url() {
  local url="$1"
  echo "$url" > "$POINTER_FILE"
  log "✅ tunnel-url.txt updated → $url"

  if [ "$GIT_PUSH" = "true" ]; then
    (
      cd "$REPO_ROOT"
      if git diff --quiet -- infrastructure/tunnel-url.txt 2>/dev/null; then
        log "tunnel-url.txt unchanged, skipping git push."
      else
        git add infrastructure/tunnel-url.txt
        git commit -m "chore: update Cloudflare tunnel URL [skip ci]" --no-verify >/dev/null 2>&1 || true
        git push origin HEAD >/dev/null 2>&1 && log "🚀 Pushed new tunnel URL to remote." \
          || log "⚠️  git push failed — frontend will pick up URL on next container restart."
      fi
    )
  fi
}

# ─── main watchdog loop ────────────────────────────────────────────────────────

log "Tunnel reconnect watchdog starting (interval=${CHECK_INTERVAL_SECONDS}s)."
log "Press Ctrl-C or send SIGTERM to stop."

# Initial start
TUNNEL_URL=$(start_tunnel) || { log "Initial tunnel start failed. Retrying in ${CHECK_INTERVAL_SECONDS}s..."; sleep "$CHECK_INTERVAL_SECONDS"; }
if [ -n "${TUNNEL_URL:-}" ]; then
  publish_url "$TUNNEL_URL"
  log "🎉 Tunnel is LIVE: $TUNNEL_URL"
fi

while true; do
  sleep "$CHECK_INTERVAL_SECONDS"

  # Check if the container is still running
  STATUS=$($DOCKER_CMD inspect --format='{{.State.Status}}' "$CONTAINER_NAME" 2>/dev/null || echo "missing")

  if [ "$STATUS" = "running" ]; then
    # Container is up — optionally verify the URL is still accessible
    CURRENT_URL=$(cat "$POINTER_FILE" 2>/dev/null || true)
    if [ -n "$CURRENT_URL" ]; then
      if ! curl -s -o /dev/null -m 5 "$CURRENT_URL" 2>/dev/null; then
        log "⚠️  Tunnel URL $CURRENT_URL is not responding — forcing restart."
        STATUS="dead"
      fi
    fi
  fi

  if [ "$STATUS" != "running" ]; then
    log "🔄 Tunnel container is '$STATUS'. Restarting..."
    NEW_URL=$(start_tunnel 2>&1) || { log "Restart failed — will retry."; continue; }
    TUNNEL_URL="$NEW_URL"
    publish_url "$TUNNEL_URL"
    log "🎉 Tunnel reconnected: $TUNNEL_URL"
  fi
done
