# ADR-020: Natural-Language Query Parsing & Cross-Service Aggregation for Complex Movie Search

**Status:** Accepted
**Date:** 2026-08-28
**Deciders:** Project owner
**Related:** ADR-002 (database-per-service, forbids cross-service DB joins),
ADR-004 ($0 budget — Ollama only), ADR-012 (ai-service's Ollama/pgvector stack)
**Issue:** #198 (Task #201)

## Context

The frontend search bar is hard-wired to movie-service's
`GET /api/v1/movies/search?query=...` — literal title/keyword matching only.
Story #198 asks for something structurally different: a single search field
that accepts free text or dictated speech and resolves queries like *"movies
Tom Hanks directed between 2000 and 2010 that also starred Meg Ryan"* into
real, filtered results — person, role (acted/directed/produced), a date
range, and collaborator constraints, including negation ("didn't direct").

Two things this needs don't exist yet:

1. Something has to turn free text into a structured filter. ai-service
   already does exactly this kind of extraction for the chat assistant
   (`ChatAssistantService`, Ollama-backed), but nothing today parses intent
   out of an open-ended query string.
2. Once parsed, the filter has to be *executed*. ADR-002 forbids a
   cross-service database join, so something has to call the relevant
   services and merge results — and that something isn't obviously either
   service's job today.

A first draft of this ADR assumed the data both sides of the filter need
already exists in actor-service and movie-service. An independent review
pass caught that it doesn't, for two of the filter's own fields — see
"Prerequisite gaps" below. This revision decides those too, rather than
letting #202/#203 discover them mid-implementation.

## Decision

**ai-service owns both the extraction and the aggregation.**

1. **Extraction:** a new ai-service endpoint accepts the raw query, runs it
   through `PromptSanitizer` (the same defense `RecommendationService`
   already applies to its inputs — flattens control characters, caps
   length) and prompts the existing Ollama model to extract a structured
   filter. Chat's input handling is *not* the same mechanism — per
   `PromptSanitizer`'s own Javadoc, `ChatAssistantService` defends itself by
   passing history as typed `UserMessage`/`AssistantMessage` objects rather
   than sanitizing concatenated text; this endpoint is closer in shape to
   recommendations (free text in) than to chat. A query with no detectable
   structure (a plain title) is returned as an explicit plain-title
   fallback rather than an empty/degenerate filter, so the frontend never
   has to branch on query shape (#198 AC3) — ai-service itself routes a
   plain-title fallback on to movie-service's existing `/search`.

2. **Aggregation:** ai-service calls both downstream services and merges in
   memory — no database, no join. It already has `MovieCatalogClient` for
   movie-service; this decision adds an equivalent `ActorCatalogClient` for
   actor-service, following the same pattern (Eureka-resolved `lb://`,
   graceful empty-result degradation on failure, never fails the whole
   request because one dependency is down).

### Structured filter shape

Agreed contract between the extraction step and the aggregation step (#202
and #203), and reused as-is by Story #199's highlighting:

```json
{
  "personName": "string | null",
  "role": "ACTED | DIRECTED | PRODUCED | null",
  "yearFrom": "int | null",
  "yearTo": "int | null",
  "collaborators": ["string", "..."],
  "genre": "string | null",
  "negated": ["field names this query negates, e.g. \"role\""],
  "plainTitle": "string | null — set instead of the fields above when no structured intent is detected"
}
```

**Known limitation, stated rather than silently absent:** `negated` is
field-grained, not per-value — it can express "didn't direct" but not "with
X but not Y" inside a multi-name `collaborators` list. No current #198
acceptance criterion needs the finer grain; revisit if one does.

Story #199 additionally needs a token/span breakdown (`{text, category,
start, end}`, category one of `CONNECTOR | NEGATION | ENTITY`) alongside
this filter, so the frontend can highlight without its own NLP — that's
Task #207, layered on this same endpoint's response, not a separate parse.

### Prerequisite gaps (decided here, not deferred to #203)

The filter shape above commits to `role` (including `DIRECTED`/`PRODUCED`)
and a `yearFrom`/`yearTo` *range*. Neither is actually servable by the
clients #203 was scoped to call as-is:

- **actor-service has no crew data.** `TmdbPersonMovieCreditsResponse`
  (`backend/actor-service/.../client/dto/`) declares only a `cast` field;
  `ActorService.getFilmography()`/`getFilmographyPage()` maps only
  `.cast()`. TMDB's own `person_movie_credits` response includes a `crew`
  array (director/producer/writer, etc. via a `job`/`department` field) —
  it's simply never been mapped here, because nothing has needed it before
  now. **Decision:** actor-service adds a `TmdbCrewCredit` record (id,
  title, job, department, releaseDate, posterPath, voteAverage — same
  shape as `TmdbCastCredit`) alongside the existing `cast` field, and a
  filmography path that can filter by `job`/`department` the way the
  existing one filters by cast membership. Scoped as its own Task (#217),
  run before #203, not folded silently into it.
- **movie-service's discover endpoint takes one exact `year`, not a
  range.** `TmdbClient.discoverMovies` passes a single `Integer year`
  straight through to TMDB's discover `year` param (exact match). TMDB's
  discover API separately supports `primary_release_date.gte` /
  `primary_release_date.lte` for a range, which this client has never
  wired up. **Decision:** widen `discoverMovies` (client through
  controller) to accept optional `yearFrom`/`yearTo`, mapped to those two
  TMDB params when either is present, falling back to the existing exact
  `year` param when only a single year is given (backward compatible with
  every existing caller). Scoped as its own Task (#218), run before #203.

Both are small, additive, backward-compatible changes to existing raw-
passthrough clients (consistent with ADR-003) — not new services or new
architecture, which is why they're decided here rather than reopening this
ADR later. #203 depends on both landing first, in addition to #202.

## Options Considered

**ai-service aggregates (chosen)** — it already owns the only LLM
integration in the system and already has the `RestClient`-per-downstream-
service pattern (`MovieCatalogClient`) to extend. Keeps "understand free
text" and "act on what was understood" in one service, one round trip from
the frontend's perspective.

**API gateway aggregates** — rejected. `ARCHITECTURE.md` §3.3 scopes the
gateway to routing, auth, rate limiting, CORS, request/response transform,
and circuit breaking — no domain logic anywhere else in this system, and
giving it an LLM dependency and a merge algorithm would be new,
unprecedented scope for that layer.

**ai-service parses, movie-service aggregates** — a real middle option, not
just a strawman: movie-service already owns the catalog and could call
actor-service itself once ai-service hands it a structured filter.
Rejected because it would need movie-service to call *out* to actor-service
on ai-service's behalf, adding a network hop and a second service that has
to know the filter contract, for no benefit over ai-service just calling
actor-service directly itself.

**movie-service calls actor-service directly and does its own NL
parsing** — rejected. Movie-service has never held an LLM dependency;
duplicating the Ollama/prompt-sanitization integration there for this one
feature is the wrong owner of "understand free text," independent of the
aggregation question above.

**Frontend orchestrates multiple calls (call actor-service's data via
whatever surface, call movie-service, merge client-side)** — rejected.
Duplicates parsing/business logic in the browser, adds round trips, and
makes it impossible to apply `PromptSanitizer` consistently before anything
reaches an LLM. Also directly conflicts with #198 AC3 ("no query-shape
branching in the frontend").

## Consequences

- **Easier:** one new endpoint, one new client class, both following
  patterns already proven in this service (`RecommendationService`'s
  sanitize-then-prompt shape for extraction, `MovieCatalogClient` for the
  downstream-client shape).
- **Harder — real gotcha, already paid for once:** `RestClientConfig`
  deliberately does **not** declare a second ambient `@LoadBalanced
  RestClient.Builder` bean — doing so silently steals Spring Cloud
  LoadBalancer's interceptor away from every other unqualified
  `RestClient.Builder` injection in the app (Eureka's own registration
  client, Spring AI's Ollama client), breaking both. The new
  `ActorCatalogClient`'s `RestClient` bean must follow the exact same
  private-builder-plus-interceptor pattern `movieServiceRestClient()`
  already uses, not a naive `@LoadBalanced` builder. With two named
  `RestClient` beans now in the context, `ActorCatalogClient`'s
  constructor injection also needs an explicit `@Qualifier` (or a distinct
  parameter name Spring can match by), the same way `MovieCatalogClient`'s
  constructor currently gets away with implicit matching only because it's
  the sole `RestClient` bean today. #203 must not reintroduce either
  problem.
- **Harder:** actor-service and movie-service both need small, additive
  extensions before #203 can do anything useful with `role=DIRECTED`,
  `role=PRODUCED`, or a year range — see "Prerequisite gaps" above. Real,
  bounded work; not a blocker on this ADR being accepted, but #203 cannot
  start before it.
- **Harder:** worst-case request latency stacks — Ollama extraction, then
  one actor-service call *per named person* (the primary subject plus each
  collaborator, not a single call), then movie-service. #202/#203 should
  parallelize the per-person actor-service calls and short-circuit Ollama
  entirely for input that's trivially a plain title (no need to pay an LLM
  round trip to learn "Inception" has no operators). Not a blocker for this
  ADR, an implementation note for those Tasks.
- **Revisit:** if complex-query volume becomes significant, the same-query
  → same-filter mapping is cacheable (Redis, TTL). ai-service has Redis
  configured but nothing in it uses caching today — this would be a new
  pattern for this service, not an extension of an existing one.
- **Docs:** `ARCHITECTURE.md` §3.7's "Features (all live...)" list currently
  names four endpoints; once #202/#203 actually ship, that list gains a
  fifth. Deferred to #206 (that Task's docs pass) — not done here, since the
  endpoint doesn't exist yet and the list documents what's live, not what's
  planned.
- **Out of scope:** gRPC exposure for this endpoint. Recommendations/chat
  have REST+gRPC parity because a backend-to-backend caller might want them;
  this is a frontend-only search feature with no such caller today. Can be
  added later the same way if that changes.
