#!/usr/bin/env bash
# Deploys LMDB backend to AWS k3s on EC2 via Terraform.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/infrastructure/terraform/aws"
K8S_OVERLAY="$REPO_ROOT/infrastructure/kubernetes/overlays/aws"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  LMDB — Deploy to AWS (Terraform + k3s)         ${NC}"
echo -e "${BLUE}=====================================================${NC}"

for cmd in aws terraform kubectl ssh; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

cd "$TF_DIR"
echo -e "\n${BLUE}📦 Initializing & Applying Terraform in ${TF_DIR}...${NC}"
terraform init -backend-config=backend.hcl -input=false
terraform apply -auto-approve

PUBLIC_IP="$(terraform output -raw public_ip)"
SSH_USER="$(terraform output -raw ssh_user)"
PORT="$(terraform output -raw demo_inbound_port || echo 30080)"

echo -e "\n${BLUE}🔑 Fetching kubeconfig from k3s node (${SSH_USER}@${PUBLIC_IP})...${NC}"
TEMP_KUBECONFIG="$(mktemp)"
MAX_ATTEMPTS=30
ATTEMPT=0
while [ "$ATTEMPT" -lt "$MAX_ATTEMPTS" ]; do
  if ssh -o StrictHostKeyChecking=accept-new -o ConnectTimeout=5 "${SSH_USER}@${PUBLIC_IP}" "sudo test -f /etc/rancher/k3s/k3s.yaml" 2>/dev/null; then
    ssh -o StrictHostKeyChecking=accept-new "${SSH_USER}@${PUBLIC_IP}" "sudo cat /etc/rancher/k3s/k3s.yaml" > "$TEMP_KUBECONFIG"
    break
  fi
  ATTEMPT=$((ATTEMPT + 1))
  echo -e "  ... waiting for k3s initialization on node (${ATTEMPT}/${MAX_ATTEMPTS})..."
  sleep 5
done

if [ ! -s "$TEMP_KUBECONFIG" ]; then
  echo -e "${RED}❌ Could not retrieve kubeconfig from k3s node after ${MAX_ATTEMPTS} attempts.${NC}" >&2
  exit 1
fi

sed -i "s/127.0.0.1/${PUBLIC_IP}/g" "$TEMP_KUBECONFIG"
export KUBECONFIG="$TEMP_KUBECONFIG"

echo -e "\n${BLUE}🚀 Deploying Kubernetes services (${K8S_OVERLAY})...${NC}"
cd "$REPO_ROOT"
kubectl apply -k "$K8S_OVERLAY"

# Wait for rollouts (full local-parity service set, #151) - same set as
# deploy-azure.sh.
echo -e "\n${BLUE}⏳ Waiting for microservices rollout...${NC}"
kubectl rollout status deployment/api-gateway --timeout=180s
kubectl rollout status deployment/movie-service --timeout=180s
kubectl rollout status deployment/actor-service --timeout=180s
kubectl rollout status deployment/user-service --timeout=180s
kubectl rollout status deployment/ai-service --timeout=180s
kubectl rollout status deployment/caddy-tls --timeout=180s
kubectl rollout status statefulset/mongodb --timeout=180s
kubectl rollout status statefulset/postgres --timeout=180s
kubectl rollout status statefulset/redis --timeout=180s
kubectl rollout status statefulset/ollama --timeout=180s

# Pull Ollama's models - manual, same one-time step as local dev; see
# deploy-azure.sh's identical comment for why this isn't automated.
echo -e "\n${BLUE}🧠 Pulling Ollama models (llama3.2, nomic-embed-text) - this can take a few minutes on first deploy...${NC}"
kubectl exec statefulset/ollama -- ollama pull llama3.2
kubectl exec statefulset/ollama -- ollama pull nomic-embed-text

if [ -f "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" ] && [ -n "${DUCKDNS_TOKEN:-}" ]; then
  echo -e "\n${BLUE}🦆 Updating DuckDNS domain record (lmdb-api.duckdns.org → ${PUBLIC_IP})...${NC}"
  "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" "$PUBLIC_IP" || true
fi

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  🎉 AWS k3s Backend Deployed Successfully!         ${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e "  API Gateway:     http://${PUBLIC_IP}:${PORT}"
echo -e "  Actuator Health: http://${PUBLIC_IP}:${PORT}/actuator/health"
echo -e "  Register:        POST http://${PUBLIC_IP}:${PORT}/api/v1/auth/register"
echo -e "  ${YELLOW}A browser on HTTPS can't call this http:// IP directly (mixed content) -${NC}"
echo -e "  ${YELLOW}see docs/guides/DEPLOYMENT_GUIDE.md §5 for fronting it with a tunnel.${NC}"
echo -e "  Teardown:        ./gradlew destroyAws (or ./infrastructure/scripts/destroy-aws.sh)"
echo -e "${GREEN}=====================================================${NC}\n"
