#!/usr/bin/env bash
# Stops an active Azure AKS cluster to scale compute to zero ($0 idle cost).
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
echo -e "${BLUE}  Filmpire — Stop Azure AKS Backend Cluster (Idle)  ${NC}"
echo -e "${BLUE}=====================================================${NC}"

for cmd in az terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

cd "$TF_DIR"
RG_NAME="${AZURE_RESOURCE_GROUP:-$(terraform output -raw resource_group_name 2>/dev/null || echo "rg-filmpire-demo")}"
CLUSTER_NAME="${AZURE_CLUSTER_NAME:-$(terraform output -raw cluster_name 2>/dev/null || echo "aks-filmpire-demo")}"

echo -e "\n${BLUE}🔍 Checking current AKS cluster power state for ${CLUSTER_NAME}...${NC}"
CURRENT_STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
echo -e "  Current Power State: ${YELLOW}${CURRENT_STATE}${NC}"

if [ "$CURRENT_STATE" == "Stopped" ]; then
  echo -e "${GREEN}✅ Cluster is already Stopped! Compute is at $0 spend.${NC}"
else
  echo -e "\n${BLUE}🛑 Stopping AKS Cluster (${CLUSTER_NAME})...${NC}"
  az aks stop --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --no-wait
  echo -e "${GREEN}✅ Stop command issued. AKS nodes are shutting down.${NC}"
fi
