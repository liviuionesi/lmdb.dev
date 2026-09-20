# Accented and dialectal speech-command fixtures (#215)

Audio for `AccentedSpeechFixtureTest`. The test sends each file to the real
Vosk speech-to-text models and checks that the transcript contains the words
of the command. It covers Story #200 AC2.

## What is here

- `en/`, `de/`: one WAV per fixture, mono 16 kHz 16-bit PCM. That is the
  format `SpeechToTextService` requires.
- `manifest.json`: one entry per fixture. Fields:
  - `file`, `language`, `voice`, `accentLabel`, `phrase`: what the file is.
  - `expectedKeywords`: the words the transcript must contain to count as
    recognized.
  - `expectedCommand`, `expectedMode`, `expectedGenre`: what the phrase should
    classify to. The test does not assert these. They are there for a test
    that adds a live Ollama.

## Where the audio comes from

All 54 files are synthetic speech. Nobody was recorded, so there is no
personal data (#215 AC4). `infrastructure/scripts/generate-accent-fixtures.sh`
builds them.

| Set | Files | Voice | Accent |
|---|---|---|---|
| English, Piper ARCTIC | 42 (7 speakers, 6 phrases each) | Piper neural voice `en_US-arctic-medium` | The CMU ARCTIC project documents each speaker: `awb` Scottish, `jmk` Canadian, `ksp` `aup` `axb` `gka` `slp` Indian |
| German, Piper | 12 (2 speakers, 6 phrases each) | Piper neural voices `de_DE-thorsten-high` and `de_DE-kerstin-low` | Standard German. Neither dataset documents an accent |

Every ARCTIC speaker says all six English phrases, and both German speakers
say all six German phrases. No file was chosen after seeing its result.

Not used:

- A Romanian and a German voice reading English text, to imitate a Romanian or
  German accent. The files sounded poor and were deleted.
- `de_DE-mls-medium`. It is trained on audiobook recordings and sounded poor.
- `de_DE-pavoque-low`. Its licence (CC BY-NC-SA) forbids commercial use.
- `de_DE-karlsson-low` and `de_DE-ramona-low`. Their licence is only "See URL".

Piper has no Austrian, Swiss or Bavarian German voice, so no German fixture is
dialectal. Real recordings are the way to fill that gap.

Credit and licence: Piper is MIT licensed. The `arctic` voice is trained on
CMU ARCTIC (Carnegie Mellon University, http://festvox.org/cmu_arctic/).
Piper's model card for this voice gives its licence as "See LICENSE file",
and the voice repository has no such file. Check the terms before you reuse
these WAV files outside this repository.

The German voices come from these datasets:

- Thorsten-Voice, CC0: https://github.com/thorstenMueller/Thorsten-Voice
- Kerstin, CC0: https://github.com/rhasspy/dataset-voice-kerstin

## Measured accuracy

Measured on 2026-09-20 with the models from ADR-021
(`vosk-model-en-us-0.22-lgraph`, `vosk-model-small-de-0.15`). A file counts
as recognized when the transcript contains all of its keywords.

| Language | Recognized | Floor in the test |
|---|---|---|
| English | 33 of 42 (79%) | 65% |
| German | 5 of 12 (42%) | 33% |

English by speaker (6 phrases each): `awb` Scottish 6, `ksp` Indian 6, `gka`
Indian 5, `slp` Indian 5, `jmk` Canadian 4, `axb` Indian 4, `aup` Indian 3.

English misses by phrase (7 speakers each): "log me out" 4, "switch to light
mode" 3, "dark mode please" 1, "light mode please" 1, "search for inception" 0,
"show me action movies" 0. Typical errors: "log me out" heard as "log meal",
"switch to light mode" heard as "switch delight mall".

German by speaker (6 phrases each): Thorsten 3, Kerstin 2. German by phrase
(2 speakers each): "dunkler Modus bitte" 2, "melde mich ab" 2, "heller Modus
bitte" 1, "suche nach Das Boot" 0, "zeig mir Actionfilme" 0, "wechsle zum
hellen Modus" 0.

Both German speakers miss the same three phrases, so the cause is the small
German model and not one voice. The transcripts show three kinds of error:

- A different word: "das bot" for "Das Boot".
- The German spelling: "aktion filme" for "Actionfilme".
- A dropped ending: "hell modus" for "hellen Modus".

The keywords were not loosened. Checking word stems ("hell") would count the
last kind as recognized, but it would change what "recognized" means.

What this does not show:

- These are synthetic voices, not people. Real speakers with the same
  accents may do better or worse.
- German has two speakers and no dialect voice, so the German number is only
  a rough check.
- Only speech-to-text is measured. Intent parsing runs after it and may or
  may not recover a near miss.

The test asserts a floor and not a perfect score. It fails if Vosk, a model
or the audio conversion breaks, and it lists every miss with what Vosk heard.
Set the floors again if you rebuild the fixtures or change a model.

## Adding a sample

1. A real recording: convert it with
   `ffmpeg -i in.wav -ar 16000 -ac 1 -sample_fmt s16 out.wav`, put it in `en/`
   or `de/`, and add an entry to `manifest.json`. Only add a recording if the
   speaker agreed that it can be published.
2. A synthetic file: add a row to a table in
   `infrastructure/scripts/generate-accent-fixtures.sh` and run
   `./infrastructure/scripts/generate-accent-fixtures.sh en` (or `de`, or `all`).
   The run rebuilds every file of that language and rewrites that language's
   entries in `manifest.json`. The other language is not touched. A rebuilt
   file sounds a little different, because Piper adds random noise.
3. Run the test and update the floors if the measured accuracy changed.

`AccentFixtureManifestTest` always runs, including in CI. It fails if a
manifest entry has no WAV file, if a WAV file has no entry, or if there are
fewer than 6 English or 4 German fixtures.

## Running the test

It needs the real Vosk models. Run `infrastructure/scripts/download-vosk-model.sh`,
then set `VOSK_MODEL_PATH` and `VOSK_MODEL_PATH_DE` to the two directories it
created under `infrastructure/docker/models`:

```bash
VOSK_MODEL_PATH=$PWD/infrastructure/docker/models/vosk-model-en-us-0.22-lgraph \
VOSK_MODEL_PATH_DE=$PWD/infrastructure/docker/models/vosk-model-small-de-0.15 \
./gradlew :backend:ai-service:test --tests '*AccentedSpeechFixtureTest'
```

Without the models, the two Vosk tests are skipped, not failed.
