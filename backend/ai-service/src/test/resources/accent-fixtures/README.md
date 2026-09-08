# Accented/dialectal speech-command fixtures (#215)

Audio fixtures for `AccentedSpeechFixtureTest`, which proves the real Vosk
speech-to-text models correctly recognize voice commands spoken with
regional/non-native accents (English) or by different speaker profiles,
including one genuine dialect (German) — Story #200 AC2.

## What's here

- `en/`, `de/` — one WAV per fixture, mono 16kHz 16-bit PCM (the format
  `SpeechToTextService` requires).
- `manifest.json` — one entry per fixture: which file, which language, a
  human-readable `accentLabel`, the phrase spoken, the keywords the
  transcript must contain to count as recognized, and (for a future
  intent-parsing extension, not asserted by the current test — see
  `AccentedSpeechFixtureTest`'s Javadoc) which `VoiceCommandType`/mode/genre
  that phrase should classify to.

## Where the audio comes from

Synthetic — espeak-ng/mbrola text-to-speech, not real human recordings. This
environment has no microphone and no consenting speaker, so machine-recorded
speech is how the Task's own "self-recorded is acceptable" is satisfied here,
with the added benefit of zero personal-data risk (AC4) and full
reproducibility.

Not every fixture is equally "accented" — `manifest.json`'s `accentLabel`
says exactly what each one is:

- Genuinely regional/non-native voices: Scottish, Lancaster, and West
  Midlands English, Caribbean English, Romanian- and German-accented
  English, and German-Bavarian (a real dialect, not just a different
  speaker).
- Acoustic-diversity-only: the other German entries use different mbrola
  speaker profiles (pitch/gender), not different dialects — no offline
  Austrian/Swiss German voice was found. Real recordings covering those
  remain a gap.

## Adding a new sample

1. Real recording: drop a mono 16kHz 16-bit PCM WAV into `en/` or `de/`
   (resample with `sox in.wav -r 16000 -c 1 -b 16 -e signed-integer out.wav`
   if it isn't already).
   Synthetic: add a row to the `FIXTURES` table in
   `infrastructure/scripts/generate-accent-fixtures.sh` and re-run it.
2. Add the matching entry to `manifest.json` (see the fields above).
3. Nothing else changes — `AccentedSpeechFixtureTest` reads the manifest, not
   a hardcoded file list.

## Running the test

Needs the real Vosk models downloaded (`VOSK_MODEL_PATH`/`VOSK_MODEL_PATH_DE`
resolvable, same env vars `application.yml` uses — run
`infrastructure/scripts/download-vosk-model.sh` first). Without them, every
test in `AccentedSpeechFixtureTest` is skipped, not failed — see that class's
Javadoc.
