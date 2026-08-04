# Running the Filmpire React App Against This Backend

This is the runbook for issue #34: pointing the existing Filmpire React app
(`frontend/filmpire` in this repo — merged in as a monorepo per
[ADR-013](../architecture/adr/013-frontend-merged-into-monorepo.md); was
previously the separate `~/Desktop/filmpire` project. CRA + RTK Query +
Vosk voice control) at this repo's TMDB v3 facade instead of the real
`api.themoviedb.org`, with the smallest possible diff to the app. See
ARCHITECTURE.md §5.1 for the facade contract and
`docs/architecture/adr/010-tmdb-facade-mapped-persisted-schema.md` for why
the facade is a persisted, typed catalog rather than a byte-cache proxy.

## 1. Start the backend stack

```bash
cd infrastructure/scripts
./start-infrastructure.sh
```

This brings up the full `infrastructure/docker/docker-compose.yml` stack via
Docker/Podman Compose: Postgres, MongoDB, Redis, MinIO, Elasticsearch/Kibana,
Discovery (Eureka), Config Service, and all four application services
(api-gateway on `:8080`, movie-service on `:8081`, user-service on `:8082`,
actor-service on `:8083`). `infrastructure/docker/.env` must have
`TMDB_API_KEY` set — the gateway needs it for the auth/account proxy
(#33) and the movie/actor services need it to populate the catalog.

Wait for everything to report healthy:

```bash
podman ps --format "{{.Names}}\t{{.Status}}"
```

Smoke-test the facade directly before touching the app:

```bash
curl http://localhost:8080/genre/movie/list
curl "http://localhost:8080/movie/popular?page=1"
curl "http://localhost:8080/movie/550?append_to_response=videos,credits"
```

## 2. Point the app at the backend

The only code change (`src/services/TMDB.js`, `src/utils/index.js`): both
TMDB base URLs read `process.env.REACT_APP_API_URL`, falling back to
`https://api.themoviedb.org/3` when it's unset. Everything else (query
params, the `api_key` the app appends to every request, response field
names) is untouched — the gateway accepts and ignores `api_key`, injecting
its own server-side key on the auth/account proxy routes.

Create `frontend/filmpire/.env.local` (gitignored, CRA's standard
local-override file — never commit it):

```
REACT_APP_API_URL=http://localhost:8080
```

`REACT_APP_TMDB_KEY` in `.env` can stay as-is; it's harmless dead weight
when talking to this backend (the query param is sent and ignored) and is
what's used again if `.env.local` is removed (see step 5).

## 3. Start the app

```bash
cd frontend/filmpire
npm start
```

CRA picks up `.env.local` on startup (restart required after creating or
editing it — it does not hot-reload). Confirm the base URL actually baked
into the bundle:

```bash
curl -s http://localhost:3000/static/js/bundle.js | grep -c "localhost:8080"   # expect > 0
```

## 4. Manual E2E checklist

Automated smoke-checks (headless Chrome screenshots + curl) already
confirmed the home page, a movie detail page, and an actor page render
correctly end-to-end through this backend, and that the read-through/
save-through cache-hit behavior is real (see movie-service logs: a repeat
`GET /movie/{id}` shows `Movie found in MongoDB` with no outbound TMDB call
logged, and the response drops from ~150ms to ~10ms). What still needs a
human with a real browser, mouse, and a TMDB account:

- [ ] Home page: category sidebar (Popular/Top Rated/Upcoming) switches lists
- [ ] Genre browsing: click a genre, confirm `discover/movie?with_genres=`
- [ ] Search: type a query, confirm `search/movie?query=` results
- [ ] Movie details: trailer modal opens and plays (YouTube key from
      `append_to_response=videos`), cast list, "you might also like"
      (recommendations/similar)
- [ ] Actor page: bio/photo render, filmography list populates
      (`discover/movie?with_cast=`)
- [ ] Login: click LOGIN, approve the request token on themoviedb.org,
      confirm redirect back and a session is established (the gateway's
      `/authentication/**` and `/account/**` proxy routes were verified
      directly with curl — `GET /authentication/token/new` returns a real
      TMDB request token — but the interactive approve-in-browser step and
      the resulting session/account calls need a live pass)
- [ ] Favorite / watchlist toggle on a movie detail page after logging in
- [ ] Vosk voice command (e.g. "show me action movies") — needs a
      microphone and can't be driven headlessly

Watch `podman logs -f filmpire-movie-service` (and `filmpire-actor-service`)
while clicking through the app to see cache misses (`fetching from TMDB`)
turn into hits (`found in MongoDB`) on repeat visits to the same movie/actor.

## 5. Verify drop-in parity (flip back to real TMDB)

```bash
mv frontend/filmpire/.env.local /tmp/env.local.bak   # or just delete it
# restart: pkill -f react-scripts; cd frontend/filmpire && npm start
curl -s http://localhost:3000/static/js/bundle.js | grep -c "localhost:8080"        # expect 0
curl -s http://localhost:3000/static/js/bundle.js | grep -c "api.themoviedb.org"    # expect > 0
```

Confirmed automatically during this issue's implementation: with
`.env.local` absent, the app renders identically (same home page layout,
same movies, same images) talking directly to `api.themoviedb.org` using
`REACT_APP_TMDB_KEY` from `.env` — restore `.env.local` afterward to go
back to the local backend.

## Notes

- Images always come straight from `image.tmdb.org` — neither this backend
  nor the app's own code changes touch image URLs (§3.8 / ARCHITECTURE.md).
- CORS: the gateway's `SecurityConfig` already allows
  `http://localhost:3000` (CRA's default port) — no gateway change was
  needed for this issue.
- To stop everything: `pkill -f react-scripts` for the app,
  `infrastructure/scripts/stop-infrastructure.sh` for the backend stack.
