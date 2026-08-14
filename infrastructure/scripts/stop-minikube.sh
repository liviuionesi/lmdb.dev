#!/usr/bin/env bash
# Stop Local Minikube Cluster
set -euo pipefail

echo "=================================================="
echo "  🛑 Stopping Minikube Cluster"
echo "=================================================="

if command -v minikube >/dev/null 2>&1; then
  MK_STATUS="$(minikube status --format='{{.Host}}' 2>/dev/null || echo "Stopped")"
  if [ "$MK_STATUS" == "Running" ]; then
    echo "Stopping minikube..."
    minikube stop
    echo "✅ Minikube cluster stopped (data preserved in ~/.minikube)."
  else
    echo "ℹ️  Minikube is not running."
  fi
else
  echo "⚠️ Minikube is not installed."
fi
