#!/bin/bash
# ==============================================================================
# Update DuckDNS domain with the target IP (or auto-detected public IP)
# ==============================================================================

set -euo pipefail

DUCKDNS_DOMAIN="${DUCKDNS_DOMAIN:-filmpire-api}"
DUCKDNS_TOKEN="${DUCKDNS_TOKEN:-30f79514-ac7b-4cb9-b979-6520c69aec5c}"
TARGET_IP="${1:-}"

if [ -z "$DUCKDNS_TOKEN" ]; then
  echo "Error: DUCKDNS_TOKEN is not set." >&2
  exit 1
fi

if [ -n "$TARGET_IP" ]; then
  echo "Updating DuckDNS domain '$DUCKDNS_DOMAIN' to IP '$TARGET_IP'..."
  RESPONSE=$(curl -s "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=${TARGET_IP}")
else
  echo "Updating DuckDNS domain '$DUCKDNS_DOMAIN' with auto-detected public IP..."
  RESPONSE=$(curl -s "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}")
fi

if [ "$RESPONSE" = "OK" ]; then
  echo "✓ Successfully updated ${DUCKDNS_DOMAIN}.duckdns.org"
else
  echo "✗ DuckDNS update failed with response: $RESPONSE" >&2
  exit 1
fi
