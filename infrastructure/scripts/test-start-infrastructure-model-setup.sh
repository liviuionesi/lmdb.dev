#!/bin/bash
#
# Tests for the model setup in start-infrastructure.sh (#200).
#
# Voice control needs two things the Compose files do not provide: the Vosk
# models on disk, and the Ollama models inside the Ollama container.
# start-infrastructure.sh must do both itself, and stop with an error when it
# cannot. A stack that starts without them looks healthy but every voice
# command fails.
#
# A fake `docker` goes first on PATH and records every call. Its answers come
# from environment variables set per test. `npm` is left off PATH so the
# frontend step is skipped.
#
# Run: infrastructure/scripts/test-start-infrastructure-model-setup.sh

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT="$SCRIPT_DIR/start-infrastructure.sh"
REPO_ROOT="$SCRIPT_DIR/../.."

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
CALLS="$TMP/calls.log"

# `docker exec lmdb-ollama ollama list|pull <model>` is answered from the
# environment; every other call succeeds silently.
cat > "$TMP/docker" <<'FAKE'
#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
if [ "$1" = "exec" ] && [ "$3" = "ollama" ]; then
  case "$4" in
    list) printf 'NAME ID SIZE MODIFIED\n%s' "$FAKE_OLLAMA_LIST"; exit 0 ;;
    pull) exit "${FAKE_PULL_EXIT:-0}" ;;
  esac
fi
exit 0
FAKE
chmod +x "$TMP/docker"

failures=0
OUTPUT=""
EXIT_CODE=0

# Runs start-infrastructure.sh once. Each test sets FAKE_* variables before
# calling this; they are passed through the environment.
run_script() {
  : > "$CALLS"
  OUTPUT="$(cd "$REPO_ROOT" && PATH="$TMP:/usr/bin:/bin" FAKE_DOCKER_CALLS="$CALLS" \
    bash "$SCRIPT" 2>&1)"
  EXIT_CODE=$?
}

pulls() { grep '^exec lmdb-ollama ollama pull ' "$CALLS" || true; }

# Prints PASS/FAIL for one check.
# $1 — test name, $2 — "true" or "false"
check() {
  if [ "$2" = "true" ]; then
    echo "PASS  $1"
  else
    echo "FAIL  $1"
    failures=$((failures + 1))
  fi
}

# Resets every knob to the "healthy, nothing installed" default.
reset() {
  export FAKE_OLLAMA_LIST=""
  export FAKE_PULL_EXIT=0
  export VOSK_DOWNLOAD_SCRIPT=/usr/bin/true
}

# Given an empty Ollama, both models are pulled. The image ships with none;
# without the pull, chat, voice parsing and semantic search fail.
reset
run_script
check "pulls both Ollama models when none are present" \
  "$([ "$EXIT_CODE" -eq 0 ] && [ "$(pulls)" = "exec lmdb-ollama ollama pull llama3.2
exec lmdb-ollama ollama pull nomic-embed-text" ] && echo true || echo false)"

# Given llama3.2 is installed, only the missing model is pulled. A restart
# must not need the network when nothing is missing.
reset
export FAKE_OLLAMA_LIST=$'llama3.2:latest abc 2.0 GB now\n'
run_script
check "skips a model that is already present" \
  "$([ "$EXIT_CODE" -eq 0 ] && [ "$(pulls)" = "exec lmdb-ollama ollama pull nomic-embed-text" ] && echo true || echo false)"

# Given both are installed, no pull runs.
reset
export FAKE_OLLAMA_LIST=$'llama3.2:latest abc 2.0 GB now\nnomic-embed-text:latest def 274 MB now\n'
run_script
check "pulls nothing when both models are present" \
  "$([ "$EXIT_CODE" -eq 0 ] && [ -z "$(pulls)" ] && echo true || echo false)"

# Given only llama3.2-vision is installed, llama3.2 is still pulled. A prefix
# match would take the vision model for the chat model and skip the pull.
reset
export FAKE_OLLAMA_LIST=$'llama3.2-vision:latest abc 8 GB now\n'
run_script
check "does not mistake llama3.2-vision for llama3.2" \
  "$(echo "$(pulls)" | grep -q 'ollama pull llama3.2$' && echo true || echo false)"

# Given a pull error, the script exits non-zero and names the model. A missing
# model must stop the start, not print a warning and carry on.
reset
export FAKE_PULL_EXIT=1
run_script
check "fails when an Ollama pull fails" \
  "$([ "$EXIT_CODE" -ne 0 ] && echo "$OUTPUT" | grep -q 'Could not pull Ollama model llama3.2' && echo true || echo false)"

# Given the Vosk download fails, no container starts. Voice control would
# return 503 for every command while all services show healthy.
reset
export VOSK_DOWNLOAD_SCRIPT=/usr/bin/false
run_script
check "stops before compose up when the Vosk models fail" \
  "$([ "$EXIT_CODE" -ne 0 ] && echo "$OUTPUT" | grep -q 'Vosk models are missing or failed verification' && ! grep -q ' up ' "$CALLS" && echo true || echo false)"

if [ "$failures" -ne 0 ]; then
  echo "$failures test(s) failed"
  exit 1
fi
echo "All tests passed"
