#!/bin/bash

# Regenerates the accented/dialectal voice-command test fixtures ai-service's
# AccentedSpeechFixtureTest transcribes (#215, Story #200's AC2).
#
# These are SYNTHETIC recordings — espeak-ng text-to-speech through a set of
# regional/non-native English voices and varied German speaker profiles — not
# real human recordings. That is a deliberate, documented trade-off, not an
# oversight: this project's autonomous-run environments have no microphone
# and no way to source real accented speech from a consenting, identifiable-
# free speaker, so "self-recorded is acceptable" (the Task's own wording)
# is satisfied here by machine-recorded speech instead, which is
# 100% reproducible, carries no personal-data risk (Story #215 AC4), and
# stays inside ADR-004's $0/offline constraint the same way Vosk itself does.
# A human contributor with real accented speech to contribute can drop
# additional real recordings into the same directories and manifest entries
# below — the test doesn't care which kind produced a given file.
#
# KNOWN LIMITATION: espeak-ng/mbrola ship several genuinely regional/non-
# native English voices (Scottish, Lancaster, West Midlands, Caribbean, and a
# handful of foreign-accented-English voices), which is why the English set
# below is accent-diverse. For German, mbrola-de8 is a genuine dialectal
# voice (German-Bavarian); the other German entries (de1/de4/de7) are
# standard-German speaker profiles that differ in pitch/gender, not dialect —
# used here for acoustic diversity, not claimed as dialectal. Austrian/Swiss
# German voices aren't available in any offline package found. Real
# recordings covering those remain a gap for a human contributor to fill —
# see the fixture manifest's "accentLabel" field, which states plainly which
# of these two categories each file actually is.
#
# Requires: espeak-ng, mbrola + the de1/de4/de7/de8/ro1 mbrola voice
# packages, and sox (all offline, all apt-installable — see the package
# names in this script and `apt-get install <name>`).
#
# Usage: ./infrastructure/scripts/generate-accent-fixtures.sh
# Re-running regenerates every file from the FIXTURES table below — safe,
# deterministic, no state carried between runs.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES_DIR="$SCRIPT_DIR/../../backend/ai-service/src/test/resources/accent-fixtures"

GREEN='\033[0;32m'
NC='\033[0m'

command -v espeak-ng >/dev/null || {
  echo "espeak-ng not found — install it first (apt-get install espeak-ng)." >&2
  exit 1
}
command -v sox >/dev/null || {
  echo "sox not found — install it first (apt-get install sox)." >&2
  exit 1
}

mkdir -p "$FIXTURES_DIR/en" "$FIXTURES_DIR/de"

# Each row: output-file | espeak-ng voice | spoken phrase
# To add a new sample: append a row here, then add the matching entry to
# manifest.json (file, language, voice, accentLabel, phrase, expectedKeywords,
# expectedCommand, and expectedMode/expectedGenre where the command needs one).
FIXTURES=(
  "en/scottish-dark-mode.wav|en-gb-scotland|dark mode please"
  "en/lancaster-light-mode.wav|en-gb-x-gbclan|light mode please"
  "en/west-midlands-logout.wav|en-gb-x-gbcwmd|log me out"
  "en/caribbean-search.wav|en-029|search for inception"
  "en/romanian-accented-light-mode.wav|en-romanian|switch to light mode"
  "en/german-accented-genre.wav|en-german-2|show me action movies"
  "de/speaker1-dark-mode.wav|mb-de1|dunkler Modus bitte"
  "de/speaker4-light-mode.wav|mb-de4|heller Modus bitte"
  "de/speaker7-logout.wav|mb-de7|melde mich ab"
  "de/bavarian-genre.wav|mb-de8|zeig mir Actionfilme"
)

for row in "${FIXTURES[@]}"; do
  IFS='|' read -r outfile voice phrase <<<"$row"
  target="$FIXTURES_DIR/$outfile"
  raw="$(mktemp --suffix=.wav)"
  # 1. Synthesize at espeak-ng's native rate.
  espeak-ng -v "$voice" -s 150 "$phrase" -w "$raw"
  # 2. Resample to the mono 16kHz 16-bit PCM Vosk requires
  # (SpeechToTextService.TARGET_FORMAT) — matching this here means the test
  # exercises Vosk's accent handling itself, not AudioSystem's resampling.
  sox "$raw" -r 16000 -c 1 -b 16 -e signed-integer "$target"
  rm -f "$raw"
  echo -e "${GREEN}wrote${NC} $outfile  (voice=$voice, phrase=\"$phrase\")"
done

echo "Done. Regenerated ${#FIXTURES[@]} fixture files under $FIXTURES_DIR."
