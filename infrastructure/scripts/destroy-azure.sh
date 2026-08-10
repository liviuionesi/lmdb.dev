#!/usr/bin/env bash
# Destroys Azure AKS infrastructure via Terraform to maintain $0 spend.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TF_DIR="$REPO_ROOT/infrastructure/terraform/azure"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${YELLOW}=====================================================${NC}"
echo -e "${YELLOW}  Filmpire — Destroy Azure AKS (Maintain $0 Spend)   ${NC}"
echo -e "${YELLOW}=====================================================${NC}"

if [ ! -d "$TF_DIR" ]; then
  echo -e "${RED}❌ Directory $TF_DIR not found.${NC}" >&2
  exit 1
fi

cd "$TF_DIR"
echo -e "\n${BLUE}🗑️ Running 'terraform destroy -auto-approve'...${NC}"
terraform destroy -auto-approve

echo -e "\n${GREEN}✅ Azure AKS infrastructure destroyed successfully.${NC}\n"
