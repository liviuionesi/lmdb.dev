#!/bin/bash

# Regenerates the accented/dialectal voice-command test fixtures that
# ai-service's AccentedSpeechFixtureTest transcribes (#215, Story #200 AC2).
#
# All fixtures are synthetic speech, not recordings of a person. This project
# has no microphone and no consenting speaker to record, and synthetic files
# carry no personal data (#215 AC4). A real recording can be added next to
# them the same way (see accent-fixtures/README.md).
#
# All voices are generated with Piper, an offline neural text-to-speech engine
# (MIT licence). Every fixture is one speaker saying one of the six command
# phrases of its language below. Nothing is picked by result.
#
#   English  The "arctic" voice holds the CMU ARCTIC speakers, and the ARCTIC
#            project documents each speaker's accent
#            (http://festvox.org/cmu_arctic/): awb is Scottish, jmk is
#            Canadian, and ksp, aup, axb, gka and slp are Indian English.
#
#   German   Thorsten-Voice (model "high") and Kerstin. Both datasets are CC0.
#            Neither documents an accent.
#
# Not used:
#   - A Romanian and a German voice reading English text, to imitate a
#     Romanian or German accent. The files sounded poor.
#   - de_DE-mls-medium. It is trained on audiobook recordings and sounds poor.
#   - de_DE-pavoque-low. Its licence (CC BY-NC-SA) forbids commercial use.
#   - de_DE-karlsson-low, de_DE-ramona-low. Their licence is only "See URL".
# Piper has no Austrian, Swiss or Bavarian German voice. German dialect
# coverage is still a gap for real recordings.
#
# Downloads are pinned by SHA-256 and cached under infrastructure/docker/
# models/piper (ignored by git).
#
# Usage: ./infrastructure/scripts/generate-accent-fixtures.sh [all|en|de]
#   The default is "all". Re-running rebuilds every file of the chosen
#   language from the tables below and rewrites that language's entries in
#   manifest.json. "en" and "de" leave the other language's files and entries
#   as they are.
#
# Piper adds random noise, so a rebuilt file sounds a little different and
# Vosk may transcribe it differently. The committed files are the fixtures.
# After a rebuild, run AccentedSpeechFixtureTest again and update the
# accuracy floors in it if the measured accuracy moved.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURES_DIR="$SCRIPT_DIR/../../backend/ai-service/src/test/resources/accent-fixtures"
CACHE_DIR="$SCRIPT_DIR/../docker/models/piper"
MODE="${1:-all}"

GREEN='\033[0;32m'
NC='\033[0m'

need() {
  command -v "$1" >/dev/null || {
    echo "$1 not found. Install it first ($2)." >&2
    exit 1
  }
}

PIPER_URL="https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz"
PIPER_SHA256="a50cb45f355b7af1f6d758c1b360717877ba0a398cc8cbe6d2a7a3a26e225992"

# Each row: model name | URL without extension | .onnx SHA-256 | .onnx.json SHA-256
MODELS=(
  "en_US-arctic-medium|https://huggingface.co/rhasspy/piper-voices/resolve/c10ece1aade47bb51c153c893d14e5bf8e5b7117/en/en_US/arctic/medium/en_US-arctic-medium|483303e294947a3ec2f910ea96093d876e1640f5772e9d89e511d6c82c667286|db2ca1a55db01cdd3ce28ae63037ac525133e9e00ca557430dec572643235efe"
  "de_DE-thorsten-high|https://huggingface.co/rhasspy/piper-voices/resolve/c10ece1aade47bb51c153c893d14e5bf8e5b7117/de/de_DE/thorsten/high/de_DE-thorsten-high|9df1c43c61149ef9b39e618e2b861fbe41e1fcea9390b2dac62e8761573ea4f1|6de734444e4c3f9e33b7ebe2746dbc19b71e85f613e79c65acf623200b99a76a"
  "de_DE-kerstin-low|https://huggingface.co/rhasspy/piper-voices/resolve/c10ece1aade47bb51c153c893d14e5bf8e5b7117/de/de_DE/kerstin/low/de_DE-kerstin-low|d352a7641892cebf2903859af94e9ba81a141110215fe3943bcda7f7da401b7a|56e708556b7b9b7a53c4f8957e021421e69f11a600962bba554cffbe72cf2d47"
)

