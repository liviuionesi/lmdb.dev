#!/usr/bin/env python3
"""Antigravity adapter for graphify's hook-guard.

graphify's guard is written against Claude Code's hook contract: it reads
``{"tool_name": ..., "tool_input": {...}}`` on stdin and, when the call looks
like raw searching, prints ``{"hookSpecificOutput": {"additionalContext": ...}}``.

Antigravity's PreToolUse contract is different on both ends — it sends
``{"toolCall": {"name": ..., "args": {...}}}`` and expects
``{"decision": "allow"|"deny"|"ask"|"force_ask", "reason": ...}``. This script
translates in both directions so the same guard, with the same reminder text,
works in either tool.

Two details the guard depends on, learned by driving it:

1. It only speaks up for search-shaped calls (``grep`` in a command). Anything
   else produces no output at all, which means "nothing to say".
2. It resolves ``graphify-out/graph.json`` relative to the working directory,
   and Antigravity runs hooks from the directory holding ``hooks.json``
   (``.agents/``). So it has to be invoked from the repository root.

Usage: graphify-guard.py <search|read>
"""

import json
import os
import subprocess
import sys

# Antigravity tool name -> the Claude Code tool name graphify's guard expects.
TOOL_MAP = {
    "run_command": "Bash",
    "grep_search": "Grep",
    "view_file": "Read",
    "find_by_name": "Glob",
    "list_dir": "Glob",
}

# Antigravity arg name -> Claude Code tool_input key, per mapped tool.
ARG_MAP = {
    "Bash": ("CommandLine", "command"),
    "Grep": ("Query", "pattern"),
    "Read": ("AbsolutePath", "file_path"),
    "Glob": ("Pattern", "glob"),
}


def allow(reason=None):
    """Emit an Antigravity PreToolUse verdict and exit.

    The guard is advisory: it never blocks, it only attaches context. So the
    decision is always "allow", with the reminder carried in "reason" when
    graphify had something to say.
    """
    out = {"decision": "allow"}
    if reason:
        out["reason"] = reason
    json.dump(out, sys.stdout)
    sys.stdout.write("\n")
    sys.exit(0)


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "search"
    graphify = os.environ.get(
        "GRAPHIFY_BIN", os.path.expanduser("~/.local/bin/graphify")
    )
    if not os.access(graphify, os.X_OK):
        allow()

    # 1. Read Antigravity's payload. A malformed or empty one is not a reason to
    #    interfere with the tool call.
    try:
        payload = json.load(sys.stdin)
    except Exception:
        allow()

    call = payload.get("toolCall") or {}
    name = call.get("name", "")
    args = call.get("args") or {}

    # 2. Translate into the shape graphify's guard parses.
    claude_tool = TOOL_MAP.get(name)
    if not claude_tool:
        allow()
    tool_input = {}
    src, dst = ARG_MAP.get(claude_tool, (None, None))
    if src and src in args:
        tool_input[dst] = args[src]
    else:
        # Fall back to the first string arg, so a renamed field degrades to a
        # missed reminder rather than a crash.
        for v in args.values():
            if isinstance(v, str):
                tool_input[dst or "command"] = v
                break
    stdin = json.dumps({"tool_name": claude_tool, "tool_input": tool_input})

    # 3. Run the guard from the repository root — it resolves graphify-out/
    #    relative to cwd, and Antigravity starts hooks in .agents/.
    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    repo_root = os.path.dirname(repo_root) if os.path.basename(repo_root) == ".agents" else repo_root
    try:
        res = subprocess.run(
            [graphify, "hook-guard", mode],
            input=stdin,
            capture_output=True,
            text=True,
            timeout=10,
            cwd=repo_root,
        )
    except Exception:
        allow()

    # 4. Translate the reply back. No output means the guard had no reminder.
    raw = (res.stdout or "").strip()
    if not raw:
        allow()
    try:
        ctx = json.loads(raw)["hookSpecificOutput"]["additionalContext"]
    except Exception:
        allow()
    allow(ctx)


if __name__ == "__main__":
    main()
