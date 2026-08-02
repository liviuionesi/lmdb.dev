#!/bin/bash
# Runs one backend module locally via `gradlew bootRun` for hot-reload
# iteration, instead of rebuilding its container image.
#
# Usage: infrastructure/scripts/dev-service.sh <module-name>
# Example: infrastructure/scripts/dev-service.sh movie-service

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR/../.."

MODULE="$1"
if [ -z "$MODULE" ]; then
    echo "Usage: $0 <module-name>"
    echo "  e.g.: $0 movie-service"
    echo ""
    echo "Available modules:"
    grep -oP "backend:\K[a-z-]+" "$REPO_ROOT/settings.gradle" | grep -v shared-library | sed 's/^/  - /'
    exit 1
fi

source "$SCRIPT_DIR/env.sh"

cd "$REPO_ROOT"

echo "Starting backend:${MODULE} (TMDB_API_KEY: ${TMDB_API_KEY:0:6}***, REDIS_PASSWORD set)..."
echo ""

./gradlew ":backend:${MODULE}:bootRun"
