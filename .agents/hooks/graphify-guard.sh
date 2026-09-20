#!/usr/bin/env bash
#
# Antigravity adapter for graphify's hook-guard.
#
# graphify's guard speaks Claude Code's hook format: it reads
# {"tool_name": ..., "tool_input": {...}} on stdin and, when a call looks like
# raw searching, prints {"hookSpecificOutput": {"additionalContext": ...}}.
# Antigravity sends {"toolCall": {"name": ..., "args": {...}}} and expects
# {"decision": "allow", "reason": ...}. This script translates both ways so
# the same guard, with the same reminder text, works in either tool.
#
# The guard is advisory: the answer is always "allow". Any problem (no
# graphify, no jq, bad input, timeout) also ends in a plain "allow", so an
# optional tool never blocks a tool call.
#
# graphify finds graphify-out/graph.json from the working directory, and
# Antigravity starts hooks in .agents/, so the guard runs from the repo root.
#
# Usage: graphify-guard.sh <search|read>   (Antigravity payload on stdin)

# Prints the verdict and exits. $1 is the reminder text, if graphify had one.
allow() {
  if [ -n "${1:-}" ]; then
    jq -cn --arg reason "$1" '{decision: "allow", reason: $reason}'
  else
    echo '{"decision":"allow"}'
  fi
  exit 0
}

mode="${1:-search}"
BIN="${GRAPHIFY_BIN:-$HOME/.local/bin/graphify}"
[ -x "$BIN" ] || allow

# 1. Translate the payload. Unknown tools produce no output, which means allow.
#    If the expected argument name is missing, fall back to the first string
#    argument, so a renamed field costs a reminder instead of causing a crash.
translated="$(jq -c '
  {run_command: "Bash", grep_search: "Grep", view_file: "Read",
   find_by_name: "Glob", list_dir: "Glob"} as $tools
  | {Bash: ["CommandLine", "command"], Grep: ["Query", "pattern"],
     Read: ["AbsolutePath", "file_path"], Glob: ["Pattern", "glob"]} as $args
  | (.toolCall.args // {}) as $a
  | $tools[.toolCall.name // ""] as $tool
  | select($tool != null)
  | $args[$tool] as $map
  | {tool_name: $tool,
     tool_input: (
       if $a[$map[0]] != null then {($map[1]): $a[$map[0]]}
       else ([$a[] | select(type == "string")][0]) as $first
            | if $first == null then {} else {($map[1]): $first} end
       end)}
' 2>/dev/null)" || allow
[ -n "$translated" ] || allow

# 2. Run the guard from the repo root.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
raw="$(cd "$ROOT" && printf '%s' "$translated" | timeout 10 "$BIN" hook-guard "$mode" 2>/dev/null)" || allow

# 3. Translate the reply. No output means the guard had no reminder.
context="$(printf '%s' "$raw" | jq -r '.hookSpecificOutput.additionalContext // empty' 2>/dev/null)" || allow
allow "$context"