# Each row: model name | file stem | Piper speaker id | speaker name | label.
# The label becomes accentLabel in manifest.json, with " (Piper neural voice)" added.
# The ids come from "speaker_id_map" in the model's .onnx.json, which the hashes above pin.
# A single-speaker model has no speaker id, so that field is empty.
ARCTIC_SPEAKERS=(
  "en_US-arctic-medium|arctic-awb|0|awb|Scottish English, CMU ARCTIC speaker awb"
  "en_US-arctic-medium|arctic-jmk|8|jmk|Canadian English, CMU ARCTIC speaker jmk"
  "en_US-arctic-medium|arctic-ksp|3|ksp|Indian English, CMU ARCTIC speaker ksp"
  "en_US-arctic-medium|arctic-aup|13|aup|Indian English, CMU ARCTIC speaker aup"
  "en_US-arctic-medium|arctic-axb|15|axb|Indian English, CMU ARCTIC speaker axb"
  "en_US-arctic-medium|arctic-gka|17|gka|Indian English, CMU ARCTIC speaker gka"
  "en_US-arctic-medium|arctic-slp|12|slp|Indian English, CMU ARCTIC speaker slp"
)

GERMAN_SPEAKERS=(
  "de_DE-thorsten-high|thorsten||thorsten|German (Germany), Thorsten-Voice neutral dataset speaker, no accent documented"
  "de_DE-kerstin-low|kerstin||kerstin|German (Germany), Kerstin voice dataset speaker, no accent documented"
)

# Each row: file slug | spoken phrase | keywords the transcript must contain (space separated)
#           | command | theme mode | genre. The last two are empty when the command has none.
PIPER_PHRASES=(
  "dark-mode|dark mode please|dark mode|CHANGE_MODE|DARK|"
  "light-mode|light mode please|light mode|CHANGE_MODE|LIGHT|"
  "log-out|log me out|log out|LOGOUT||"
  "search|search for inception|search inception|SEARCH||"
  "action-movies|show me action movies|action movies|CHOOSE_GENRE||Action"
  "switch-light-mode|switch to light mode|light mode|CHANGE_MODE|LIGHT|"
)

# The same six commands in German. The search phrase names a German film ("Das Boot")
# so a German speaker's phrase is tested, not an English title read with a German mouth.
GERMAN_PHRASES=(
  "dark-mode|dunkler Modus bitte|dunkler modus|CHANGE_MODE|DARK|"
  "light-mode|heller Modus bitte|heller modus|CHANGE_MODE|LIGHT|"
  "log-out|melde mich ab|melde ab|LOGOUT||"
  "search|suche nach Das Boot|suche boot|SEARCH||"
  "action-movies|zeig mir Actionfilme|zeig actionfilme|CHOOSE_GENRE||Action"
  "switch-light-mode|wechsle zum hellen Modus|hellen modus|CHANGE_MODE|LIGHT|"
)

# Downloads a file and checks its SHA-256. A file that is already there and matches is kept.
#   $1 url, $2 expected sha256, $3 destination
fetch_verified() {
  local url="$1" sha256="$2" dest="$3"
  if [ -f "$dest" ] && echo "$sha256  $dest" | sha256sum -c --status -; then
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  curl -fsSL -o "$dest.part" "$url"
  if ! echo "$sha256  $dest.part" | sha256sum -c --status -; then
    rm -f "$dest.part"
    echo "Checksum mismatch for $url. The file changed or the download is corrupt." >&2
    exit 1
  fi
  mv "$dest.part" "$dest"
}

# Fetches one model from the MODELS table, checked against its pinned hashes.
#   $1 model name
fetch_model() {
  local wanted="$1" row model_name url onnx_sha json_sha
  for row in "${MODELS[@]}"; do
    IFS='|' read -r model_name url onnx_sha json_sha <<<"$row"
    if [ "$model_name" = "$wanted" ]; then
      fetch_verified "$url.onnx" "$onnx_sha" "$CACHE_DIR/$model_name.onnx"
      fetch_verified "$url.onnx.json" "$json_sha" "$CACHE_DIR/$model_name.onnx.json"
      return 0
    fi
  done
  echo "Model $wanted is not in the MODELS table." >&2
  exit 1
}

