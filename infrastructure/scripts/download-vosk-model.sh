#!/bin/bash

# Downloads the offline Vosk speech-to-text model ai-service needs for
# voice control (#68). Not committed to git — same
# reasoning as Ollama's model weights (infrastructure/docker/docker-compose.yml):
# a binary blob that's trivial to re-fetch doesn't belong in version control.
#
# Run this once before `docker-compose up` (or before running ai-service
# with bootRun). Re-running is a no-op if the model is already present.

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODELS_DIR="$SCRIPT_DIR/../docker/models"
MODEL_NAME="vosk-model-small-en-us-0.15"
MODEL_URL="https://alphacephei.com/vosk/models/${MODEL_NAME}.zip"
# Vosk dlopens native code from this directory, so unverified content here runs
# inside ai-service. Keep in step with VOSK_MODEL_SHA256 in
# backend/ai-service/Dockerfile, which pins the same archive for the image build.
MODEL_SHA256="30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498"

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ -d "$MODELS_DIR/$MODEL_NAME" ]; then
    echo -e "${GREEN}✓${NC} Vosk model already present at $MODELS_DIR/$MODEL_NAME"
    exit 0
fi

echo "Downloading Vosk speech-to-text model (~40MB, small English)..."
mkdir -p "$MODELS_DIR"
curl -fsSL -o "/tmp/${MODEL_NAME}.zip" "$MODEL_URL"

echo "Verifying checksum..."
echo "${MODEL_SHA256}  /tmp/${MODEL_NAME}.zip" | sha256sum -c -

echo "Unzipping into $MODELS_DIR..."
unzip -q "/tmp/${MODEL_NAME}.zip" -d "$MODELS_DIR"
rm "/tmp/${MODEL_NAME}.zip"

echo -e "${GREEN}✓${NC} Vosk model ready at $MODELS_DIR/$MODEL_NAME"
echo -e "${YELLOW}!${NC} Restart ai-service (or docker-compose up -d ai-service) to pick it up."
