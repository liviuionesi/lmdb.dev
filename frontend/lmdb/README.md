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

## Recommendations

`/recommendations` (`src/components/Recommendations/Recommendations.jsx`, sign-in required)
shows AI-generated movie picks, each with ai-service's own explanation for
why it was suggested. The view calls `aiApi.getMovieRecommendations`
(`src/services/AI.js`), which builds its request from the signed-in user's
Favorites — resolving each favorited movie's title through the TMDB facade,
since user-service only ever returns a movie id — then posts it to
`POST /api/v1/ai/recommendations`. A user with no favorites yet sees a
prompt to favorite some movies instead of an empty or erroring list.

## Chat Assistant

A persistent chat launcher (`src/components/ChatWidget/ChatWidget.jsx`), fixed to the bottom-left
corner, is available on every page. It opens a panel with a message list and an input, independent
of the voice control Fab (bottom-right, see below), so the two never overlap.

Sending a message posts to `POST /api/v1/ai/chat` (`aiApi.sendChatMessage`, `src/services/AI.js`)
and appends the assistant's reply. The `conversationId` the backend returns is stored via
`src/utils/chatConversation.js` and survives a page reload; it is sent on every following message so
the conversation continues server-side, and omitted on the first message of a new one. The header's
"Start a new conversation" button clears the stored id and the visible history. A failed send shows
a dismissible error and keeps the typed message; a pending send shows a "typing" indicator. Both, and
each new assistant reply, are announced to screen readers via an `aria-live="polite"` region.

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

