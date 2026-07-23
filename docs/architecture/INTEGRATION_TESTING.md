# Integration Testing Strategy

**Issue:** #19 (Service Integration Testing)
**Status:** Complete — three layers: per-service suites (pre-existing),
gateway-boundary suite, and a live full-stack journey suite.

## Why this shape

Filmpire is database-per-service (ADR-002): each service owns its store and
services never share a database. There is therefore **no direct
service-to-service DB join to integration-test** — the couplings implied by the
original #19 checklist ("Movie-User favorites", "Movie-Actor cast") are not DB
joins:

- **Favorites/watchlist** live in user-service's PostgreSQL as bare TMDB movie
  IDs. The client hydrates movie details separately via the movie facade. There
  is no user-service → movie-service *DB* call.
- **Cast / filmography** come from TMDB through movie-service (`credits`,
  `discover?with_cast`) and actor-service (`person/{id}`); there is no
  actor↔movie DB join (see ADR-002 and the actor-service entity Javadoc).

But those journeys are real — they just live at the **API-composition layer**
the React app drives through the gateway, not in the database. So "integration"
is tested at three layers, each where its kind of interaction is real:

1. **Per-service boundaries** (already in place) — each service integration-
   tests its own edges with **WireMock** (a fake TMDB) and **Testcontainers**
   (real Mongo / PostgreSQL, `@ServiceConnection`). These prove read-through
   caching, save-through persistence, byte-fidelity, error passthrough, JWT
   auth flows, etc.
2. **The gateway boundary** (`GatewayIntegrationTest`) — the one place
   cross-service *policy* converges: routing, auth, rate limiting, circuit
   breaking, error propagation. Real gateway, WireMock downstreams.
3. **Full-stack journeys** (`FullStackJourneyIT`, new) — the actual cross-
   service *data-flow* journeys the checklist names, black-box against the
   whole running stack. This is where "Movie-User favorites" and "Movie-Actor
   cast" are genuinely exercised end to end.

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
| **TMDB facade/proxy routing** (`/movie`, `/person`, `/authentication`, `/account`) | **api-gateway** `GatewayIntegrationTest` | real gateway → WireMock; api_key stripped/injected, `/3` prefix, session_id forwarded |
| **Movie-User favorites/watchlist round-trip** | **api-gateway** `FullStackJourneyIT` | live stack: register → favorite → read-back → watchlist → remove → confirm gone |
| **Movie-Actor: details, filmography, movies-by-actor** | **api-gateway** `FullStackJourneyIT` | live stack: `/person/{id}`, `/person/{id}/movie_credits`, `/discover/movie?with_cast` |
| **Movie-with-cast (`append_to_response=credits`)** | **api-gateway** `FullStackJourneyIT` | live stack: `/movie/550?append_to_response=credits` |
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

Covers routing (movies, genres, actors), 401-without-token, valid-token routing
+ `X-User-*` propagation, 404 and 500 passthrough, breaker open→fallback
(asserting the downstream sees fewer calls than sent), rate-limit 429-after-
burst, CORS preflight, and — since #33 landed — the TMDB **facade** routes
(`/movie`, `/genre`, `/discover`, `/search`, `/person`) plus the
`/authentication` and `/account` **proxy** routes (api_key stripped/injected,
`/3` prefix restored, `session_id` forwarded untouched).

## Full-stack journey suite (`FullStackJourneyIT`)

A black-box client against the **whole running stack** (gateway + user/movie/
actor services + PostgreSQL/MongoDB/Redis via Eureka) — the layer where the
checklist's cross-service journeys are genuinely real. Uses `WebTestClient`
bound to `${FILMPIRE_GATEWAY_URL:http://localhost:8080}` and a `TestUserBuilder`
that registers a unique throwaway account per run (no cleanup, no collisions).

Four journeys:

- **Movie-User** — register → add favorite → read favorites (present) → add
  watchlist (present) → remove favorite → confirm gone. Proves the write path
  (gateway → JWT filter → user-service → PostgreSQL) and read-back are
  consistent and that removal actually takes effect.
