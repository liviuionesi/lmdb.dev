#!/usr/bin/env bash
# Wrapper to generate Filmpire project statistics and documentation
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHON_SCRIPT="$SCRIPT_DIR/generate-project-stats.py"

if ! command -v python3 >/dev/null 2>&1; then
  echo "❌ Error: Python 3 is required to generate project statistics." >&2
  exit 1
fi

python3 "$PYTHON_SCRIPT"
