#!/usr/bin/env bash
# Probes local, tunnel, and cloud infrastructure status and health.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${BLUE}=====================================================${NC}"
echo -e "${BLUE}  Filmpire — Infrastructure & Service Health Check  ${NC}"
echo -e "${BLUE}=====================================================${NC}"

# 1. Local Gateway
echo -e "\n${BLUE}1. Local Gateway (http://localhost:8080):${NC}"
if curl -s -m 2 http://localhost:8080/actuator/health >/dev/null 2>&1; then
  echo -e "   ${GREEN}🟢 ONLINE (HTTP 200)${NC}"
else
  echo -e "   ${YELLOW}⚪ OFFLINE / STANDBY${NC}"
fi

# 2. Cloudflare Tunnel
echo -e "\n${BLUE}2. Cloudflare Public HTTPS Tunnel:${NC}"
TUNNEL_FILE="$REPO_ROOT/infrastructure/tunnel-url.txt"
if [ -f "$TUNNEL_FILE" ]; then
  TUNNEL_URL="$(cat "$TUNNEL_FILE" | tr -d '[:space:]')"
  if [ -n "$TUNNEL_URL" ] && curl -s -m 4 "${TUNNEL_URL}/actuator/health" >/dev/null 2>&1; then
    echo -e "   ${GREEN}🟢 LIVE: ${TUNNEL_URL}${NC}"
  elif [ -n "$TUNNEL_URL" ]; then
    echo -e "   ${YELLOW}🟡 PUBLISHED (${TUNNEL_URL}) but unreachable / inactive${NC}"
  else
    echo -e "   ${YELLOW}⚪ No active tunnel URL${NC}"
  fi
else
  echo -e "   ${YELLOW}⚪ No active tunnel URL${NC}"
fi

# 3. Azure AKS
echo -e "\n${BLUE}3. Azure AKS (Kubernetes Nodes & Pods):${NC}"
if command -v kubectl >/dev/null 2>&1 && kubectl get nodes >/dev/null 2>&1; then
  echo -e "   Cluster Context: $(kubectl config current-context 2>/dev/null || echo 'unknown')"
  kubectl get nodes -o wide 2>/dev/null || true
  echo ""
  kubectl get pods -o wide 2>/dev/null || true
else
  echo -e "   ${YELLOW}⚪ No active Kubernetes cluster connection${NC}"
fi

echo -e "\n${BLUE}=====================================================${NC}\n"
