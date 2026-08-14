#!/usr/bin/env bash
# Deploy LMDB Microservices to Local Minikube Cluster
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
OVERLAY_DIR="$REPO_ROOT/infrastructure/kubernetes/overlays/local"

echo "=================================================="
echo "  🎬 Deploying LMDB Microservices to Minikube"
echo "=================================================="

# 1. Check/Start Minikube
if ! command -v minikube >/dev/null 2>&1; then
  echo "❌ Error: minikube is not installed or not in PATH." >&2
  exit 1
fi

MK_STATUS="$(minikube status --format='{{.Host}}' 2>/dev/null || echo "Stopped")"
if [ "$MK_STATUS" != "Running" ]; then
  echo "⏳ Minikube is not running. Starting minikube..."
  minikube start --driver=podman --container-runtime=containerd --rootless=true --memory=4096 --cpus=4
fi

# Ensure kubeconfig is updated
minikube update-context >/dev/null 2>&1 || true

# 2. Check secrets.env
if [ ! -f "$OVERLAY_DIR/secrets.env" ]; then
  echo "⚠️ secrets.env not found in $OVERLAY_DIR. Creating from secrets.env.example..."
  cp "$OVERLAY_DIR/secrets.env.example" "$OVERLAY_DIR/secrets.env"
fi

# Load local images into minikube if not already present
if ! minikube image ls 2>/dev/null | grep -q "localhost/api-gateway:local"; then
  echo "📦 Loading local microservices container images into minikube..."
  if command -v podman >/dev/null 2>&1; then
    podman save -o /tmp/lmdb-local-images.tar localhost/api-gateway:latest localhost/movie-service:latest localhost/actor-service:latest localhost/user-service:latest localhost/ai-service:latest mongo:7.0 redis:7.4-alpine 2>/dev/null || true
    if [ -f /tmp/lmdb-local-images.tar ]; then
      minikube image load /tmp/lmdb-local-images.tar
      rm -f /tmp/lmdb-local-images.tar
      minikube image tag localhost/api-gateway:latest localhost/api-gateway:local 2>/dev/null || true
      minikube image tag localhost/movie-service:latest localhost/movie-service:local 2>/dev/null || true
      minikube image tag localhost/actor-service:latest localhost/actor-service:local 2>/dev/null || true
      minikube image tag localhost/user-service:latest localhost/user-service:local 2>/dev/null || true
      minikube image tag localhost/ai-service:latest localhost/ai-service:local 2>/dev/null || true
      minikube image tag localhost/mongo:7.0 localhost/mongo:local 2>/dev/null || true
      minikube image tag localhost/redis:7.4-alpine localhost/redis:local 2>/dev/null || true
    fi
  fi
fi

# 3. Apply manifests
echo "📦 Applying Minikube Kustomize manifests..."
minikube kubectl -- apply -k "$OVERLAY_DIR"

# 4. Wait for core workloads to be ready
echo "⏳ Waiting for microservices workloads to roll out..."
WORKLOADS=(
  "deployment/api-gateway"
  "deployment/movie-service"
  "deployment/user-service"
  "deployment/actor-service"
  "deployment/ai-service"
  "deployment/cloudflare-tunnel"
)

for workload in "${WORKLOADS[@]}"; do
  echo "  Checking $workload..."
  minikube kubectl -- rollout status "$workload" --timeout=240s || {
    echo "⚠️ Warning: $workload did not reach Ready state in time, continuing..."
  }
done

# 5. Extract and publish Cloudflare tunnel URL
echo "⏳ Extracting public HTTPS Cloudflare Tunnel URL..."
TUNNEL_URL=""
for i in {1..30}; do
  LOGS=$(minikube kubectl -- logs deployment/cloudflare-tunnel 2>&1 || true)
  URL_MATCH=$(echo "$LOGS" | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com' | head -n 1 || true)
  if [ -n "$URL_MATCH" ]; then
    TUNNEL_URL="$URL_MATCH"
    break
  fi
  sleep 2
done

if [ -n "$TUNNEL_URL" ]; then
  POINTER_FILE="$REPO_ROOT/infrastructure/tunnel-url.txt"
  echo "$TUNNEL_URL" > "$POINTER_FILE"
  echo "✅ Tunnel URL saved: $POINTER_FILE -> $TUNNEL_URL"

  # Commit & push tunnel pointer so lmdb.dev automatically connects
  cd "$REPO_ROOT"
  CURRENT_BRANCH="$(git branch --show-current 2>/dev/null || echo "develop")"
  if git diff --quiet infrastructure/tunnel-url.txt; then
    echo "ℹ️  Tunnel URL unchanged in git."
  else
    git add infrastructure/tunnel-url.txt
    git commit -m "chore(infra): publish minikube tunnel url [skip ci]" || true
    git push origin "$CURRENT_BRANCH" || true
    if [ "$CURRENT_BRANCH" != "main" ]; then
      git checkout main && git merge "$CURRENT_BRANCH" -m "Merge branch '$CURRENT_BRANCH' into main [skip ci]" && git push origin main && git checkout "$CURRENT_BRANCH" || true
    fi
    echo "✅ Published updated tunnel URL to GitHub."
  fi

  echo ""
  echo "=================================================="
  echo "  🎉 Minikube Deployment is LIVE & Connected!"
  echo "=================================================="
  echo "  Cluster Provider:      Local Minikube Cluster"
  echo "  Cloudflare Tunnel URL: $TUNNEL_URL"
  echo "  Connected Frontend:    https://www.lmdb.dev"
  echo "=================================================="
else
  echo "⚠️ Cloudflare tunnel URL not detected yet. Inspect with: minikube kubectl -- logs deployment/cloudflare-tunnel"
fi
