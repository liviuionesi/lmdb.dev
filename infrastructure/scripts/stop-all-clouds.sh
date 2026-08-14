#!/usr/bin/env bash
# stop-all-clouds.sh — Detects which cloud(s) are provisioned and running,
# then stops them all to minimise idle spend. Safe to run at end of day.
# Does NOT destroy infrastructure — PVC data and Terraform state are preserved.
#
# Usage:
#   ./infrastructure/scripts/stop-all-clouds.sh           # stop all running clouds
#   ./infrastructure/scripts/stop-all-clouds.sh --dry-run # print what WOULD be stopped
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_AZURE="$REPO_ROOT/infrastructure/terraform/azure"
TF_AWS="$REPO_ROOT/infrastructure/terraform/aws"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'

DRY_RUN=false
for arg in "$@"; do
  [[ "$arg" == "--dry-run" ]] && DRY_RUN=true
done

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  LMDB — Stop All Cloud Backends                 ${NC}"
if $DRY_RUN; then
  echo -e "${YELLOW}  [DRY RUN — no changes will be made]               ${NC}"
fi
echo -e "${BLUE}=====================================================${NC}"

STOPPED_COUNT=0
ALREADY_STOPPED=0
SKIPPED=0

# ── Azure AKS ────────────────────────────────────────────────────────────────
echo -e "\n${BLUE}── Azure AKS ──────────────────────────────────────${NC}"
if command -v az >/dev/null 2>&1; then
  READ_AKS="$(az aks list --query "[0].[resourceGroup, name]" -o tsv 2>/dev/null || echo "")"
  if [ -n "$READ_AKS" ]; then
    RG_NAME="$(echo "$READ_AKS" | sed -n '1p')"
    CLUSTER_NAME="$(echo "$READ_AKS" | sed -n '2p')"
    STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" \
      --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
    echo -e "  Cluster ${YELLOW}${CLUSTER_NAME}${NC} (RG: ${YELLOW}${RG_NAME}${NC}) — Power State: ${YELLOW}${STATE}${NC}"

    if [ "$STATE" == "Stopped" ]; then
      echo -e "  ${GREEN}✅ Already stopped — no action needed.${NC}"
      ALREADY_STOPPED=$((ALREADY_STOPPED + 1))
    elif [ "$STATE" == "Unknown" ]; then
      echo -e "  ${YELLOW}⚠️  Could not determine state — skipping.${NC}"
      SKIPPED=$((SKIPPED + 1))
    else
      if $DRY_RUN; then
        echo -e "  ${CYAN}[DRY RUN] Would stop: az aks stop --resource-group $RG_NAME --name $CLUSTER_NAME${NC}"
      else
        echo -e "  ${BLUE}🛑 Stopping...${NC}"
        "$SCRIPT_DIR/stop-azure.sh"
        STOPPED_COUNT=$((STOPPED_COUNT + 1))
      fi
    fi
  else
    echo -e "  ${YELLOW}ℹ️  No AKS cluster found in Azure subscription — skipping.${NC}"
    SKIPPED=$((SKIPPED + 1))
  fi
else
  echo -e "  ${YELLOW}ℹ️  Azure CLI not installed or not authenticated — skipping.${NC}"
  SKIPPED=$((SKIPPED + 1))
fi

# ── AWS k3s ───────────────────────────────────────────────────────────────────
echo -e "\n${BLUE}── AWS k3s ────────────────────────────────────────${NC}"
if command -v aws >/dev/null 2>&1; then
  INSTANCE_ID="$(aws ec2 describe-instances --filters "Name=tag:Name,Values=lmdb-k3s-demo,lmdb-k3s" "Name=instance-state-name,Values=running,stopped" --query "Reservations[0].Instances[0].InstanceId" --output text 2>/dev/null || echo "")"

  if [ -n "$INSTANCE_ID" ] && [ "$INSTANCE_ID" != "None" ]; then
    STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" \
      --query "Reservations[0].Instances[0].State.Name" --output text 2>/dev/null || echo "unknown")"
    echo -e "  Instance ${YELLOW}${INSTANCE_ID}${NC} — State: ${YELLOW}${STATE}${NC}"

    if [ "$STATE" == "stopped" ]; then
      echo -e "  ${GREEN}✅ Already stopped — no action needed.${NC}"
      ALREADY_STOPPED=$((ALREADY_STOPPED + 1))
    elif [ "$STATE" == "running" ]; then
      if $DRY_RUN; then
        echo -e "  ${CYAN}[DRY RUN] Would stop: aws ec2 stop-instances --instance-ids $INSTANCE_ID${NC}"
      else
        echo -e "  ${BLUE}🛑 Stopping AWS k3s instance...${NC}"
        aws ec2 stop-instances --instance-ids "$INSTANCE_ID" >/dev/null
        echo -e "  ${GREEN}✅ Stop signal sent. Data preserved (EBS volume).${NC}"
        STOPPED_COUNT=$((STOPPED_COUNT + 1))
      fi
    else
      echo -e "  ${YELLOW}⚠️  Instance state is '${STATE}' — skipping.${NC}"
      SKIPPED=$((SKIPPED + 1))
    fi
  else
    echo -e "  ${YELLOW}ℹ️  No LMDB EC2 instance found in AWS — skipping.${NC}"
    SKIPPED=$((SKIPPED + 1))
  fi
else
  echo -e "  ${YELLOW}ℹ️  AWS CLI not installed or not configured — skipping.${NC}"
  SKIPPED=$((SKIPPED + 1))
fi

# ── Minikube ──────────────────────────────────────────────────────────────────
echo -e "\n${BLUE}── Minikube (local) ───────────────────────────────${NC}"
if command -v minikube >/dev/null 2>&1; then
  MK_STATUS="$(minikube status --format='{{.Host}}' 2>/dev/null || echo "Stopped")"
  echo -e "  Minikube Host State: ${YELLOW}${MK_STATUS}${NC}"
  if [ "$MK_STATUS" == "Running" ]; then
    if $DRY_RUN; then
      echo -e "  ${CYAN}[DRY RUN] Would stop: minikube stop${NC}"
    else
      echo -e "  ${BLUE}🛑 Stopping Minikube...${NC}"
      minikube stop
      echo -e "  ${GREEN}✅ Minikube stopped. Data preserved in ~/.minikube.${NC}"
      STOPPED_COUNT=$((STOPPED_COUNT + 1))
    fi
  else
    echo -e "  ${GREEN}✅ Minikube not running — no action needed.${NC}"
    ALREADY_STOPPED=$((ALREADY_STOPPED + 1))
  fi
else
  echo -e "  ${YELLOW}ℹ️  Minikube not installed — skipping.${NC}"
  SKIPPED=$((SKIPPED + 1))
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  Summary${NC}"
echo -e "${GREEN}=====================================================${NC}"
if $DRY_RUN; then
  echo -e "  ${CYAN}[DRY RUN] — No changes made. Re-run without --dry-run to apply.${NC}"
else
  echo -e "  Stopped:        ${STOPPED_COUNT} cloud(s)"
  echo -e "  Already idle:   ${ALREADY_STOPPED} cloud(s)"
  echo -e "  Skipped:        ${SKIPPED} cloud(s) (not configured)"
  echo -e ""
  if [ "$STOPPED_COUNT" -gt 0 ]; then
    echo -e "  ${GREEN}💰 Cloud compute scaled to \$0. Residual disk/IP cost: ~\$0.25/day.${NC}"
  else
    echo -e "  ${GREEN}💰 No compute was running — already at minimum cost.${NC}"
  fi
fi
echo -e "${GREEN}=====================================================${NC}"
echo ""
