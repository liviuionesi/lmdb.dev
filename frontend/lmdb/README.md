# LMDB Frontend

A modern, responsive movie streaming & discovery application built with React 19, Vite, Material-UI, Redux Toolkit Query, and Vosk offline voice recognition.

## Tech Stack

- **Framework**: React 19 (Vite Build Tooling)
- **UI Components**: Material-UI v9 (MUI) + Emotion
- **State Management & Caching**: Redux Toolkit & RTK Query
- **Routing**: React Router DOM v7
- **Voice Control**: Vosk Speech-to-Text integration via `ai-service`
- **Natural-Language Search**: free-text/dictated movie search resolved by `ai-service` (parsing + cross-service aggregation with actor-service/movie-service — see [ADR-020](../../docs/architecture/adr/020-nl-query-cross-service-aggregation.md))
- **Testing**: Vitest 4 + React Testing Library (180+ tests)
- **Code Quality**: ESLint + Prettier

## Natural-Language Search

The search bar (`src/components/Search/Search.jsx`) does not call
movie-service's title search directly. It dispatches a single mutation from
a dedicated RTK Query slice, `aiApi` (`src/services/AI.js`), to
`POST /api/v1/ai/search/execute` — reached through the API Gateway, not
movie-service. ai-service parses the free text into a structured filter (or
a plain-title fallback) and aggregates results across actor-service and
movie-service itself; the frontend never branches on query shape or makes
more than one call. See [ADR-020](../../docs/architecture/adr/020-nl-query-cross-service-aggregation.md)
for why the frontend consumes ai-service this way instead of orchestrating
multiple backend calls client-side.

## Voice Assistant Setup (Vosk Speech-to-Text)

To enable click-to-talk voice commands:

1. **Start the Microservices Backend**:
   Ensure `ai-service` is running on port 8084 (or via API Gateway on port 8080).

2. **Voice Commands**:
   Click the microphone button to record audio. Spoken audio is transcribed by `ai-service` via Vosk and automatically executed:
   - Browse genres (e.g., *"show me action movies"*)
   - Toggle theme (e.g., *"change to dark mode"*)
   - Search movies (e.g., *"search Inception"*)
   - Auth actions (e.g., *"log out"*)

## Available Scripts

In `frontend/lmdb`:

### `npm run dev`
Runs the application in development mode with Hot Module Replacement (HMR) on [http://localhost:5173](http://localhost:5173) (or `http://localhost:3000`).

### `npm test`
Runs the Vitest component and hook test suite.

### `npm run test:coverage`
Generates a full test coverage report.

### `npm run build`
Bundles and optimizes the production SPA to the `dist/` folder for Vercel deployment.

## Architecture & Design

For deep architectural details on RTK Query caching policies, dynamic URL resolution, and component hierarchy, see [FRONTEND_ARCHITECTURE.md](FRONTEND_ARCHITECTURE.md).

## Deployment

The application is deployed on Vercel: [https://lmdb.dev/](https://lmdb.dev/)

