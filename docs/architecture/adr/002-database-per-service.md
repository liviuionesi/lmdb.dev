# ADR-002: Per-Service Database Choices (MongoDB / PostgreSQL / Redis)

**Status:** Accepted — the AI row amended by [ADR-012](012-ai-service-postgresql-pgvector.md) (2026-07-23)
**Date:** 2025-11-14 (recorded retroactively 2026-07-21)
**Deciders:** Project owner

## Context

Each service owns its data store (database-per-service pattern). The choice
per service must fit the data shape and access pattern, not a single
company-wide default.

## Decision

| Service | Store | Reason |
|---------|-------|--------|
| Movie | MongoDB | Deeply nested, irregular TMDB documents (cast, crew, videos, collections) map naturally to a document store; the catalog is re-derivable from TMDB, which is what makes ADR-011's self-healing safe here |
| User | PostgreSQL | Account data wants ACID, uniqueness constraints, relational integrity (users ↔ favorites ↔ watchlist) |
| Actor | PostgreSQL | Many-to-many actor↔movie relations and join-shaped queries |
| ~~AI~~ | ~~MongoDB~~ | ~~Conversation history and embedding vectors; flexible, write-heavy~~ — **superseded by [ADR-012](012-ai-service-postgresql-pgvector.md): PostgreSQL + pgvector.** Conversation history is user-owned and not re-derivable, so it must not sit on a store with neither schema validation nor a safe recovery path |
| Media | MongoDB + MinIO | File metadata (document) + S3-compatible object storage for bytes. Note (§3.8): TMDB media is never stored as bytes — only CDN references — so MinIO is reserved for hypothetical user uploads |
| Gateway | Redis | Rate-limiter token state — ephemeral, shared, fast |
| All | Redis | Short-TTL response cache in the read-through chain |

The organizing principle behind the table, made explicit by ADR-012:
**user-owned data lives in PostgreSQL under Flyway; re-derivable catalog data
lives in MongoDB, where ADR-011's self-healing is safe.**

## Options Considered

**Single PostgreSQL for everything** — simpler ops, JSONB could hold TMDB
documents; rejected because it erases the polyglot-persistence learning goal
and couples services through one database (anti-pattern the project is meant
to demonstrate avoiding).

**Single MongoDB for everything** — rejected for user/actor data where
relational constraints genuinely fit better.

## Consequences

- Easier: each service's storage matches its access pattern.
- Harder: two database engines + object store to run locally and in K8s;
  cross-service queries impossible by design (must compose via APIs).
- Revisit: growth/TTL policy for the persisted movie catalog — tracked as
  issue #44 (facade hardening).

## Amendments

- **2026-07-22 (ADR-010):** the original Movie rationale cited ADR-003's
  byte-verbatim storage as the reason a document store fit. ADR-010 superseded
  that model — the facade now persists a *mapped, typed* catalog — so the
  byte-fidelity argument no longer applies. MongoDB remains correct for
  movie-service, but on the strength of the nested-document shape alone.
- **2026-07-23 (ADR-012):** the AI row is superseded; ai-service moves to
  PostgreSQL + pgvector. See ADR-012 for the reasoning, which turns on the
  distinction between re-derivable and user-owned data that ADR-011 introduced.
