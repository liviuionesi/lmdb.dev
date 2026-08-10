#!/usr/bin/env bash
# Deploys Filmpire backend to AWS k3s on EC2 via Terraform.
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
echo -e "${BLUE}  Filmpire — Deploy to AWS (Terraform + k3s)         ${NC}"
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
ssh -o StrictHostKeyChecking=accept-new "${SSH_USER}@${PUBLIC_IP}" "sudo cat /etc/rancher/k3s/k3s.yaml" > "$TEMP_KUBECONFIG"
sed -i "s/127.0.0.1/${PUBLIC_IP}/g" "$TEMP_KUBECONFIG"
export KUBECONFIG="$TEMP_KUBECONFIG"

echo -e "\n${BLUE}🚀 Deploying Kubernetes services (${K8S_OVERLAY})...${NC}"
cd "$REPO_ROOT"
kubectl apply -k "$K8S_OVERLAY"

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  🎉 AWS k3s Backend Deployed Successfully!         ${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e "  API Gateway:     http://${PUBLIC_IP}:${PORT}"
echo -e "  Actuator Health: http://${PUBLIC_IP}:${PORT}/actuator/health"
echo -e "  Teardown:        ./gradlew destroyAws (or ./infrastructure/scripts/destroy-aws.sh)"
echo -e "${GREEN}=====================================================${NC}\n"
