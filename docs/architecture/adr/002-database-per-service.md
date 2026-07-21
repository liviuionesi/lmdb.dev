# ADR-002: Per-Service Database Choices (MongoDB / PostgreSQL / Redis)

**Status:** Accepted
**Date:** 2025-11-14 (recorded retroactively 2026-07-21)
**Deciders:** Project owner

## Context

Each service owns its data store (database-per-service pattern). The choice
per service must fit the data shape and access pattern, not a single
company-wide default.

## Decision

| Service | Store | Reason |
|---------|-------|--------|
| Movie | MongoDB | Deeply nested TMDB documents (cast, crew, videos); the TMDB facade (ADR-003) stores raw JSON verbatim — document store is the natural fit |
| User | PostgreSQL | Account data wants ACID, uniqueness constraints, relational integrity (users ↔ favorites ↔ watchlist) |
| Actor | PostgreSQL | Many-to-many actor↔movie relations and join-shaped queries |
| AI | MongoDB | Conversation history and embedding vectors; flexible, write-heavy |
| Media | MongoDB + MinIO | File metadata (document) + S3-compatible object storage for bytes |
| Gateway | Redis | Rate-limiter token state — ephemeral, shared, fast |
| All | Redis | Short-TTL response cache in the read-through chain |

## Options Considered

**Single PostgreSQL for everything** — simpler ops, JSONB could hold TMDB
documents; rejected because it erases the polyglot-persistence learning goal
and couples services through one database (anti-pattern the project is meant
to demonstrate avoiding).

**Single MongoDB for everything** — rejected for user/actor data where
relational constraints genuinely fit better.

## Consequences

- Easier: each service's storage matches its access pattern; the facade's
  byte-fidelity requirement is trivially met by Mongo document storage.
- Harder: two database engines + object store to run locally and in K8s;
  cross-service queries impossible by design (must compose via APIs).
- Revisit: data lifecycle for the raw-document collection (TTL/growth
  policy) — tracked as facade hardening work.
