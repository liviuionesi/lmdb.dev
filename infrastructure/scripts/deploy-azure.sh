#!/usr/bin/env bash
# Deploys LMDB backend to Azure AKS via Terraform and Kubernetes overlays.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/infrastructure/terraform/azure"
K8S_OVERLAY="$REPO_ROOT/infrastructure/kubernetes/overlays/azure"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  LMDB — Deploy to Azure AKS (Terraform + K8s)  ${NC}"
echo -e "${BLUE}=====================================================${NC}"

# 1. Verify required CLI tools
for cmd in az terraform kubectl; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

# 2. Verify Azure login
if ! az account show >/dev/null 2>&1; then
  echo -e "${YELLOW}⚠️ Azure CLI is not logged in. Running 'az login'...${NC}"
  az login
fi

# 3. Terraform Init & Apply
echo -e "\n${BLUE}📦 Initializing & Applying Terraform in ${TF_DIR}...${NC}"
cd "$TF_DIR"
terraform init -backend-config=backend.hcl -input=false
terraform apply -auto-approve

RG_NAME="$(terraform output -raw resource_group_name)"
CLUSTER_NAME="$(terraform output -raw cluster_name)"
PORT="$(terraform output -raw demo_inbound_port || echo 30080)"

# 4. Fetch AKS Credentials
echo -e "\n${BLUE}🔑 Fetching AKS cluster credentials...${NC}"
az aks get-credentials --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --overwrite-existing

# 4b. Update DuckDNS *before* any pod (Caddy included) starts — Caddy
# requests its TLS cert immediately on boot, so DNS must already point here
# or the ACME challenge fails and Caddy won't retry for a while (#175). The
# node exists as soon as the cluster does, so its external IP is already
# resolvable here without waiting on a workload rollout.
NODE_IP=""
for i in {1..12}; do
  NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}' 2>/dev/null || true)"
  if [ -n "$NODE_IP" ]; then
    break
  fi
  sleep 2
done
if [ -n "$NODE_IP" ] && [ -f "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" ] && [ -n "${DUCKDNS_TOKEN:-}" ]; then
  echo -e "\n${BLUE}🦆 Updating DuckDNS domain record (lmdb-api.duckdns.org → ${NODE_IP})...${NC}"
  "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" "$NODE_IP" || true
fi

# 5. Apply Kubernetes Overlays
echo -e "\n${BLUE}🚀 Deploying Kubernetes services (${K8S_OVERLAY})...${NC}"
cd "$REPO_ROOT"
kubectl apply -k "$K8S_OVERLAY"

# 6. Wait for Rollouts (full local-parity service set, #151)
echo -e "\n${BLUE}⏳ Waiting for microservices rollout...${NC}"
kubectl rollout status deployment/api-gateway --timeout=180s
kubectl rollout status deployment/movie-service --timeout=180s
kubectl rollout status deployment/actor-service --timeout=180s
kubectl rollout status deployment/user-service --timeout=180s
kubectl rollout status deployment/ai-service --timeout=180s
kubectl rollout status statefulset/mongodb --timeout=180s
kubectl rollout status statefulset/postgres --timeout=180s
kubectl rollout status statefulset/redis --timeout=180s
# Ollama's image itself starts fine without a model pulled, but the
# readiness/liveness probes run `ollama list` which only meaningfully
# passes once the daemon is fully up - give it the same timeout, not a
# rollout-status wait tied to actual model availability (that's the manual
# step below, same as local dev).
kubectl rollout status statefulset/ollama --timeout=180s

# 6b. Pull Ollama's models (manual, same one-time step as local dev - see
# docker-compose.yml's equivalent comment. Not automated: multi-GB
# downloads on every fresh cluster would make first deploy slow and
# failure-prone; the StatefulSet's PVC keeps them across pod restarts once
# pulled, so this only needs to run again after a full destroy/recreate.)
echo -e "\n${BLUE}🧠 Pulling Ollama models (llama3.2, nomic-embed-text) - this can take a few minutes on first deploy...${NC}"
kubectl exec statefulset/ollama -- ollama pull llama3.2
kubectl exec statefulset/ollama -- ollama pull nomic-embed-text

# 7. Re-confirm Node External IP (DuckDNS was already updated in step 4b,
# before any pod started — this just re-resolves it in case that early
# lookup came back empty while the cluster was still settling).
if [ -z "$NODE_IP" ]; then
  echo -e "\n${BLUE}🌐 Discovering AKS Node Public IP...${NC}"
  for i in {1..12}; do
    NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}' 2>/dev/null || true)"
    if [ -n "$NODE_IP" ]; then
      break
    fi
    sleep 2
  done
  if [ -n "$NODE_IP" ] && [ -f "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" ] && [ -n "${DUCKDNS_TOKEN:-}" ]; then
    echo -e "\n${BLUE}🦆 Updating DuckDNS domain record (lmdb-api.duckdns.org → ${NODE_IP})...${NC}"
    "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" "$NODE_IP" || true
  fi
fi

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  🎉 Azure Backend Deployed Successfully!            ${NC}"
echo -e "${GREEN}=====================================================${NC}"
if [ -n "$NODE_IP" ]; then
  echo -e "  API Gateway:     http://${NODE_IP}:${PORT}"
  echo -e "  Actuator Health: http://${NODE_IP}:${PORT}/actuator/health"
  echo -e "  Popular Movies:  http://${NODE_IP}:${PORT}/movie/popular"
  echo -e "  Register:        POST http://${NODE_IP}:${PORT}/api/v1/auth/register"
  echo -e "  ${YELLOW}A browser on HTTPS can't call this http:// IP directly (mixed content) -${NC}"
  echo -e "  ${YELLOW}see docs/guides/DEPLOYMENT_GUIDE.md §5 for fronting it with a tunnel.${NC}"
else
  echo -e "  Node External IP could not be detected yet. Run 'kubectl get nodes -o wide'."
fi
echo -e "  Teardown:        ./gradlew destroyAzure (or ./infrastructure/scripts/destroy-azure.sh)"
echo -e "${GREEN}=====================================================${NC}\n"
