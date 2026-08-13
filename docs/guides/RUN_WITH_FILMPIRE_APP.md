# Running the LMDB React App Against This Backend

This is the runbook for running the LMDB React application (`frontend/filmpire` in this repo — merged in as a monorepo per [ADR-013](../architecture/adr/013-frontend-merged-into-monorepo.md); Vite + Redux Toolkit Query + MUI + Vosk offline voice control) against this repo's TMDB v3 facade instead of the real `api.themoviedb.org`. See [ARCHITECTURE.md §5.1](../architecture/ARCHITECTURE.md) for the facade contract and [ADR-010](../architecture/adr/010-tmdb-facade-mapped-persisted-schema.md) for why the facade is a persisted, typed catalog rather than a simple byte-cache proxy.

## 1. Start the backend stack

```bash
./gradlew deployLocal
# or: ./infrastructure/scripts/start-infrastructure.sh
```

This brings up the full `infrastructure/docker/docker-compose.yml` stack via Docker/Podman Compose: PostgreSQL (pgvector), MongoDB, Redis, MinIO, Kafka, Ollama, Elasticsearch/Logstash/Kibana, OpenZipkin, Discovery (Eureka), Config Service, and all backend microservices. `infrastructure/docker/.env` must have `TMDB_API_KEY` set.

Wait for everything to report healthy:

```bash
./gradlew statusInfra
```

Smoke-test the facade directly before launching the app:

```bash
curl http://localhost:8080/genre/movie/list
curl "http://localhost:8080/movie/popular?page=1"
curl "http://localhost:8080/movie/550?append_to_response=videos,credits"
```

## 2. Dynamic Backend Discovery & App Startup

The frontend uses dynamic runtime auto-discovery ([`apiUrl.js`](../../frontend/lmdb/src/utils/apiUrl.js), [ADR-016](../architecture/adr/016-dynamic-backend-resolution.md)). When running locally (`http://localhost:5173` or `http://localhost:3000`), it automatically binds to `http://localhost:8080` without requiring manual environment overrides.

```bash
cd frontend/filmpire
npm install
npm run dev
```

The Vite development server will launch on `http://localhost:5173` (or `http://localhost:3000`).

## 3. Manual E2E Checklist

Automated tests ([`e2e/`](../../e2e/README.md) with Playwright) continuously test user flows, but for manual exploratory testing:

- [ ] **Home page:** category sidebar (Popular/Top Rated/Upcoming) switches movie grids
- [ ] **Genre browsing:** click a genre, confirm `discover/movie?with_genres=` filters
- [ ] **Search:** type a movie query, confirm `search/movie?query=` results
- [ ] **Movie details:** trailer modal plays, cast list renders, similar recommendations load
- [ ] **Actor page:** bio/photo render, filmography list populates (`discover/movie?with_cast=`)
- [ ] **User Auth:** register account, login, JWT token stored, profile page displays favorites
- [ ] **Favorites & Watchlist:** toggle favorites/watchlist on movie detail pages
- [ ] **Vosk Voice Control:** click microphone button, speak commands (e.g. "show me comedy movies")

## 4. Notes

- **Image Assets:** Movie poster and backdrop images stream directly from TMDB CDN (`image.tmdb.org`).
- **CORS:** The gateway's [`SecurityConfig.java`](../../backend/api-gateway/src/main/java/dev/lmdb/gateway/config/SecurityConfig.java) permits `http://localhost:5173`, `http://localhost:3000`, and `https://*.vercel.app`.
- **Teardown:** Run `./gradlew stopLocal` to stop all containers gracefully.
