# ADR-023: Multi-Step Natural-Language Search

**Status:** Accepted
**Date:** 2026-09-20
**Deciders:** Project owner
**Related:** ADR-020 (the first search pipeline, extended here), ADR-002 (no cross-service joins),
ADR-004 ($0 budget)
**Issue:** #333 (Tasks #334, #335)

## Context

Search returned fewer results the more a query said, and often none. Four causes were found by
running real queries against the stack:

1. A year range kept only the movies that were among the 200 most popular of those years. Tom Hanks
   has 28 movies from the 1990s in the catalog. The search returned 5.
2. Only people already saved in the local catalog were found.
3. The 3B model (llama3.2) invented values. For "James Bond franchise after year 2000" it returned
   Daniel Craig, ACTED, 2000 to 2015 and three made-up co-stars.
4. Franchises, sorting, "top 10", "last 20 years" and awards were not supported at all.

## Decision

**A query is handled in five steps.** `QueryAggregationService` runs them.

1. **Read.** The model reads the people, franchise, genre and keywords. Code reads everything that
   has fixed phrasing, so it cannot be wrong: relative years ("the last 20 years"), decades, sort
   order, "top N", a minimum rating, and an Oscar category. `QueryModifiers` does this.
2. **Ground.** `FilterGrounding` removes every model value the query text does not support. A person
   must appear in the query. A year must be in the query. A role, a negation and an award word must
   have their cue words. This works the same for any model.
3. **Look up.** Each named thing is looked up in its own source. A movie must be in every set.
   - A person: actor-service. It fetches from TMDB and saves what it finds.
   - A franchise: movie-service, through the new collection endpoints (TMDB "collections").
   - An Oscar category: Wikidata (see below).
   - With no name: keyword title searches and movie-service discover.
4. **Filter.** The year check reads each movie's own release date. A genre on a person's movies and
   keywords are checked by the model, one batch of 40 movies at a time.
5. **Arrange.** Movies below a minimum rating are dropped, the rest are sorted, and the first N are
   kept. Sorting by revenue fetches the movie details, because lists do not carry revenue.

**If nothing is left, the weakest criterion is dropped and the search runs again.** The order is
keywords, minimum rating, genre, collaborators, years. The response names what was dropped. The
person, franchise and award are never dropped.

**Awards come from Wikidata.** TMDB has no award data. Wikidata's public query service lists each
winning film with its TMDB id. The answer is kept for 24 hours. If Wikidata cannot be reached, the
last answer is used, or none. An award lookup never fails a search. It is free and needs no key, so
it fits ADR-004.

A query Wikidata has not answered lately can take up to a minute. So the service asks for every
category in the background at startup, in up to three passes. Only one question per category is in
flight at a time; a search that arrives meanwhile waits for that answer.

**The chat model depends on the machine.** A GPU with 5.5 GB of memory or more runs
`qwen2.5:7b-instruct`. Anything else runs `llama3.2`. `compose-files.sh` decides, and a value in
`.env` wins. qwen2.5 read 8 of 8 test queries correctly in about 1.5 seconds on an RTX 3060.
llama3.2 invented values even with a forced schema.

## Consequences

- One new outside dependency, Wikidata. It is optional and fails soft.
- The model is asked with temperature 0, so the same query is read the same way each time. A reply
  with nothing the query supports counts as a failed attempt and is retried once.
- Limits, stated on purpose:
  - At most 80 movies go to the model check and 60 to a revenue lookup.
  - "Not starring X" still returns nothing. No source lists the movies X is absent from.
  - Genre on a person's movies depends on the model knowing the movie.
  - Rating sorts use TMDB's average vote, including movies with few votes.
- Decades, relative years and sort words are English only.

## Alternatives considered

- **Ask the model for the award winners.** It knows famous ones and invents the rest.
- **Store an award list in the repo.** It would be out of date after each ceremony.
- **Fix the 3B model with a stricter prompt.** Tried. A forced JSON schema made it consistent, but
  it still invented whole fields.
- **Always use the 7B model.** Too slow on a CPU. It fills the 6 GB of a small GPU.
