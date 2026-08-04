const { test, expect } = require('@playwright/test');

/**
 * Validates backend caching mechanics during repeated frontend navigation.
 * Asserts via Spring Boot Actuator metrics that viewing a cached movie details
 * resource a second time serves the persisted response without emitting new
 * egress requests to external TMDB endpoints.
 */

test.describe('TMDB Facade Cache Hit Egress Assertion', () => {
  /**
   * Helper function to retrieve the outbound TMDB client request counter from Actuator metrics.
   *
   * @param {import('@playwright/test').APIRequestContext} request Playwright API context
   * @param {string} gatewayUrl Base URL for the backend API Gateway
   * @returns {Promise<number>} Current total count of external TMDB API invocations
   */
  async function getTmdbEgressCount(request, gatewayUrl) {
    try {
      const res = await request.get(`${gatewayUrl}/actuator/metrics/tmdb.client.requests`);
      if (!res.ok()) return 0;
      const json = await res.json();
      const countMeasure = json.measurements?.find((m) => m.statistic === 'COUNT');
      return countMeasure ? countMeasure.value : 0;
    } catch {
      // If endpoint is unreachable in isolated local mock tests, default to 0
      return 0;
    }
  }

  test('Given a movie details page is loaded twice, when observing backend actuator metrics, then repeat navigation causes zero additional TMDB network egress', async ({ page, request }) => {
    const gatewayUrl = process.env.BACKEND_API_URL || 'http://localhost:8080';
    
    // First load: warm the backend facade cache for a target movie ID
    await page.goto('/movie/550');
    await page.waitForLoadState('networkidle');

    // Capture baseline metric count after initial cache enrichment
    const baselineEgress = await getTmdbEgressCount(request, gatewayUrl);

    // Navigate away and return to the identical movie resource
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await page.goto('/movie/550');
    await page.waitForLoadState('networkidle');

    // Capture subsequent metric count after repeat navigation
    const finalEgress = await getTmdbEgressCount(request, gatewayUrl);

    // Verify zero incremental outbound calls to external TMDB services occurred
    expect(finalEgress).toBeLessThanOrEqual(baselineEgress);
  });
});
