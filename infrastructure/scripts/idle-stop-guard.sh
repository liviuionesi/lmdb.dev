#!/usr/bin/env bash
# Enables, disables, or checks the "Cluster Idle Auto-Stop" GitHub Actions
# workflow (.github/workflows/cluster-idle-stop.yml) — the scheduled job
# that stops whichever cloud is running once it's been idle for 1 hour.
# Toggling via `gh workflow` flips a GitHub-side flag, not the workflow
# file itself: fully reversible, no CI/CD edit, no commit needed.
set -euo pipefail

WORKFLOW="Cluster Idle Auto-Stop"
REPO="liviuionesi/lmdb.dev"
ACTION="${1:-status}"

if ! command -v gh >/dev/null 2>&1; then
  echo "❌ 'gh' CLI is not installed or not in PATH." >&2
  exit 1
fi

case "$ACTION" in
  disable)
    gh workflow disable "$WORKFLOW" --repo "$REPO"
    echo "🛑 Idle auto-stop disabled. Cloud compute will stay up regardless of activity until you re-enable it."
    ;;
  enable)
    gh workflow enable "$WORKFLOW" --repo "$REPO"
    echo "✅ Idle auto-stop re-enabled. Cloud compute will stop again after 1 hour of no traffic."
    ;;
  status)
    gh workflow list --repo "$REPO" --all | grep "$WORKFLOW" || echo "⚠️ Workflow not found."
    ;;
  *)
    echo "Usage: $0 <disable|enable|status>" >&2
    exit 1
    ;;
esac
