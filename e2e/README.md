# Filmpire End-to-End Test Suite (Playwright)

This directory contains browser-level E2E tests that drive the real Filmpire React app (`frontend/filmpire`) against the local microservices stack and API Gateway. This complements the Postman/Newman API smoke tests by verifying actual user journeys in the DOM.

## Prerequisites & Stack Startup

Before running the Playwright tests locally, bring up both the backend services and the frontend application:

1. **Start the backend Docker infrastructure & microservices:**
   ```bash
   ./infrastructure/scripts/start-infrastructure.sh
   ```
   Ensure the API Gateway is running and accessible at `http://localhost:8080`.

2. **Start the React frontend application:**
   ```bash
   cd frontend/filmpire
   npm run start
   ```
   The frontend app should be live at `http://localhost:3000`.

## Running the E2E Suite

Navigate to the `e2e/` directory and run:

```bash
# Install Playwright dependencies (first run only)
npm install
npx playwright install --with-deps chromium

# Run all test scenarios in headless mode
npm test

# Run tests with interactive Playwright UI
npm run test:ui
```

## Test Scenarios Covered
- **User Journeys:** Movie browsing by category/genre, keyword search, detailed movie pages (trailers and cast credits), actor profile views, and list pagination.
- **Cache Egress Assertion:** Verifies via `/actuator/metrics` that repeat page visits serve cached responses without emitting outbound TMDB calls.
- **Authentication:** OAuth browser redirection is explicitly mocked/skipped here, as full live token exchanges are tested at the API level by `#33`.
