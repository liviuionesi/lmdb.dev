# ADR-021: Vosk Model Selection for Bilingual (English + German) Speech-to-Text

**Status:** Accepted
**Date:** 2026-08-29
**Deciders:** Project owner (spike delegated; independent review pass pending
before Task #212 starts)
**Related:** ADR-004 ($0/offline budget — no cloud STT), ADR-012 (ai-service's
Ollama/pgvector stack, same service this model lives in)
**Issue:** #200 (Task #211)

## Context

Story #200 needs voice control to reliably understand both English and
German, including regional accents and dialects. Today `SpeechToTextService`
(`backend/ai-service/.../service/SpeechToTextService.java`) loads exactly one
hardcoded model — `vosk-model-small-en-us-0.15` (~40MB compressed) — lazily,
on first request. There is no German model at all. The model isn't committed
to git: it's fetched once by `infrastructure/scripts/download-vosk-model.sh`
for local dev, and pinned by a checksummed `curl` step in
`backend/ai-service/Dockerfile` for the image build.

Two things constrain this decision beyond simple accuracy:

- **ai-service's own resource ceiling.**
  `infrastructure/kubernetes/base/ai-service/deployment.yaml` requests
  384Mi / limits 768Mi of memory and limits 512Mi of ephemeral storage — sized
  around one small model, the JVM heap, and everything else ai-service
  already does (Ollama client, pgvector, chat/recommendation logic). Loading
  a second, larger model doubles the resident footprint; a model whose own
  RAM cost approaches or exceeds the 768Mi limit is disqualified outright,
  independent of how accurate it is.
- **This Task's own scope.** Per #211's acceptance criteria, this is
  research and a documented decision, not the integration — that's Task
  #212. No code changes are made here.

**Why this decision is grounded in published benchmarks, not a live audio
test.** #211's AC calls for a "quick accented-speech smoke test," but two
things rule that out in this run: this session has no network egress to the
domains that actually host the model archives (`alphacephei.com` is blocked
by the environment's egress proxy, and the candidate archives run from 40MB
to 4.4GB — too large to fetch through the text-oriented fetch tooling that
is reachable even where allowed); and the accented/dialectal test-recording
fixture this smoke test would run against doesn't exist yet — building it is
Task #215, scoped separately and not yet started. A literal audio smoke test
today would have no real fixture to run against regardless of network
access. This decision instead uses alphacep's own published word-error-rate
(WER) benchmarks (`alphacep/vosk-space`'s `models.md`), weighting the
noisier, more spontaneous test sets — TED-LIUM talks and call-center/podcast
audio — over the clean read-speech sets (LibriSpeech test-clean, Tuda-de
test), since spontaneous/varied-recording-condition speech is the closer
available proxy for accented/dialectal speech than studio-quality readings.
These figures come from a single fetch of `alphacep/vosk-space/models.md`
during this session — no further network access was available to cross-check
them against a second source or confirm the benchmark methodology/version —
so they're taken at face value as the vendor's self-reported numbers, not
independently reproduced.
**The literal accented-recording verification still has to happen** once
#215's fixture and #212's integration both exist — this ADR's choice is
provisional on that verification, not a replacement for it.

### Candidates compared

| Model | Size | WER (relevant sets) | Verdict |
|---|---|---|---|
| `vosk-model-small-en-us-0.15` (current) | 40MB | 9.85 (librispeech), 10.38 (tedlium) | Baseline; weakest accuracy of the English options |
| `vosk-model-en-us-0.22-lgraph` | 128MB | 7.82 (librispeech), 8.20 (tedlium) | **Chosen** — dynamic-graph design keeps runtime memory close to a small model while cutting WER meaningfully |
| `vosk-model-en-us-0.22` | 1.8GB | 5.69 (librispeech), 6.05 (tedlium), 29.78 (callcenter) | Best accuracy, but disqualified — wouldn't fit the 768Mi memory limit or 512Mi ephemeral-storage limit on its own |
| `vosk-model-en-us-0.42-gigaspeech` | 2.3GB | 5.64 (librispeech), 6.24 (tedlium), 30.17 (callcenter) | Same disqualification as above; podcast-tuned, not otherwise a better fit |
| `vosk-model-small-de-0.15` | 45MB | 13.75 (Tuda-de test), 30.67 (podcast) | **Chosen** — lowest WER of the German options that fit the resource budget |
| `vosk-model-small-de-zamia-0.3` | 49MB | 14.81 (Tuda-de test), 37.46 (podcast) | Rejected — worse WER than the alphacep small model on both sets, and off the `vosk-model-small-<lang>-<ver>` naming convention the download script/Dockerfile already assume |
| `vosk-model-de-0.21` | 1.9GB | 9.83 (Tuda-de test), 24.00 (podcast), 12.82 (cv-test) | Disqualified — same resource ceiling as the English full model |
| `vosk-model-de-tuda-0.6-900k` | 4.4GB | 9.48 (Tuda-de test), 25.82 (podcast) | Disqualified, more so — largest candidate by far |

There is no German model at the `-lgraph` (dynamic-graph, mid-size,
near-full accuracy) tier that the English choice uses — alphacep only ships
German as small/large/huge. That asymmetry is real, not an oversight in this
comparison: the German side simply has a bigger accuracy-vs-footprint gap to
close later than the English side does.

## Decision

**English:** replace `vosk-model-small-en-us-0.15` with
`vosk-model-en-us-0.22-lgraph` (128MB). It lowers WER on every published set
versus the current small model, including the noisier tedlium set (8.20 vs
10.38), while its dynamic-graph design is specifically built to keep runtime
memory near a small model's rather than a full model's — the only English
option besides the current one that plausibly fits the existing memory
ceiling.

**German:** adopt `vosk-model-small-de-0.15` (45MB) as the first German
model this service has ever loaded. It has the lowest published WER of the
two small-tier German candidates and matches the existing
`vosk-model-small-<lang>-<ver>` naming/versioning convention the download
script and Dockerfile already encode for English, so Task #212 extends an
existing pattern rather than inventing a new one.

Both full-size English and German models (1.8GB–4.4GB) are ruled out
outright: any one of them alone would leave little to no room under the
768Mi memory limit for the JVM heap, Ollama client, and pgvector work
ai-service already does — before a second language's model is even
considered.

## Options Considered

**Keep English-only, add nothing for German** — rejected; directly fails
Story #200's own goal.

**Full-size models for both languages (`en-us-0.22` + `de-0.21`), raise the
k8s memory/ephemeral-storage limits to match** — a real option, not a
strawman: it gets the best available accuracy on both languages. Rejected
for this decision because it turns a model-selection spike into an infra
change (deployment resource limits) with its own review, and because it
isn't necessary yet — accuracy hasn't been shown to be insufficient at the
smaller tier since #215's fixture doesn't exist. Worth revisiting if the
accent verification (once possible) shows the chosen tier isn't good enough;
see "Consequences."

**`vosk-model-en-us-0.22-lgraph` for English, full `vosk-model-de-0.21` for
German (mixed tiers)** — rejected as internally inconsistent for no clear
gain: German's accuracy gap between small and full is proportionally similar
to English's small-vs-full gap, so there's no principled reason to pay the
large-model resource cost for German while avoiding it for English.

**`vosk-model-small-de-zamia-0.3` instead of the alphacep small German
model** — rejected; strictly worse published WER on both available test
sets, for the same disk/RAM footprint.

## Consequences

- **Harder — needs real measurement, not assumption:** two resident models,
  even at the small/lgraph tier chosen here, meaningfully close the gap to
  the 768Mi memory limit. Task #212 must measure actual combined RSS with
  both models loaded (not just trust the disk-size numbers above) before
  assuming there's headroom, and either raise the deployment's memory limit
  modestly if measurement shows it's tight, or load exactly one model at a
  time — keyed by the active language switch, evicting the other — if a
  limit increase isn't wanted. Either way is Task #212's call, not decided
  here.
- **Harder — Dockerfile and download script both change:** Task #212 must
  update `backend/ai-service/Dockerfile`'s checksum-pinned `curl` step (the
  same `VOSK_MODEL_SHA256`-pattern, now for two archives) and
  `infrastructure/scripts/download-vosk-model.sh` for local dev, plus
  `VOSK_MODEL_PATH`-equivalent config for a second, German path.
- **Deferred, not skipped:** the literal accented/dialectal-recording
  verification this ADR's AC calls for is blocked on Task #215's fixture.
  This model choice is provisional on that verification passing once it can
  run — not a substitute for it.
- **Revisit:** if #215's fixture later shows the small/lgraph tier isn't
  accurate enough on real accented speech, the fallback is the full-size
  models considered and rejected above, paired with a deliberate, separate
  decision to raise ai-service's memory/ephemeral-storage limits — not
  something to slip into #212 quietly if that need arises.
