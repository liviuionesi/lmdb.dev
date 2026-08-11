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
CYAN='\033[0;36m'
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
  echo -e "${GREEN}✅ Instance is already stopped! Compute is at \$0 spend.${NC}"
else
  echo -e "\n${BLUE}🛑 Stopping EC2 Instance (${INSTANCE_ID})...${NC}"
  aws ec2 stop-instances --instance-ids "$INSTANCE_ID" >/dev/null
  echo -e "${BLUE}⏳ Waiting for instance to enter stopped state...${NC}"
  aws ec2 wait instance-stopped --instance-ids "$INSTANCE_ID"
  echo -e "${GREEN}✅ EC2 instance is now fully Stopped.${NC}"
fi

echo -e "\n${CYAN}💰 AWS Cost Summary (approximate):${NC}"
echo -e "  ${GREEN}EC2 Compute (m7i-flex.large): \$0.00/hr  ← saved!${NC}"
echo -e "  ${YELLOW}EBS Root Volume (30 GB):       ~\$0.10/day (~\$3.00/month)${NC}"
echo -e "  ─────────────────────────────────────────"
echo -e "  ${YELLOW}Total idle cost:               ~\$0.10/day  (vs ~\$2.50/day running)${NC}"
echo -e "\n  📦 Your k3s database data is preserved on the EBS volume."
echo -e "  ▶️  To resume: ${CYAN}./infrastructure/scripts/start-aws.sh${NC} or ${CYAN}./gradlew startAws${NC}"
echo -e "  🌐 Or via GitHub Actions: trigger ${CYAN}cluster-stop.yml${NC} with cloud=aws, action=start"
echo -e ""
