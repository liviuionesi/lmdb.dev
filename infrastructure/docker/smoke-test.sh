#!/usr/bin/env bash
#
# smoke-test.sh — automated live-stack API acceptance test (issue #47).
#
# Waits for the LMDB stack to be healthy, then runs the Postman/newman
# collection against it. One source of truth for "the services are up — prove
# the API works", used both locally and in CI:
#
#   # against a stack you already started (the day-to-day local case):
#   ./infrastructure/docker/smoke-test.sh
#
#   # bring the whole stack up, test it, tear it down (the CI case):
#   MANAGE_STACK=true TMDB_API_KEY=xxxx ./infrastructure/docker/smoke-test.sh
#
# Exit code is newman's: 0 = every request/assertion passed, non-zero = a
# failure the caller (a shell, or a CI job) can gate on.
#
# Configuration (all optional, sane defaults):
#   GATEWAY_URL     Base URL of the running gateway   (default http://localhost:8080)
#   COLLECTION      Postman collection path           (default docs/api/LMDB-API.postman_collection.json)
#   HEALTH_TIMEOUT  Seconds to wait for health        (default 180)
#   MANAGE_STACK    "true" to up/down the compose stack (default false)
#   COMPOSE_FILE    Compose file for MANAGE_STACK     (default infrastructure/docker/docker-compose.yml)
#   TMDB_API_KEY    Passed to the stack when managed  (required for MANAGE_STACK=true)
#   TMDB_USERNAME   TMDB account login, optional      (unlocks folder 14's account flow)
#   TMDB_PASSWORD   TMDB account password, optional   (never store these in the collection)
#   NEWMAN          newman invocation                 (default "npx --yes newman")
#
set -euo pipefail

# Resolve the repo root from this script's location, so it works regardless of
# the caller's working directory.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

GATEWAY_URL="${GATEWAY_URL:-http://localhost:8080}"
COLLECTION="${COLLECTION:-$REPO_ROOT/docs/api/LMDB-API.postman_collection.json}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"
MANAGE_STACK="${MANAGE_STACK:-false}"
COMPOSE_FILE="${COMPOSE_FILE:-$REPO_ROOT/infrastructure/docker/docker-compose.yml}"
NEWMAN="${NEWMAN:-npx --yes newman}"
REPORT_FILE="${REPORT_FILE:-$REPO_ROOT/newman-report.xml}"

# Downstream services newman exercises, polled on their host-mapped ports. The
# gateway itself is polled at GATEWAY_URL directly (see wait_for_health) — a
# healthy gateway alone is not enough, because it can be up while a downstream
# is still starting, which would flake the collection.
#   name:port
HEALTH_TARGETS=(
  "movie-service:8081"
  "user-service:8082"
  "actor-service:8083"
  "ai-service:8084"
)

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; BLUE='\033[0;34m'; NC='\033[0m'
log()  { echo -e "${BLUE}[smoke]${NC} $*"; }
ok()   { echo -e "${GREEN}[smoke] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[smoke] ⚠${NC} $*"; }
err()  { echo -e "${RED}[smoke] ✗${NC} $*" >&2; }

# ---------------------------------------------------------------------------
# Compose command detection — the dev machine uses podman-compose, CI runners
# use `docker compose`. Only needed when MANAGE_STACK=true.
# ---------------------------------------------------------------------------
COMPOSE=""
detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
  elif command -v podman-compose >/dev/null 2>&1; then
    COMPOSE="podman-compose"
  elif podman compose version >/dev/null 2>&1; then
    COMPOSE="podman compose"
  else
    err "No compose command found (need 'docker compose' or 'podman-compose') for MANAGE_STACK=true."
    exit 2
  fi
  log "Using compose command: $COMPOSE"
}

# ---------------------------------------------------------------------------
# Stack lifecycle (MANAGE_STACK=true only).
# ---------------------------------------------------------------------------
stack_up() {
  if [[ -z "${TMDB_API_KEY:-}" ]]; then
    err "MANAGE_STACK=true requires TMDB_API_KEY (the movie/actor/gateway containers need it)."
    exit 2
  fi
  log "Bringing the stack up (this builds images on first run)…"
  # shellcheck disable=SC2086
  TMDB_API_KEY="$TMDB_API_KEY" $COMPOSE -f "$COMPOSE_FILE" up -d --build

  # ~2.3GB combined; budgeted against e2e-smoke.yml's 30-minute job timeout
  # alongside the image builds and HEALTH_TIMEOUT below — on a GitHub-hosted
  # runner this normally finishes in well under a minute. `|| true` so a slow
  # or failed pull degrades ai-service's health check rather than aborting the
  # whole run before movie/user/actor-service ever get exercised.
  log "Ensuring Ollama models (llama3.2, nomic-embed-text) are present…"
  # shellcheck disable=SC2086
  $COMPOSE -f "$COMPOSE_FILE" exec -T ollama ollama pull llama3.2 || true
  # shellcheck disable=SC2086
  $COMPOSE -f "$COMPOSE_FILE" exec -T ollama ollama pull nomic-embed-text || true
}

