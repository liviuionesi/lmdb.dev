#!/usr/bin/env bash
# Stops an active AWS EC2 k3s instance to scale compute to zero ($0 idle compute spend).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/infrastructure/terraform/aws"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  Filmpire — Stop AWS EC2 Backend Instance (Idle)   ${NC}"
echo -e "${BLUE}=====================================================${NC}"

for cmd in aws terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

cd "$TF_DIR"
INSTANCE_ID="${AWS_INSTANCE_ID:-$(terraform output -raw instance_id 2>/dev/null || true)}"
if [ -z "$INSTANCE_ID" ]; then
  INSTANCE_ID="$(aws ec2 describe-instances --filters "Name=tag:Name,Values=filmpire-k3s-demo" "Name=instance-state-name,Values=running,stopped" --query "Reservations[0].Instances[0].InstanceId" --output text 2>/dev/null || echo "")"
fi

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" == "None" ]; then
  echo -e "${RED}❌ Could not find EC2 instance for Filmpire.${NC}" >&2
  exit 1
fi

echo -e "\n${BLUE}🔍 Checking EC2 instance state for ${INSTANCE_ID}...${NC}"
STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query "Reservations[0].Instances[0].State.Name" --output text)"
echo -e "  Current State: ${YELLOW}${STATE}${NC}"

if [ "$STATE" == "stopped" ]; then
  echo -e "${GREEN}✅ Instance is already stopped! Compute is at $0 spend.${NC}"
else
  echo -e "\n${BLUE}🛑 Stopping EC2 Instance (${INSTANCE_ID})...${NC}"
  aws ec2 stop-instances --instance-ids "$INSTANCE_ID" >/dev/null
  echo -e "${GREEN}✅ Stop command issued. EC2 instance is powering down.${NC}"
fi
