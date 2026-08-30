#!/bin/bash

# Downloads the offline Vosk speech-to-text models ai-service needs for
# bilingual voice control (#68, #200, #212). Not committed to git — same
# reasoning as Ollama's model weights (infrastructure/docker/docker-compose.yml):
# a binary blob that's trivial to re-fetch doesn't belong in version control.
#
# Run this once before `docker-compose up` (or before running ai-service
# with bootRun). Re-running is a no-op for any model already present.
#
# Model choice per ADR-021 (docs/architecture/adr/021-vosk-bilingual-model-selection.md):
# English moved from vosk-model-small-en-us-0.15 to vosk-model-en-us-0.22-lgraph
# (lower WER, still small-model-class runtime memory); German is
# vosk-model-small-de-0.15, ai-service's first German model.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/../docker/models"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

# Vosk dlopens native code from these directories, so unverified content here
# runs inside ai-service. Keep each entry in step with the matching
# VOSK_MODEL_SHA256_* build arg in backend/ai-service/Dockerfile, which pins
# the same archives for the image build.
#
# KNOWN LIMITATION (both entries below): the checksums for these two archives
# are UNVERIFIED placeholders, not real published hashes. The autonomous-run
# environment that wrote this script has no network egress to
# alphacephei.com (same constraint ADR-021 already documents for its own WER
# research) or to any mirror checked, so the actual archive bytes were never
# fetched here to hash them for real. `sha256sum -c` below will therefore
# always fail — by design, a safe failure, not silent unverified execution —
# until someone with real network access downloads each archive, runs
# `sha256sum` on it, and replaces the placeholder with the real value here
# and in the Dockerfile. Tracked as a follow-up (see #212's issue comments).
declare -A MODEL_URL=(
  [en]="https://alphacephei.com/vosk/models/vosk-model-en-us-0.22-lgraph.zip"
  [de]="https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip"
)
declare -A MODEL_NAME=(
  [en]="vosk-model-en-us-0.22-lgraph"
  [de]="vosk-model-small-de-0.15"
)
declare -A MODEL_SHA256=(
  [en]="0000000000000000000000000000000000000000000000000000000000000000"
  [de]="0000000000000000000000000000000000000000000000000000000000000000"
)
declare -A MODEL_SIZE_HINT=(
  [en]="~128MB"
  [de]="~45MB"
)

download_model() {
  local lang="$1"
  local name="${MODEL_NAME[$lang]}"
  local url="${MODEL_URL[$lang]}"
  local sha256="${MODEL_SHA256[$lang]}"

  if [ -d "$MODELS_DIR/$name" ]; then
    echo -e "${GREEN}✓${NC} Vosk $lang model already present at $MODELS_DIR/$name"
    return 0
  fi

  echo "Downloading Vosk $lang speech-to-text model (${MODEL_SIZE_HINT[$lang]})..."
  mkdir -p "$MODELS_DIR"
  curl -fsSL -o "/tmp/${name}.zip" "$url"

  echo "Verifying checksum..."
  if ! echo "${sha256}  /tmp/${name}.zip" | sha256sum -c -; then
    echo -e "${RED}✗${NC} Checksum mismatch for $name — see this script's header comment:" \
      "the pinned hash is an unverified placeholder pending real network access."
    rm -f "/tmp/${name}.zip"
    return 1
  fi

  echo "Unzipping into $MODELS_DIR..."
  unzip -q "/tmp/${name}.zip" -d "$MODELS_DIR"
  rm "/tmp/${name}.zip"

  echo -e "${GREEN}✓${NC} Vosk $lang model ready at $MODELS_DIR/$name"
}

status=0
download_model en || status=1
download_model de || status=1

if [ "$status" -eq 0 ]; then
  echo -e "${YELLOW}!${NC} Restart ai-service (or docker-compose up -d ai-service) to pick these up."
else
  echo -e "${RED}✗${NC} One or more models failed to verify — see messages above." \
    "ai-service still starts without them; only speech-to-text for the affected" \
    "language(s) degrades to 503 until a verified model is in place."
fi
exit "$status"
