# ADR-008: Contract Testing with Spring Cloud Contract

**Status:** Accepted
**Date:** 2026-07-21
**Deciders:** Project owner

## Context

Service boundaries are only as good as their contracts. Today the critical
contract (facade ↔ TMDB shape) is pinned by recorded-fixture tests, but the
internal boundary (gateway ↔ movie-service, and soon actor-service) has no
executable contract — a breaking change in a service surfaces only in
end-to-end tests, late and expensively.

## Decision

Adopt **Spring Cloud Contract** for internal service boundaries:

- movie-service (and later actor-service) define contracts for the facade
  endpoints they serve; the build publishes stub jars.
- api-gateway consumes those stubs in its tests (StubRunner) — gateway
  routing tests run against generated stubs instead of hand-written mocks.
- The TMDB-side contract remains fixture-based (we cannot impose contracts
  on a third party; recorded real responses are the source of truth there —
  this split is deliberate).

## Options Considered

**Spring Cloud Contract (chosen)** — producer-side contracts in Groovy/YAML,
native Spring test integration, stub publication through the existing Gradle
build.

**Pact** — better for polyglot consumer-driven flows; overkill here where
both sides are Spring and the same repo.

**Fixtures only (status quo)** — sufficient for TMDB, but leaves internal
boundaries unprotected; rejected as the sole mechanism.

## Consequences

- Easier: breaking API changes fail the producer's build immediately;
  gateway tests stop depending on hand-maintained mocks.
- Harder: contract DSL learning curve; stub artifacts add build steps.
- Revisit: if a non-JVM consumer appears, re-evaluate Pact.
