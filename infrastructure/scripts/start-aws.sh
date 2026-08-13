#!/usr/bin/env bash
# Starts an existing stopped AWS EC2 k3s instance to resume LMDB backend.
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
echo -e "${BLUE}  LMDB — Start AWS EC2 Backend Instance (k3s)   ${NC}"
echo -e "${BLUE}=====================================================${NC}"

for cmd in aws terraform; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo -e "${RED}❌ Required tool '$cmd' is not installed or not in PATH.${NC}" >&2
    exit 1
  fi
done

cd "$TF_DIR"
if [ ! -f "terraform.tfstate" ] && [ ! -d ".terraform" ]; then
  terraform init -backend-config=backend.hcl -input=false >/dev/null 2>&1 || true
fi

INSTANCE_ID="${AWS_INSTANCE_ID:-$(terraform output -raw instance_id 2>/dev/null || true)}"
if [ -z "$INSTANCE_ID" ]; then
  # Fallback query by tag
  INSTANCE_ID="$(aws ec2 describe-instances --filters "Name=tag:Name,Values=lmdb-k3s-demo" "Name=instance-state-name,Values=running,stopped" --query "Reservations[0].Instances[0].InstanceId" --output text 2>/dev/null || echo "")"
fi

if [ -z "$INSTANCE_ID" ] || [ "$INSTANCE_ID" == "None" ]; then
  echo -e "${RED}❌ Could not find EC2 instance for LMDB. Please apply Terraform first.${NC}" >&2
  exit 1
fi

echo -e "\n${BLUE}🔍 Checking EC2 instance state for ${INSTANCE_ID}...${NC}"
STATE="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query "Reservations[0].Instances[0].State.Name" --output text)"
echo -e "  Current State: ${YELLOW}${STATE}${NC}"

if [ "$STATE" == "running" ]; then
  echo -e "${GREEN}✅ Instance is already running!${NC}"
else
  echo -e "\n${BLUE}⚡ Starting EC2 Instance (${INSTANCE_ID})...${NC}"
  aws ec2 start-instances --instance-ids "$INSTANCE_ID" >/dev/null
  echo -e "${BLUE}⏳ Waiting for instance to enter running state...${NC}"
  aws ec2 wait instance-running --instance-ids "$INSTANCE_ID"
fi

PUBLIC_IP="$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --query "Reservations[0].Instances[0].PublicIpAddress" --output text)"
echo -e "${GREEN}✅ Node Public IP: ${PUBLIC_IP}${NC}"

if [ -f "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" ] && [ -n "${DUCKDNS_TOKEN:-}" ]; then
  "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" "$PUBLIC_IP" || true
fi

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  🎬 AWS Backend is Live!                            ${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e "  API Gateway:  ${CYAN}http://${PUBLIC_IP}:30080${NC}"
echo -e "  Cloud DNS:    ${CYAN}https://api.lmdb.dev${NC}"
echo -e "  Vercel App:   ${CYAN}https://lmdb.dev${NC}"
echo -e ""
echo -e "  To stop and save compute spend when not in use:"
echo -e "    ${CYAN}./infrastructure/scripts/stop-aws.sh${NC} or ${CYAN}./gradlew stopAws${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e ""
