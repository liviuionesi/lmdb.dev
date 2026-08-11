#!/usr/bin/env bash
# Starts or provisions Azure AKS cluster to run Filmpire backend.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/infrastructure/terraform/azure"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  Filmpire — Start Azure AKS Backend Cluster        ${NC}"
echo -e "${BLUE}=====================================================${NC}"

for cmd in az terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

cd "$TF_DIR"
if [ ! -d ".terraform" ]; then
  terraform init -backend-config=backend.hcl -input=false
fi

RG_NAME="${AZURE_RESOURCE_GROUP:-rg-filmpire-demo}"
CLUSTER_NAME="${AZURE_CLUSTER_NAME:-aks-filmpire-demo}"

echo -e "\n${BLUE}🔍 Checking current AKS cluster power state for ${CLUSTER_NAME}...${NC}"
CURRENT_STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --query "powerState.code" -o tsv 2>/dev/null || echo "NotFound")"
echo -e "  Current Power State: ${YELLOW}${CURRENT_STATE}${NC}"

if [ "$CURRENT_STATE" == "Running" ]; then
  echo -e "${GREEN}✅ Cluster is already Running!${NC}"
elif [ "$CURRENT_STATE" == "Stopped" ]; then
  echo -e "\n${BLUE}⚡ Resuming stopped AKS Cluster (${CLUSTER_NAME})...${NC}"
  az aks start --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --no-wait
  
  echo -e "${BLUE}⏳ Waiting for cluster to reach Running state...${NC}"
  az aks wait --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --custom "powerState.code=='Running'" --timeout 240
else
  echo -e "\n${BLUE}🚀 Provisioning AKS Cluster with Terraform...${NC}"
  terraform apply -auto-approve -input=false
fi

# Fetch credentials & deploy Kubernetes manifests if needed
if command -v kubectl >/dev/null 2>&1; then
  echo -e "\n${BLUE}🔑 Refreshing AKS credentials...${NC}"
  az aks get-credentials --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --overwrite-existing
  
  if [ -d "$REPO_ROOT/infrastructure/kubernetes/overlays/azure" ]; then
    echo -e "${BLUE}📦 Applying Kubernetes manifests...${NC}"
    kubectl apply -k "$REPO_ROOT/infrastructure/kubernetes/overlays/azure" || true
  fi

  echo -e "${BLUE}🌐 Checking node IP...${NC}"
  NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}' 2>/dev/null || true)"
  if [ -n "$NODE_IP" ]; then
    echo -e "${GREEN}✅ Node IP: ${NODE_IP}${NC}"
    if [ -f "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" ] && [ -n "${DUCKDNS_TOKEN:-}" ]; then
      "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" "$NODE_IP" || true
    fi
  fi
fi

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  🎉 Azure Backend Started Successfully!             ${NC}"
echo -e "${GREEN}=====================================================${NC}"