- **Movie-Actor** — actor details (`/person/{id}`), filmography
  (`/person/{id}/movie_credits`), and movies-by-actor (`/discover/movie?with_cast`),
  spanning actor-service and movie-service.
- **Movie-with-cast** — `/movie/550?append_to_response=credits` returns the cast.
- **Auth boundary** — a protected user route rejects an unauthenticated caller.

**Skips cleanly when the stack is down.** A `@BeforeAll` probes the gateway
health endpoint; if unreachable, every test aborts via a JUnit *assumption*
(skipped, not failed), so `./gradlew build` stays green on a machine with no
stack running. When the stack is up, the four journeys run for real. This is the
one live-dependent suite; everything else is self-contained via Testcontainers.

The same journeys are also exercised through **newman** against the shared
Postman collection (`docs/api/Filmpire-API.postman_collection.json`),
which is the manual/CI acceptance gate — automated by the smoke-test script
below (#47).

## Automated live-stack smoke test (`smoke-test.sh`, #47)

`infrastructure/docker/smoke-test.sh` is the one-command way to answer "the
services are up — does the API actually work?" without touching Postman. It
waits for the gateway + movie/user/actor services to report healthy (bounded
timeout, not a fixed sleep), runs the newman collection, and exits with
newman's status (0 = all green) plus a JUnit report at `newman-report.xml`.

It is the single source of truth for local and CI runs:

```bash
# Test a stack you already started (day-to-day):
./infrastructure/docker/smoke-test.sh

# Bring the whole stack up, test it, tear it down (what CI does):
MANAGE_STACK=true TMDB_API_KEY=xxxx ./infrastructure/docker/smoke-test.sh
```

It detects the compose command (`docker compose` on CI runners,
`podman-compose` on the dev machine) and is parameterized by `GATEWAY_URL`,
`COLLECTION`, `HEALTH_TIMEOUT`, and `MANAGE_STACK`.

**CI:** `.github/workflows/e2e-smoke.yml` calls the script with
`MANAGE_STACK=true` on a nightly schedule and on manual `workflow_dispatch`
(not per-push — building six images + the full stack is heavy and needs the
TMDB key; nightly/manual keeps CI minutes and the $0 budget in check). It
uploads `newman-report.xml` as a run artifact.

> **One-time setup:** add a repository secret `TMDB_API_KEY`
> (Settings → Secrets and variables → Actions). The movie/actor/gateway
> containers need it to reach TMDB for un-cached data.

## Honest coverage notes vs. the #19 checklist

- **"Test service discovery"**: covered by discovery-service's own tests; the
  gateway suite deliberately replaces `lb://` with static URIs (no Eureka in a
  unit-speed integration test). `FullStackJourneyIT` does exercise real Eureka
  resolution implicitly, since it goes through the live `lb://` gateway.
- **"Coverage > 80%"**: JaCoCo runs per module; these suites raise gateway
  integration coverage of the routing/security/resilience wiring. It is not a
  single cross-repo coverage number — a database-per-service system has no one
  meaningful cross-module coverage figure.
- **Contract testing** (Spring Cloud Contract) is tracked separately as **#43**;
  **full-stack browser E2E** (Playwright driving the real React app) as **#38**.
  These suites intentionally do not duplicate those layers.

## Running

```bash
./gradlew :backend:api-gateway:test        # GatewayIntegrationTest + FullStackJourneyIT
./gradlew build                            # whole suite, all modules

# FullStackJourneyIT runs for real only when the stack is up; otherwise it skips:
podman-compose -f infrastructure/docker/docker-compose.yml up -d
FILMPIRE_GATEWAY_URL=http://localhost:8080 ./gradlew :backend:api-gateway:test

# newman acceptance gate against the running stack:
npx newman run docs/api/Filmpire-API.postman_collection.json
```

Requires a container runtime (Podman/Docker) for Testcontainers Redis and the
per-service Mongo/PostgreSQL containers. No dedicated `integrationTest` Gradle
task/source set was added — integration tests run in the normal `test` task
(one green bar for a solo repo). If Testcontainers reports "Could not find a
valid Docker environment" under Podman, the rootless socket has usually gone
stale — `systemctl --user restart podman.socket` recreates it.
