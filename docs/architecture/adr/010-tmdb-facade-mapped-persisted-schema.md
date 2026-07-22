# ADR-010: TMDB Facade Serves Mapped, Persisted Data — Supersedes ADR-003

**Status:** Accepted (supersedes ADR-003)
**Date:** 2026-07-22
**Deciders:** Project owner

## Context

ADR-003 chose byte-for-byte raw JSON passthrough for the TMDB-shaped facade
(`/movie/**`, `/person/**`, `/discover/**`, `/search/**`, `/genre/**`):
TMDB's response bodies were stored verbatim as opaque strings
(`tmdb_raw_documents`) and replayed unmodified, specifically to guarantee the
existing Filmpire React app (built against real TMDB) would work by changing
only its base URL.

Revisiting the goal: this project is a portfolio showcase. A service whose
job is "cache TMDB's bytes and hand them back" does not demonstrate backend
engineering — it demonstrates a reverse proxy. The product owner wants every
service (movie, actor, media, ai, user) to be backed by data the platform
actually owns, computes, and persists — queryable, extensible, and capable of
outliving TMDB as a data source — while *still* presenting the TMDB v3 API
surface (paths, snake_case field names, response envelopes) so the existing
Filmpire React app keeps working with no changes to its data-access code.

Two prior assumptions from ADR-003 turned out to not hold as strongly as
believed:
- "Typed mapping is lossy" — true only because the typed model was
  incomplete, not because typed mapping is inherently lossy. The gap
  (`original_title`, `belongs_to_collection`, `production_countries`,
  `video`, spoken-language ISO codes) is closeable with a handful of fields.
- "Every TMDB field addition silently breaks parity" — real, but tolerable:
  this is a fixed, well-known API (TMDB v3), not one under active schema
  churn. New fields can be added to the typed model as they're needed.

## Decision

The facade's persisted store changes from raw JSON strings to the **same
typed, queryable domain model** the native `/api/v1` API already uses
(`Movie`, `Genre`, `Video`, `Credits`, actor-service's `Actor`, etc.),
extended to close the field gaps above. Read-through/save-through semantics
are unchanged (Redis → MongoDB/PostgreSQL → TMDB, save-through on miss);
only the *storage shape* changes, from opaque string to typed entity.

On the way out, the facade converts the typed entity to TMDB's exact
response shape — same field names (snake_case via `@JsonProperty`, e.g.
`poster_path`), same envelope (`page`/`total_pages`/`total_results`/
`results`), same nesting (`append_to_response=videos,credits` merges the
persisted videos/credits onto the movie response). The wire contract with
the React app is unchanged; only the mechanism behind it changed from
"replay cached bytes" to "serialize an owned, persisted record."

The raw-passthrough machinery this replaces
(`TmdbFacadeService`/`TmdbDocument`/`TmdbDocumentRepository`/`TmdbRawClient`
in movie-service, `PersonFacadeService`/`TmdbPersonDocument`/
`TmdbPersonDocumentRepository` in actor-service) is removed — it is fully
superseded, not kept as a parallel path.

## Consequences

- **Easier:** one persisted model to reason about instead of two (raw +
  native) per ADR-003's own noted downside. Every request — facade or
  native — grows the same queryable dataset. Analytics, search, and future
  features (ratings, custom collections) can build directly on real fields
  instead of needing the Kafka event stream (ADR-006) as a workaround for
  opaque cached blobs.
- **Harder:** the typed model must be kept in step with the TMDB fields the
  frontend actually renders — an explicit, reviewed decision each time, not
  "whatever bytes TMDB sent." Detail-shaped fidelity (budget, runtime,
  tagline, etc.) is not available for movies only ever seen via list
  endpoints until their detail is fetched at least once — acceptable, same
  as any read-through cache with progressive enrichment.
- **List/discovery endpoints** (`discover/movie`, `search/movie`, category
  lists) still call TMDB live for ranking/search relevance — TMDB's search
  index is not being reimplemented — but every movie returned is
  upserted into MongoDB, so the catalog grows from real traffic and repeat
  detail lookups are served locally.
- Media-service (§3.8) and ai-service (§3.7) were never TMDB-shaped to begin
  with (purpose-built domain models per ARCHITECTURE.md) — this ADR does not
  change their design, only confirms the same "own the data" principle
  applies uniformly across all five services.
