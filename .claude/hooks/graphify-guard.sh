#!/usr/bin/env sh
#
# Claude Code wrapper for graphify's hook-guard.
#
# graphify is installed per-user, outside the repository. The work contract
# says every autonomous run starts from a fresh checkout, where that path
# does not exist. Resolve the binary at call time and exit quietly when it
# is missing, so a tool call is never blocked by an absent optional tool.
#
# Mirrors .agents/hooks/graphify-guard.py, which already resolves
# GRAPHIFY_BIN and checks for executability.
#
# Usage: graphify-guard.sh <search|read>   (Claude Code hook payload on stdin)

# graphify's guard resolves graphify-out/graph.json relative to the working
# directory, so it must run from the repository root. Same constraint the
# Antigravity adapter documents.
ROOT="${CLAUDE_PROJECT_DIR:-$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)}"
cd "$ROOT" 2>/dev/null || exit 0

BIN="${GRAPHIFY_BIN:-$HOME/.local/bin/graphify}"
[ -x "$BIN" ] || BIN="$(command -v graphify 2>/dev/null)"
[ -n "$BIN" ] && [ -x "$BIN" ] || exit 0

exec "$BIN" hook-guard "$1"
