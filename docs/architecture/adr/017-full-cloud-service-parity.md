# ADR-017: Cloud Overlays Run the Full Application Service Set, Not a Movie-Only Slice

**Status:** Accepted
**Date:** 2026-08-10
**Deciders:** Project owner
**Related:** ADR-004 ($0 budget), ADR-002 (database-per-service), §11.1, §11.5

## Context

`overlays/azure` and `overlays/aws` originally deployed only `api-gateway`
+ `movie-service` + MongoDB + Redis — the "core slice" — on the reasoning
that a free-tier node (~2vCPU/4GB) couldn't fit more. That reasoning was
never re-checked against real numbers once node sizing changed for other
reasons; it was carried forward as an assumption.

The actual constellation of services that runs locally — gateway,
movie/actor/user/ai-service, MongoDB, Postgres, Redis, Ollama — was never
re-evaluated for the cloud once the movie-only decision was made, so the
two demo targets (Azure, AWS) only ever showed a fraction of what the
system actually does: no login, no favorites/watchlist, no AI chat or
recommendations, no voice control.

## Decision

Deploy the full local-parity service set to both cloud overlays: `api-
gateway`, `movie-service`, `actor-service`, `user-service`, `ai-service`,
MongoDB, Postgres, Redis, and Ollama — everything that runs locally except
`media-service` (no Kubernetes manifests exist for it yet, and it would
also need an object-storage decision that hasn't been made for a cloud
target) and the observability-only services (`discovery-service`/Eureka,
`config-service`, Kafka, Zipkin — ADR-005 already keeps these out of every
K8s overlay on separate grounds unrelated to node size).

This required re-sizing both nodes, verified live rather than assumed:
Azure to `Standard_D4ls_v7` (4vCPU/8GB, within this subscription's Dlsv7
quota) and AWS to `m7i-flex.large` (8GiB/2vCPU, the largest Free
Tier-*eligible* type this account permits at all — see §11.1 for how that
constraint differs from Azure's).

## Options Considered

**Leave the movie-only slice, treat "full parity" as a local-only
concept.** Rejected: a demo environment that can only show a third of the
product's actual features undersells the system to exactly the audience
(a prospective client or employer evaluating this work) a live demo exists
to persuade. The node-size assumption that justified this was outdated the
moment it was checked.

**Deploy everything including Ollama's local LLM chat backend on the
existing node size.** Rejected on the numbers: Ollama alone requests 1Gi
and can use up to 4Gi under load — arithmetic that doesn't fit the
original ~4GB node regardless of how tightly everything else is trimmed.

**Skip Ollama specifically, keep the smaller node, deploy everything
else.** A real, considered option — it would have kept cost identical to
before. Rejected once actual pricing was checked live (Azure Retail
Pricing API, not assumed): the larger node upgrade costs `$0.234`/hr vs.
`$0.117`/hr, and even left running continuously for a full month that's
~$171 — comfortably inside a $200 free credit for many multiples of a
realistic demo session's actual runtime (§11.5). The cost difference
turned out to not be the constraint it was assumed to be; a full
feature-complete demo was worth the (still effectively free, given
ephemeral usage) extra node size.

## Consequences

- Both `overlays/azure` and `overlays/aws`'s `kustomization.yaml` grew
  substantially: new `resources:` entries, `configMapGenerator` merges to
  disable Eureka client registration on the newly-added services (neither
  overlay runs `discovery-service`), and a `MOVIE_SERVICE_BASE_URL`
  override on `ai-service` to bypass its default Eureka-resolved `lb://`
  scheme.
- Live-verified end-to-end on both clouds, not just "applied cleanly":
  movies, actors, register/login (real JWTs), and voice control's
  speech-to-text (baked-in Vosk model, #151) all confirmed working through
  the real gateway with real CORS.
- Two real first-boot bugs surfaced only once the full set was actually
  deployed and exercised under this sizing — MongoDB's memory limit being
  too tight for its own initialization, and movie-service's Kafka consumer
  wasting resources retrying a broker neither overlay has — documented and
  fixed as part of this change (§11.5).
- The $0 budget guarantee (ADR-004) is unchanged in kind: still enforced
  by the zero-spend budget-guard tripwire and the destroy-after-demo
  habit, not by a specific node size. This ADR is a sizing decision within
  that guarantee, not a change to it.
