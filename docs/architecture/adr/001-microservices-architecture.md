# ADR-001: Microservices Architecture with Spring Cloud

**Status:** Accepted
**Date:** 2025-11-14 (recorded retroactively 2026-07-21)
**Deciders:** Project owner

## Context

Filmpire needs a backend that clones the TMDB v3 API for an existing React
app. Functionally, a single Spring Boot monolith would serve the product goal
with a fraction of the operational cost. However, this project has an
explicit second goal: to learn and demonstrate end-to-end ownership of a
complex, production-shaped backend (service decomposition, service
discovery, centralized config, gateway patterns, independent deployability).

## Decision

Build 8 services on Spring Cloud (Eureka discovery, Config Server, Cloud
Gateway) rather than a monolith.

## Options Considered

### Option A: Modular monolith
| Dimension | Assessment |
|-----------|------------|
| Complexity | Low |
| Operational cost | Low (one deployable) |
| Learning/demonstration value | Low — hides the distributed-systems problems |

### Option B: Microservices (chosen)
| Dimension | Assessment |
|-----------|------------|
| Complexity | High |
| Operational cost | High (8 deployables, discovery, config) |
| Learning/demonstration value | High — every cross-cutting concern becomes real |

## Trade-off Analysis

**We knowingly over-decompose.** By industry standards, a movie-catalog
domain of this size does not justify 8 services — "monolith first" would be
the professionally correct default. The decomposition is justified here
*only* by the learning/portfolio goal, and this ADR exists to show that the
trade-off was made consciously, not by cargo-culting the Netflix stack.

## Consequences

- Easier: demonstrating gateway routing, per-service data stores, independent
  scaling stories, resilience patterns between services.
- Harder: local resource footprint, deployment complexity, debugging across
  service boundaries (mitigated by ADR-007 tracing).
- Revisit: if this became a real product with a team, collapse user/actor/ai
  services into the movie service until scale demands otherwise.
