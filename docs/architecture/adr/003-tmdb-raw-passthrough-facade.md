# ADR-003: TMDB Facade Serves Raw Stored JSON, Not Re-Mapped DTOs

**Status:** ~~Accepted~~ **SUPERSEDED by [ADR-010](010-tmdb-facade-mapped-persisted-schema.md) (2026-07-22)**
**Date:** 2026-07-21
**Deciders:** Project owner

> **This decision is no longer in force. Do not implement against it.**
>
> The raw-passthrough model described below was replaced one day later by
> ADR-010: the facade now serves TMDB's exact *wire shape* from Filmpire's own
> mapped, typed, persisted catalog rather than replaying stored TMDB bytes. The
> reason was product-level, not technical — a byte-for-byte proxy does not
> demonstrate backend engineering, which is the point of the project.
>
> Everything this ADR describes has been deleted from the codebase:
> `TmdbDocument`, `TmdbDocumentRepository`, `TmdbRawClient`, `TmdbFacadeService`
> and the `tmdb_raw_documents` / `tmdb_person_documents` collections
> (commits `cecd614`, `2ce583a`).
>
> Kept for the record because the trade-off analysis below is still the clearest
> statement of what was given up by moving to a mapped model — notably the
> fidelity guarantee, which ADR-010 has to maintain by hand instead.

## Context

The product goal is a drop-in TMDB v3 clone: the existing Filmpire React app
must work by changing only its base URL. The app depends on TMDB's exact
response shapes (field names, nesting, `{page, results, total_pages,
total_results}` envelopes). Movie-service already had typed DTOs
(`TmdbMovieResponse` → `MovieDto`) for its native `/api/v1` API — but that
mapping is lossy (drops `genre_ids`, `original_title`, `video`, etc.).

## Decision

The facade stores and serves TMDB's response bodies **verbatim as JSON
strings** (`tmdb_raw_documents`, keyed by canonical path+params). No
parse/re-serialize round trip ever touches the payload.

## Options Considered

### Option A: Extend the typed DTO model to full TMDB shape
| Dimension | Assessment |
|-----------|------------|
| Effort | High — dozens of fields × many endpoints, ongoing drift risk |
| Fidelity | Fragile — every TMDB field addition silently breaks parity |

### Option B: Raw string passthrough (chosen)
| Dimension | Assessment |
|-----------|------------|
| Effort | Low — one storage type + generic forwarding |
| Fidelity | Guaranteed — the bytes served are the bytes TMDB sent |

## Trade-off Analysis

Raw storage gives up queryability of the cached payloads (you cannot
`find({vote_average: {$gt: 8}})` against opaque strings). That's acceptable:
the native `/api/v1` path keeps its typed model for queryable use-cases, and
the facade's job is fidelity, not queries. Freshness is handled by
per-endpoint staleness windows (lists 6h, details 30d) rather than parsing
content.

## Consequences

- Easier: byte-for-byte compatibility is structural, not maintained by hand;
  new TMDB fields flow through automatically.
- Harder: two parallel models exist (raw facade + typed native); analytics
  on cached data needs the event stream (ADR-006) instead of the documents.
- ~~Revisit: TTL index and growth policy for `tmdb_raw_documents`; single-flight
  locking for concurrent misses.~~ — moot: `tmdb_raw_documents` no longer
  exists. Single-flight locking was implemented anyway (`MovieService`'s
  `ReentrantLock`), and growth policy for the *mapped* catalog is tracked as
  issue #44.