# Tear down and, on a failed run, dump logs first so CI has something to read.
stack_down() {
  local rc=$1
  if [[ "$rc" -ne 0 ]]; then
    warn "Run failed (rc=$rc) — dumping recent container logs:"
    # shellcheck disable=SC2086
    $COMPOSE -f "$COMPOSE_FILE" logs --tail=80 || true
  fi
  log "Tearing the stack down…"
  # shellcheck disable=SC2086
  $COMPOSE -f "$COMPOSE_FILE" down -v || true
}

# ---------------------------------------------------------------------------
# Health gate — poll every target until all report a 2xx on /actuator/health,
# or give up after HEALTH_TIMEOUT and fail (better than running newman against a
# half-started stack and getting confusing assertion failures).
# ---------------------------------------------------------------------------
wait_for_health() {
  # Downstreams share the gateway's host but expose their own ports.
  local host
  host="$(echo "$GATEWAY_URL" | sed -E 's#^https?://([^:/]+).*#\1#')"

  log "Waiting up to ${HEALTH_TIMEOUT}s for services to become healthy…"
  local deadline=$((SECONDS + HEALTH_TIMEOUT))

  # The gateway is polled at its real URL (honors a non-default host/port), so a
  # wrong/dead GATEWAY_URL fails the gate instead of silently checking :8080.
  local -a checks=("gateway|${GATEWAY_URL%/}/actuator/health")
  for target in "${HEALTH_TARGETS[@]}"; do
    checks+=("${target%%:*}|http://${host}:${target##*:}/actuator/health")
  done

  for check in "${checks[@]}"; do
    local name="${check%%|*}" url="${check#*|}"
    until curl -fsS "$url" >/dev/null 2>&1; do
      if (( SECONDS >= deadline )); then
        err "Timed out waiting for ${name} at ${url}"
        return 1
      fi
      sleep 3
    done
    ok "${name} healthy"
  done
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
RC=0

if [[ "$MANAGE_STACK" == "true" ]]; then
  detect_compose
  # Ensure teardown happens whatever exits us from here on.
  trap 'stack_down $RC' EXIT
  stack_up
fi

if ! wait_for_health; then
  RC=1
  exit $RC
fi

if [[ ! -f "$COLLECTION" ]]; then
  err "Collection not found: $COLLECTION"
  RC=2
  exit $RC
fi

# Optional TMDB account credentials (issue #33 AC2/AC3). Supplied here rather
# than stored in the collection, which is version-controlled. When set, folder
# 14 approves its request token via TMDB's validate_with_login endpoint and the
# whole login flow — session, account, favorites, watchlist — runs unattended.
# When unset the collection asserts the error-passthrough path instead, so the
# default CI run stays green.
#
# Falls back to the gitignored infrastructure/docker/.env so the password lives
# in exactly one untracked file rather than in a shell history, a CI log, or the
# collection. Only these two keys are read, and an already-exported value wins,
# so CI (which sets them as secrets) is unaffected. Sourced in a subshell-free
# read loop rather than `source`, so an unrelated line in .env cannot execute.
ENV_FILE="${ENV_FILE:-$SCRIPT_DIR/.env}"
if [[ -f "$ENV_FILE" ]]; then
  while IFS='=' read -r key value; do
    case "$key" in
      TMDB_USERNAME) [[ -z "${TMDB_USERNAME:-}" ]] && TMDB_USERNAME="$value" ;;
      TMDB_PASSWORD) [[ -z "${TMDB_PASSWORD:-}" ]] && TMDB_PASSWORD="$value" ;;
    esac
  done < <(grep -E '^TMDB_(USERNAME|PASSWORD)=' "$ENV_FILE" || true)
fi

TMDB_CREDS=()
if [[ -n "${TMDB_USERNAME:-}" && -n "${TMDB_PASSWORD:-}" ]]; then
  TMDB_CREDS=(--env-var "tmdb_username=${TMDB_USERNAME}"
              --env-var "tmdb_password=${TMDB_PASSWORD}")
  log "TMDB credentials supplied — folder 14 will run the authenticated account flow."
else
  log "No TMDB_USERNAME/TMDB_PASSWORD — folder 14 covers the unauthenticated contract only."
fi

log "Running newman against ${GATEWAY_URL}…"
# --env-var gateway_url overrides the collection variable so the same collection
# works against any host (localhost, a compose network alias, a remote stack).
if $NEWMAN run "$COLLECTION" \
      --env-var "gateway_url=${GATEWAY_URL}" \
      "${TMDB_CREDS[@]+"${TMDB_CREDS[@]}"}" \
      --reporters cli,junit \
      --reporter-junit-export "$REPORT_FILE"; then
  ok "Smoke test passed — report: $REPORT_FILE"
  RC=0
else
  RC=$?
  err "Smoke test FAILED (newman rc=$RC) — report: $REPORT_FILE"
fi

exit $RC
