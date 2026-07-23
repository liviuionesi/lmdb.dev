# ADR-011: Self-Healing Read-Through on MongoDB Schema Drift

**Status:** Accepted
**Date:** 2026-07-23
**Deciders:** Project owner
**Related:** ADR-010 (mapped/persisted facade), ADR-002 (database-per-service)
**Issue:** #46

## Context

ADR-010 made the TMDB facade serve Filmpire's own persisted, typed catalog
rather than replaying TMDB's raw bytes. That decision is what makes the project
a real backend rather than a proxy — but it introduced a failure mode the raw
model did not have: **the persisted documents now have a schema, and that schema
can drift away from the code.**

movie-service persists to MongoDB, which is schemaless. Unlike the PostgreSQL
services — where Flyway owns the schema and `ddl-auto: validate` fails fast at
startup if the mapping and the tables disagree — nothing checks that a stored
document still matches the current `Movie` model. A document written by an older
model version simply sits there until something reads it, and then throws.

This is not hypothetical. It happened on 2026-07-23 (found while rebuilding the
stale container images, commit `a88676c`):

```
GET /movie/550 -> 500
org.springframework.core.convert.ConverterNotFoundException:
  No converter found capable of converting from type [java.lang.String]
  to type [com.filmpire.movie.model.SpokenLanguage]
```

The Fight Club document had been persisted before ADR-010, when
`spokenLanguages` was `List<String>`; the post-ADR-010 model declares
`List<SpokenLanguage>`. The severity comes from the fact that it is **not
self-limiting**: the read fails, so the save-through path never runs, so the
document is never rewritten, so the *next* request fails identically. One
poisoned document 500s forever until a human deletes it by hand — which is
exactly what had to be done.

Every future change to an embedded type reintroduces this. The catalog grows
from real traffic, so by the time a model changes there may be thousands of
documents in the old shape.

## Decision

**Treat a document that no longer maps to the current model as a cache miss.**

At the single read-through seam in `MovieService`:

1. Catch `ConversionException` / `MappingException` from the repository read.
2. Log a warning naming the movie and the drift.
3. Evict the document **by query via `MongoTemplate`**, never through a derived
   `deleteBy…` — Spring Data loads the entity before deleting it, which would
   rethrow the very error being recovered from.
4. Fall through to the existing TMDB fetch + save-through, which rewrites the
   document in the current shape.

The first request after a model change costs one TMDB call and repairs itself;
every subsequent request is served locally as normal. No manual cleanup, no
migration script, no 500.

**Only mapping/conversion failures are swallowed.** Infrastructure faults
(`DataAccessException` — Mongo unreachable, auth failure) propagate untouched.
This boundary is load-bearing: treating an outage as a universal cache miss
would turn "MongoDB is down" into "every request stampedes TMDB", blowing the
rate limit and replacing a clear failure with a confusing one.

## Options Considered

**Self-healing read-through (chosen)** — costs one TMDB call per drifted
document, recovers automatically, and needs no coordination with deploys. It
composes with the existing design instead of bolting a new mechanism on: the
read-through path already knows how to handle "not in the database."

**Schema-version field + migration on read** — the `Movie` model already carries
a `tmdbSyncVersion` field, so this looks like the intended path. It cannot work:
the version lives *inside* the document, and deserialization fails before any
version check can run. (`tmdbSyncVersion` is in fact vestigial — always written
as the constant `1`, never read. Left as-is; removing it is unrelated cleanup.)

**Backwards-compatible custom converters** — register a `String -> SpokenLanguage`
converter, etc. Rejected: it makes every model change permanently more expensive,
accumulating compatibility shims for shapes no one should still be storing, and
each one is a new place to get subtly wrong.

**Offline migration scripts per model change** — the classic answer, and correct
for data you cannot re-derive. Rejected here precisely because this data *is*
re-derivable: TMDB is the seed source (ADR-010), so a migration script would be
elaborate machinery to reconstruct what one HTTP call already returns.

**Wipe the collection on deploy** — simple, but throws away the whole catalog
(which grows from real traffic and is the point of ADR-010) to fix documents
that may be a tiny fraction of it, and turns every deploy into a TMDB stampede.

## Consequences

- Easier: model changes no longer require a migration step or manual cleanup;
  drifted documents heal on first access. The failure mode changes from
  "permanent 500" to "one extra upstream call."
- Cost: a drifted document is silently discarded rather than migrated. That is
  acceptable only because the catalog is re-derivable from TMDB — **this
  reasoning does not transfer to user-owned data.** Favorites, watchlists and
  accounts live in PostgreSQL under Flyway precisely because they cannot be
  re-fetched from anywhere, and must never adopt this pattern.
- Cost: a model change that affects many documents produces a burst of TMDB
  traffic as they heal. Bounded by the existing rate limiter and the
  single-flight lock, but worth knowing before a large model change ships.
- Scope: movie-service only. actor-service and user-service are on PostgreSQL
  with Flyway + `ddl-auto: validate`, so their equivalent drift is caught at
  startup rather than per-row; adding this there would be dead defensive code.
- Revisit: if a future model change is genuinely lossy in a way a re-fetch
  cannot restore (a field Filmpire computes and TMDB does not supply), this
  pattern silently discards it — that change needs a real migration instead.

## Verification

`TmdbFacadeIntegrationTest` plants the exact malformed document in real MongoDB
(Testcontainers) and asserts the endpoint returns 200, the document is replaced
rather than duplicated, and exactly one TMDB call occurs. A companion
precondition test asserts the planted shape genuinely fails to convert, so the
suite cannot quietly become vacuous if converters or field names change later.
`MovieServiceTest` covers the eviction, the `DataAccessException` boundary, and
the best-effort behavior when the cleanup delete itself fails.
