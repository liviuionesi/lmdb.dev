# Integration Testing Strategy

**Issue:** #19 (Service Integration Testing)
**Status:** Gateway-boundary suite implemented; per-service suites pre-existing.

## Why this shape

Filmpire is database-per-service (ADR-002): each service owns its store and
services never share a database. There is therefore **no direct
service-to-service DB join to integration-test** — the couplings implied by the
original #19 checklist ("Movie-User favorites", "Movie-Actor cast") do not
exist as live service calls:

- **Favorites/watchlist** live in user-service's PostgreSQL as bare TMDB movie
  IDs. The client hydrates movie details separately via the movie facade. There
  is no user-service → movie-service call to test.
- **Cast / filmography** come from TMDB through movie-service (`credits`,
  `discover?with_cast`) and actor-service (`person/{id}`); there is no
  actor↔movie DB join (see ADR-002 and the actor-service entity Javadoc).

So "integration" is tested at the two places where interaction is real:

1. **Per-service boundaries** (already in place) — each service integration-
   tests its own edges with **WireMock** (a fake TMDB) and **Testcontainers**
   (real Mongo / PostgreSQL, `@ServiceConnection`). These prove read-through
   caching, save-through persistence, byte-fidelity, error passthrough, JWT
   auth flows, etc.
2. **The gateway boundary** (new, #19) — the one place cross-service behavior
   converges: routing, auth, rate limiting, circuit breaking, error
   propagation.

## Layers & where each concern is tested

| Concern | Where | How |
|---|---|---|
| Read-through / save-through, byte-fidelity | movie-service, actor-service | WireMock (fake TMDB) + Testcontainers Mongo/PG |
| Auth: register/login/refresh/logout, favorites/watchlist | user-service | Testcontainers PostgreSQL, full journey test |
| **Routing to the correct service** | **api-gateway** `GatewayIntegrationTest` | real gateway → WireMock downstreams |
| **Public vs. authenticated exchanges** | **api-gateway** | Spring Security 401 vs. routed |
| **JWT propagation (`X-User-*`)** | **api-gateway** | assert headers reached downstream |
| **Downstream error passthrough (404/500)** | **api-gateway** | WireMock error stubs |
| **Circuit-breaker fallback** | **api-gateway** | statusCodes-tripped breaker → 503 fallback |
| **Rate limiting (429)** | **api-gateway** | Testcontainers Redis, burst exhausted |
| **CORS preflight** | **api-gateway** | OPTIONS from configured origin |
| Rate-limit algorithm details | api-gateway (unit) | `GlobalRateLimitFilterTest` |
| Service discovery (Eureka) | discovery-service | its own registration tests |

## Gateway integration suite (`GatewayIntegrationTest`)

Boots the **real** gateway (`@SpringBootTest(RANDOM_PORT)`, `WebTestClient`
against Netty) with:

- Routes repointed from `lb://service` to a single **WireMock** server on port
  9971 that stands in for every downstream (`application-gateway-it.yml`).
- **Eureka disabled**; the discovery route locator off.
- A **Testcontainers Redis** backing the `RequestRateLimiter`.
- A dedicated `cb-test` route whose circuit breaker treats HTTP 500 as a
  failure and is tuned (window 4 / min 4 calls) to open quickly.

Ten tests cover routing (movies, genres, actors), 401-without-token, valid-
token routing + `X-User-*` propagation, 404 and 500 passthrough, breaker
open→fallback (asserting the downstream sees fewer calls than sent), rate-limit
429-after-burst, and CORS preflight.

## Honest coverage notes vs. the #19 checklist

- **"Gateway routing tests"** for the TMDB **facade** paths (bare `/movie/...`,
  `/person/...`) are NOT here yet: those routes are added by **#33** (gateway
  TMDB facade routing + auth proxy), which is not implemented. This suite
  covers the existing `/api/v1/**` routes.
- **"Test service discovery"**: covered by discovery-service's own tests; the
  gateway suite deliberately replaces `lb://` with static URIs (no Eureka in a
  unit-speed integration test).
- **"Coverage > 80%"**: JaCoCo runs per module; this suite raises gateway
  integration coverage of the routing/security/resilience wiring. It is not a
  single cross-repo coverage number.
- **Contract testing** (Spring Cloud Contract) is tracked separately as **#43**;
  **full-stack E2E** (Playwright driving the real React app) as **#38**. This
  suite intentionally does not duplicate those layers.

## Running

```bash
./gradlew :backend:api-gateway:test        # includes GatewayIntegrationTest
./gradlew build                            # whole suite, all modules
```

Requires a container runtime (Podman/Docker) for Testcontainers Redis and the
per-service Mongo/PostgreSQL containers. No dedicated `integrationTest` Gradle
task/source set was added — integration tests run in the normal `test` task
(one green bar for a solo repo).
