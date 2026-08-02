#!/bin/bash
# Loads infrastructure/docker/.env (TMDB_API_KEY, REDIS_PASSWORD, etc.) into
# the calling script's environment and warns if TMDB_API_KEY is missing.
#
# Usage: source "$(dirname "${BASH_SOURCE[0]}")/env.sh"   (or an absolute path)

ENV_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FILMPIRE_ENV_FILE="$ENV_SCRIPT_DIR/../docker/.env"

if [ -f "$FILMPIRE_ENV_FILE" ]; then
    set -a
    source "$FILMPIRE_ENV_FILE"
    set +a
else
    echo "⚠️  No infrastructure/docker/.env found — using defaults. Copy" >&2
    echo "   infrastructure/docker/.env.example to infrastructure/docker/.env to customize." >&2
fi

if [ -z "$TMDB_API_KEY" ] || [ "$TMDB_API_KEY" = "your_tmdb_api_key_here" ]; then
    echo "⚠️  TMDB_API_KEY is not set. Movie/actor catalog population and the" >&2
    echo "   gateway's TMDB auth proxy won't work without it." >&2
fi

export TMDB_API_KEY
export REDIS_PASSWORD="${REDIS_PASSWORD:-redis123}"
