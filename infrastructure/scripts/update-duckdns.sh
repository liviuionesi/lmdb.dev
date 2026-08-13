#!/usr/bin/env bash
# Updates DuckDNS dynamic DNS record with the cluster's public IP address.
# Used by GitHub Actions deploy workflow and local Terraform deployment hooks.
set -euo pipefail

PUBLIC_IP="${1:-${PUBLIC_IP:-}}"
DOMAIN="${2:-${DUCKDNS_DOMAIN:-lmdb-api}}"
TOKEN="${3:-${DUCKDNS_TOKEN:-}}"

if [ -z "$PUBLIC_IP" ]; then
  echo "[update-duckdns] Error: No public IP address provided." >&2
  echo "Usage: $0 <public-ip> [domain] [token]" >&2
  exit 1
fi

if [ -z "$TOKEN" ]; then
  echo "[update-duckdns] Warning: DUCKDNS_TOKEN not set. Skipping DNS update." >&2
  echo "[update-duckdns] To auto-update DNS, configure DUCKDNS_TOKEN in repo secrets." >&2
  exit 0
fi

echo "[update-duckdns] Updating DuckDNS domain '$DOMAIN.duckdns.org' with IP '$PUBLIC_IP'..."

RESPONSE=$(curl -s -m 10 "https://www.duckdns.org/update?domains=${DOMAIN}&token=${TOKEN}&ip=${PUBLIC_IP}" || echo "FAILED")

if [ "$RESPONSE" = "OK" ]; then
  echo "[update-duckdns] Successfully updated '$DOMAIN.duckdns.org' -> $PUBLIC_IP"
else
  echo "[update-duckdns] Warning: DuckDNS API returned: $RESPONSE" >&2
fi
