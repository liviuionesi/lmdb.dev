#!/usr/bin/env bash
# Starts an existing stopped Azure AKS cluster to resume Filmpire backend.
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
if [ ! -f "terraform.tfstate" ] && [ ! -d ".terraform" ]; then
  terraform init -backend-config=backend.hcl -input=false >/dev/null 2>&1 || true
fi

RG_NAME="${AZURE_RESOURCE_GROUP:-$(terraform output -raw resource_group_name 2>/dev/null || echo "rg-filmpire-demo")}"
CLUSTER_NAME="${AZURE_CLUSTER_NAME:-$(terraform output -raw cluster_name 2>/dev/null || echo "aks-filmpire-demo")}"
PORT="${DEMO_PORT:-$(terraform output -raw demo_inbound_port 2>/dev/null || echo "30080")}"

echo -e "\n${BLUE}🔍 Checking current AKS cluster power state for ${CLUSTER_NAME}...${NC}"
CURRENT_STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
echo -e "  Current Power State: ${YELLOW}${CURRENT_STATE}${NC}"

if [ "$CURRENT_STATE" == "Running" ]; then
  echo -e "${GREEN}✅ Cluster is already Running!${NC}"
else
  echo -e "\n${BLUE}⚡ Starting AKS Cluster (${CLUSTER_NAME})...${NC}"
  az aks start --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --no-wait
  
  echo -e "${BLUE}⏳ Waiting for cluster to reach Running state...${NC}"
  az aks wait --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --custom "powerState.code=='Running'" --timeout 180
fi

# Fetch credentials & find public IP
if command -v kubectl >/dev/null 2>&1; then
  echo -e "\n${BLUE}🔑 Refreshing AKS credentials...${NC}"
  az aks get-credentials --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --overwrite-existing
  
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
