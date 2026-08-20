#!/usr/bin/env bash
# Starts or provisions Azure AKS cluster to run LMDB backend.
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
echo -e "${BLUE}  LMDB — Start Azure AKS Backend Cluster        ${NC}"
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

RG_NAME="${AZURE_RESOURCE_GROUP:-$(terraform output -raw resource_group_name 2>/dev/null || echo "")}"
CLUSTER_NAME="${AZURE_CLUSTER_NAME:-$(terraform output -raw cluster_name 2>/dev/null || echo "")}"

if [ -z "$RG_NAME" ] || [ -z "$CLUSTER_NAME" ]; then
  READ_AKS="$(az aks list --query "[0].[resourceGroup, name]" -o tsv 2>/dev/null || echo "")"
  if [ -n "$READ_AKS" ]; then
    RG_NAME="${RG_NAME:-$(echo "$READ_AKS" | sed -n '1p')}"
    CLUSTER_NAME="${CLUSTER_NAME:-$(echo "$READ_AKS" | sed -n '2p')}"
  else
    RG_NAME="${RG_NAME:-lmdb-demo}"
    CLUSTER_NAME="${CLUSTER_NAME:-lmdb-aks}"
  fi
fi

echo -e "\n${BLUE}🔍 Checking current AKS cluster power state for ${CLUSTER_NAME}...${NC}"
CURRENT_STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --query "powerState.code" -o tsv 2>/dev/null || echo "NotFound")"
echo -e "  Current Power State: ${YELLOW}${CURRENT_STATE}${NC}"

if [ "$CURRENT_STATE" == "Running" ]; then
  echo -e "${GREEN}✅ Cluster is already Running!${NC}"
elif [ "$CURRENT_STATE" == "Stopped" ]; then
  echo -e "\n${BLUE}⚡ Resuming stopped AKS Cluster (${CLUSTER_NAME})...${NC}"
  az aks start --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --no-wait
  
  echo -e "${BLUE}⏳ Waiting for cluster to reach Running state...${NC}"
  TIMEOUT=300
  ELAPSED=0
  while [ "$ELAPSED" -lt "$TIMEOUT" ]; do
    STATE="$(az aks show --resource-group "$RG_NAME" --name "$CLUSTER_NAME" \
      --query "powerState.code" -o tsv 2>/dev/null || echo "Unknown")"
    if [ "$STATE" == "Running" ]; then
      break
    fi
    sleep 10
    ELAPSED=$((ELAPSED + 10))
    echo -e "  ... still ${STATE} (${ELAPSED}s elapsed)"
  done
else
  echo -e "\n${BLUE}🚀 Provisioning AKS Cluster with Terraform...${NC}"
  terraform apply -auto-approve -input=false
fi

# Fetch credentials & deploy Kubernetes manifests
if command -v kubectl >/dev/null 2>&1; then
  echo -e "\n${BLUE}🔑 Refreshing AKS credentials & connecting to API server...${NC}"
  az aks get-credentials --resource-group "$RG_NAME" --name "$CLUSTER_NAME" --overwrite-existing

  for i in {1..30}; do
    if kubectl get nodes >/dev/null 2>&1; then
      break
    fi
    sleep 2
  done

  if [ -d "$REPO_ROOT/infrastructure/kubernetes/overlays/azure" ]; then
    echo -e "${BLUE}📦 Applying Kubernetes manifests (overlays/azure)...${NC}"
    kubectl apply -k "$REPO_ROOT/infrastructure/kubernetes/overlays/azure" || true
  fi

  # Wait for all 9 workloads to be Ready
  echo -e "\n${BLUE}⏳ Waiting for all pods to be Ready...${NC}"
  WORKLOADS=("deployment/api-gateway" "deployment/movie-service" "deployment/actor-service" "deployment/user-service" "deployment/ai-service")
  STATEFULSETS=("statefulset/postgres" "statefulset/mongodb" "statefulset/redis" "statefulset/ollama")
  for w in "${WORKLOADS[@]}"; do
    kubectl rollout status "$w" --timeout=300s 2>/dev/null && \
      echo -e "  ${GREEN}✅ $w ready${NC}" || \
      echo -e "  ${YELLOW}⚠️  $w not yet ready — check 'kubectl get pods'${NC}"
  done
  for s in "${STATEFULSETS[@]}"; do
    kubectl rollout status "$s" --timeout=300s 2>/dev/null && \
      echo -e "  ${GREEN}✅ $s ready${NC}" || \
      echo -e "  ${YELLOW}⚠️  $s not yet ready — check 'kubectl get pods'${NC}"
  done

  # Resolve live node IP
  echo -e "\n${BLUE}🌐 Resolving live Node IP...${NC}"
  NODE_IP="$(kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="ExternalIP")].address}' 2>/dev/null || true)"
  if [ -n "$NODE_IP" ]; then
    echo -e "  ${GREEN}✅ Node public IP: ${NODE_IP}${NC}"
    echo -e "  ${CYAN}🔗 Direct API Gateway: http://${NODE_IP}:30080/actuator/health${NC}"

    # Update DuckDNS so api.lmdb.dev points to the new IP
    if [ -f "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" ]; then
      if [ -n "${DUCKDNS_TOKEN:-}" ]; then
        echo -e "${BLUE}🦆 Updating DuckDNS → ${NODE_IP}...${NC}"
        "$REPO_ROOT/infrastructure/scripts/update-duckdns.sh" "$NODE_IP" && \
          echo -e "${GREEN}✅ DuckDNS updated: api.lmdb.dev → ${NODE_IP}${NC}" || \
          echo -e "${YELLOW}⚠️  DuckDNS update failed — check DUCKDNS_TOKEN${NC}"

        # Pods (Caddy included) already came back up above, as soon as the
        # cluster resumed — likely before DNS was updated. Caddy requests
        # its TLS cert the moment it starts, so a cold resume can win that
        # race and leave Caddy holding a failed cert it won't retry for a
        # while. Force one fresh attempt now that DNS is correct (#175).
        echo -e "${BLUE}🔁 Restarting caddy-tls to retry its TLS cert against current DNS...${NC}"
        kubectl rollout restart deployment/caddy-tls >/dev/null 2>&1 || true
      else
        echo -e "${YELLOW}⚠️  DUCKDNS_TOKEN not set — DuckDNS NOT updated. Export it and re-run:${NC}"
        echo -e "     ${CYAN}export DUCKDNS_TOKEN=your-token${NC}"
        echo -e "     ${CYAN}./infrastructure/scripts/update-duckdns.sh ${NODE_IP}${NC}"
      fi
    fi
  else
    echo -e "  ${YELLOW}⚠️  Could not resolve node external IP yet — try 'kubectl get nodes -o wide'${NC}"
  fi
fi

echo -e "\n${GREEN}=====================================================${NC}"
echo -e "${GREEN}  🎬 LMDB Azure Backend is Live!                 ${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e "  API Gateway:  ${CYAN}http://${NODE_IP:-<node-ip>}:30080${NC}"
echo -e "  Cloud DNS:    ${CYAN}https://api.lmdb.dev${NC}"
echo -e "  Vercel App:   ${CYAN}https://lmdb.dev${NC}"
echo -e ""
echo -e "  To start a HTTPS tunnel (bypasses DNS caching):"
echo -e "    ${CYAN}./infrastructure/scripts/start-tunnel.sh${NC}"
echo -e ""
echo -e "  To stop and save ~\$5/day when not in use:"
echo -e "    ${CYAN}./infrastructure/scripts/stop-azure.sh${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo -e ""