# Says one phrase and writes it as a mono 16 kHz 16-bit WAV file, the format Vosk needs
# (SpeechToTextService.TARGET_FORMAT). Converting here means the test checks Vosk's own
# accent handling and not the resampling.
#   $1 model name, $2 speaker id (empty for a single-speaker model), $3 phrase, $4 output file
synthesize() {
  local model="$1" speaker_id="$2" phrase="$3" outfile="$4" raw
  local speaker_args=()
  [ -z "$speaker_id" ] || speaker_args=(--speaker "$speaker_id")
  raw="$(mktemp --suffix=.wav)"

  # 1. Synthesize at the model's native rate (16 or 22.05 kHz).
  echo "$phrase" | "$CACHE_DIR/piper/piper" --quiet \
    --model "$CACHE_DIR/$model.onnx" --config "$CACHE_DIR/$model.onnx.json" \
    "${speaker_args[@]}" --output_file "$raw" >/dev/null

  # 2. Convert to 16 kHz mono 16-bit PCM.
  mkdir -p "$(dirname "$FIXTURES_DIR/$outfile")"
  ffmpeg -v error -y -i "$raw" -ar 16000 -ac 1 -sample_fmt s16 -map_metadata -1 \
    "$FIXTURES_DIR/$outfile"
  rm -f "$raw"
}

# Appends one fixture's manifest entry to NEW_ENTRIES.
#   $1 file, $2 language, $3 voice, $4 label, $5 phrase, $6 keywords, $7 command,
#   $8 theme mode (may be empty), $9 genre (may be empty)
add_entry() {
  NEW_ENTRIES="$(jq --arg file "$1" --arg lang "$2" --arg voice "$3" --arg label "$4" \
    --arg phrase "$5" --arg keywords "$6" --arg command "$7" --arg mode "$8" --arg genre "$9" \
    '. + [{file: $file, language: $lang, voice: $voice, accentLabel: $label,
           phrase: $phrase, expectedKeywords: ($keywords | split(" ")),
           expectedCommand: $command}
          + (if $mode != "" then {expectedMode: $mode} else {} end)
          + (if $genre != "" then {expectedGenre: $genre} else {} end)]' <<<"$NEW_ENTRIES")"
}

# Every speaker in a table says every phrase in a table.
#   $1 language (en/de), $2 name of the speaker table, $3 name of the phrase table
generate_speakers() {
  local language="$1"
  local -n speaker_rows="$2"
  local -n phrase_rows="$3"
  local fetched="" speaker_row phrase_row

  for speaker_row in "${speaker_rows[@]}"; do
    IFS='|' read -r model stem speaker_id speaker label <<<"$speaker_row"
    [ "$model" = "$fetched" ] || { fetch_model "$model"; fetched="$model"; }
    for phrase_row in "${phrase_rows[@]}"; do
      IFS='|' read -r slug phrase keywords command mode genre <<<"$phrase_row"
      local outfile="$language/$stem-$slug.wav"
      synthesize "$model" "$speaker_id" "$phrase" "$outfile"
      add_entry "$outfile" "$language" "piper:$model:$speaker" "$label (Piper neural voice)" \
        "$phrase" "$keywords" "$command" "$mode" "$genre"
    done
  done
}

# Rebuilds the fixtures of the chosen languages and their manifest entries.
#   $1.. the languages to rebuild (en and/or de)
generate() {
  need curl "your package manager"
  need tar "your package manager"
  need ffmpeg "your package manager"
  need jq "your package manager"

  # 1. Fetch and unpack Piper, checked against the pinned hash.
  fetch_verified "$PIPER_URL" "$PIPER_SHA256" "$CACHE_DIR/piper.tar.gz"
  [ -x "$CACHE_DIR/piper/piper" ] || tar -xzf "$CACHE_DIR/piper.tar.gz" -C "$CACHE_DIR"

  # 2. Build the files and manifest entries of each language.
  NEW_ENTRIES="[]"
  local language
  for language in "$@"; do
    if [ "$language" = "en" ]; then
      generate_speakers en ARCTIC_SPEAKERS PIPER_PHRASES
    else
      generate_speakers de GERMAN_SPEAKERS GERMAN_PHRASES
    fi
  done

  # 3. Replace these languages' entries in the manifest and keep every other entry.
  local manifest="$FIXTURES_DIR/manifest.json" languages merged
  [ -f "$manifest" ] || echo "[]" >"$manifest"
  languages="$(jq -cn '$ARGS.positional' --args "$@")"
  merged="$(jq --argjson languages "$languages" --argjson new "$NEW_ENTRIES" \
    'map(select(.language as $l | ($languages | index($l)) | not)) + $new' "$manifest")"
  printf '%s\n' "$merged" >"$manifest"
  echo -e "${GREEN}wrote${NC} $(jq length <<<"$NEW_ENTRIES") fixtures ($*) and their manifest entries"
}

case "$MODE" in
  all) generate en de ;;
  en) generate en ;;
  de) generate de ;;
  *)
    echo "Usage: $0 [all|en|de]" >&2
    exit 1
    ;;
esac

echo "Done. Fixture files are under $FIXTURES_DIR."
