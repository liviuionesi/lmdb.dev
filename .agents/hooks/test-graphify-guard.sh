#!/bin/bash
#
# Tests for the Antigravity graphify-guard.sh adapter.
#
# A fake graphify stands in for the real one. It records the working
# directory and the stdin it was given, then prints a fixed reminder in
# Claude Code's format. That lets each test check both translations: what
# Antigravity sent becomes what graphify expects, and what graphify said
# becomes what Antigravity expects.
#
# Run: .agents/hooks/test-graphify-guard.sh

set -u

HOOK_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HOOK="$HOOK_DIR/graphify-guard.sh"
REPO_ROOT="$(cd "$HOOK_DIR/../.." && pwd)"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/graphify" <<'FAKE'
#!/usr/bin/env bash
pwd > "$FAKE_CWD_FILE"
cat > "$FAKE_STDIN_FILE"
echo "$@" > "$FAKE_ARGS_FILE"
[ "${FAKE_SILENT:-0}" = "1" ] && exit 0
echo '{"hookSpecificOutput":{"additionalContext":"use graphify query first"}}'
FAKE
chmod +x "$TMP/graphify"

export GRAPHIFY_BIN="$TMP/graphify"
export FAKE_CWD_FILE="$TMP/cwd" FAKE_STDIN_FILE="$TMP/stdin" FAKE_ARGS_FILE="$TMP/args"

failures=0
OUT=""

# Runs the hook. $1 is the mode, $2 the payload. Clears the fake's records first.
run_hook() {
  rm -f "$FAKE_CWD_FILE" "$FAKE_STDIN_FILE" "$FAKE_ARGS_FILE"
  OUT="$(printf '%s' "$2" | (cd "$HOOK_DIR/.." && "$HOOK" "$1"))"
}

# Prints PASS/FAIL for one check. $1 is the name, $2 is "true" or "false".
check() {
  if [ "$2" = "true" ]; then echo "PASS  $1"; else echo "FAIL  $1"; failures=$((failures + 1)); fi
}

# What graphify received, with the JSON put in one fixed layout.
sent() { jq -cS . < "$FAKE_STDIN_FILE" 2>/dev/null; }

# A shell search becomes a Bash call, and graphify's reminder comes back as the reason.
run_hook search '{"toolCall":{"name":"run_command","args":{"CommandLine":"grep -rn foo src"}}}'
check "run_command is sent to graphify as a Bash command" \
  "$([ "$(sent)" = '{"tool_input":{"command":"grep -rn foo src"},"tool_name":"Bash"}' ] && echo true || echo false)"
check "graphify's reminder comes back as the allow reason" \
  "$([ "$(echo "$OUT" | jq -cS .)" = '{"decision":"allow","reason":"use graphify query first"}' ] && echo true || echo false)"
check "the mode is passed on to graphify" \
  "$([ "$(cat "$FAKE_ARGS_FILE")" = "hook-guard search" ] && echo true || echo false)"
check "graphify runs from the repo root, not from .agents" \
  "$([ "$(cat "$FAKE_CWD_FILE")" = "$REPO_ROOT" ] && echo true || echo false)"

run_hook read '{"toolCall":{"name":"view_file","args":{"AbsolutePath":"/tmp/x.txt"}}}'
check "view_file is sent as a Read with file_path" \
  "$([ "$(sent)" = '{"tool_input":{"file_path":"/tmp/x.txt"},"tool_name":"Read"}' ] && echo true || echo false)"

run_hook read '{"toolCall":{"name":"list_dir","args":{"Pattern":"*.java"}}}'
check "list_dir is sent as a Glob with glob" \
  "$([ "$(sent)" = '{"tool_input":{"glob":"*.java"},"tool_name":"Glob"}' ] && echo true || echo false)"

run_hook search '{"toolCall":{"name":"grep_search","args":{"Query":"foo"}}}'
check "grep_search is sent as a Grep with pattern" \
  "$([ "$(sent)" = '{"tool_input":{"pattern":"foo"},"tool_name":"Grep"}' ] && echo true || echo false)"

# A renamed argument must still reach graphify through the first-string fallback.
run_hook search '{"toolCall":{"name":"run_command","args":{"cmd":"grep foo"}}}'
check "a renamed argument falls back to the first string" \
  "$([ "$(sent)" = '{"tool_input":{"command":"grep foo"},"tool_name":"Bash"}' ] && echo true || echo false)"

# Tools the guard does not cover are allowed without calling graphify at all.
run_hook search '{"toolCall":{"name":"unknown_tool","args":{}}}'
check "an unknown tool is allowed and graphify is not called" \
  "$([ "$OUT" = '{"decision":"allow"}' ] && [ ! -e "$FAKE_STDIN_FILE" ] && echo true || echo false)"

run_hook search 'not json'
check "a malformed payload is allowed and graphify is not called" \
  "$([ "$OUT" = '{"decision":"allow"}' ] && [ ! -e "$FAKE_STDIN_FILE" ] && echo true || echo false)"

# Silence from graphify means it had no reminder; that is a plain allow.
FAKE_SILENT=1 run_hook search '{"toolCall":{"name":"run_command","args":{"CommandLine":"ls"}}}'
check "no reminder from graphify gives a plain allow" \
  "$([ "$OUT" = '{"decision":"allow"}' ] && echo true || echo false)"

# An absent graphify must never block a tool call.
OUT="$(printf '%s' '{"toolCall":{"name":"run_command","args":{"CommandLine":"grep x"}}}' \
  | GRAPHIFY_BIN="$TMP/does-not-exist" "$HOOK" search)"
check "a missing graphify gives a plain allow" \
  "$([ "$OUT" = '{"decision":"allow"}' ] && echo true || echo false)"

if [ "$failures" -ne 0 ]; then echo "$failures test(s) failed"; exit 1; fi
echo "All tests passed"
