#!/usr/bin/env bash
# Stops the Azure AKS cluster to reduce compute spend to ~$0.25/day (disks only).
# Waits for full de-allocation before returning so callers know cost is zeroed.
# Data on PersistentVolumeClaims (Postgres, MongoDB, Redis, Ollama) is preserved.
# Use `start-azure.sh` to resume the cluster when needed (approx. 2-3 min).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/infrastructure/terraform/azure"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  Filmpire — Stop Azure AKS Backend Cluster         ${NC}"
echo -e "${BLUE}=====================================================${NC}"

# 1. Check prerequisites
for cmd in az terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

# 2. Resolve cluster coordinates from Terraform state (or env overrides)
cd "$TF_DIR"
if [ ! -d ".terraform" ]; then
  echo -e "${BLUE}⚙️  Initialising Terraform backend to read state...${NC}"
  terraform init -backend-config=backend.hcl -input=false -reconfigure >/dev/null 2>&1
fi
RG_NAME="${AZURE_RESOURCE_GROUP:-$(terraform output -raw resource_group_name 2>/dev/null || echo "filmpire-demo")}"
CLUSTER_NAME="${AZURE_CLUSTER_NAME:-$(terraform output -raw cluster_name 2>/dev/null || echo "filmpire-aks")}"

# 3. Check current power state
echo -e "\n${BLUE}🔍 Checking power state for cluster: ${YELLOW}${CLUSTER_NAME}${BLUE} in RG ${YELLOW}${RG_NAME}${NC}"
CURRENT_STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" \
  --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
echo -e "  Current Power State: ${YELLOW}${CURRENT_STATE}${NC}"

if [ "$CURRENT_STATE" == "Stopped" ]; then
  echo -e "\n${GREEN}✅ Cluster is already Stopped — compute is at \$0.${NC}"
elif [ "$CURRENT_STATE" == "Unknown" ]; then
  echo -e "${RED}❌ Could not determine cluster state. Is the cluster provisioned?${NC}" >&2
  exit 1
else
  # 4. Issue stop and wait for de-allocation
  echo -e "\n${BLUE}🛑 Stopping AKS Cluster (${CLUSTER_NAME})...${NC}"
  az aks stop --resource-group "$RG_NAME" --name "$CLUSTER_NAME"

  echo -e "${BLUE}⏳ Waiting for cluster to reach Stopped state...${NC}"
  TIMEOUT=300
  ELAPSED=0
  while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
    STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" \
      --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
    if [ "$STATE" == "Stopped" ]; then
      break
    fi
    sleep 10
    ELAPSED=$((ELAPSED + 10))
    echo -e "  ... still ${STATE} (${ELAPSED}s elapsed)"
  done

  FINAL_STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" \
    --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
  if [ "$FINAL_STATE" != "Stopped" ]; then
    echo -e "${RED}⚠️  Cluster did not reach Stopped state within ${TIMEOUT}s (current: ${FINAL_STATE}).${NC}" >&2
    echo -e "${YELLOW}   It may still be shutting down — check the Azure Portal.${NC}" >&2
    exit 1
  fi
  echo -e "\n${GREEN}✅ Cluster is now Stopped.${NC}"
fi

# 5. Print cost summary
echo -e "\n${CYAN}💰 Cost Summary (approximate):${NC}"
echo -e "  ${GREEN}VM Compute (Standard_D4ls_v7): \$0.00/hr  ← saved!${NC}"
echo -e "  ${YELLOW}Azure Disk PVCs (~16 GiB):     ~\$0.01/hr${NC}"
echo -e "  ${YELLOW}Static Public IP:              ~\$0.004/hr${NC}"
echo -e "  ─────────────────────────────────────────"
echo -e "  ${YELLOW}Total idle cost:               ~\$0.25/day  (vs ~\$5.06/day running)${NC}"
echo -e "\n  📦 Your data (Postgres, MongoDB, Redis, Ollama) is preserved on PVCs."
echo -e "  ▶️  To resume: ${CYAN}./infrastructure/scripts/start-azure.sh${NC}"
echo -e "  🌐 Or via GitHub Actions: trigger the ${CYAN}cluster-stop${NC} workflow with action=start"
echo -e ""
