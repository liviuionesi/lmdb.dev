# ADR-006: Kafka Event Bus for Save-Through Events & Analytics

**Status:** Accepted
**Date:** 2026-07-21
**Deciders:** Project owner

## Context

The architecture listed event-driven messaging as "future consideration."
For a tech-lead-grade backend, an event-driven seam is the most commonly
probed missing skill. It needs a *real* purpose, not a toy: the TMDB facade
(ADR-003) produces a natural event stream — every save-through is a signal
of what users actually request.

## Decision

Introduce **Apache Kafka** (local/compose and local-K8s profiles only — see
free-tier constraint below):

- Topic `tmdb.document.saved` — movie-service publishes an event on every
  facade save-through (key: canonical request key; payload: endpoint type,
  path, timestamp — NOT the full body).
- A consumer (initially inside movie-service as a separate consumer group;
  extractable later) maintains a **most-requested-movies analytics view**
  exposed via the native API (`/api/v1/analytics/most-requested`).

## Options Considered

### Option A: Kafka (chosen)
| Dimension | Assessment |
|-----------|------------|
| Interview relevance | Highest — consumer groups, partitions, offset semantics |
| Local footprint | ~1 GB (KRaft mode, single broker) — fine on the dev laptop |
| Free-tier cloud fit | Does NOT fit 1–2 GB nodes — local-only profile |

### Option B: RabbitMQ
Lighter (~150 MB), simpler; less industry cachet for the portfolio goal.

### Option C: Defer (status quo)
Zero cost, but leaves the most-probed gap open.

## Trade-off Analysis

Kafka's weight is acceptable because it is confined to local profiles; the
cloud core slice is unchanged (ADR-004 unaffected). Publishing metadata
instead of full bodies keeps events small and avoids duplicating the raw
document store.

## Consequences

- Easier: demonstrates pub/sub, consumer groups, at-least-once handling and
  idempotent consumption on a real data flow.
- Harder: one more stateful container locally; event publishing must never
  break the request path (fire-and-forget with failure logging).
- Revisit: extract the analytics consumer into its own service if a real
  second consumer appears; consider Kafka Streams for windowed trends.
