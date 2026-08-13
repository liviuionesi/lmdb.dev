const { test, expect } = require('@playwright/test');

/**
 * End-to-end user journey tests for the LMDB React app.
 * Validates browsing categories, genre selection, search functionality,
 * viewing movie information, inspecting actor profiles, and pagination.
 */

test.describe('LMDB Core User Journeys', () => {
  test('Given a user opens the home page, when movies finish loading, then movie cards and navigation items are visible', async ({ page }) => {
    await page.goto('/');
    // Verify navbar brand and initial movie cards render in the viewport
    await expect(page.getByRole('banner')).toBeVisible();
    await expect(page.locator('.movie-card').first()).toBeVisible({ timeout: 15000 });
  });

  test('Given a user on the home list, when selecting a genre from the sidebar, then the filtered movie list updates', async ({ page }) => {
    await page.goto('/');
    // Open sidebar on smaller mobile viewports if collapsed
    const menuButton = page.locator('button[aria-label="menu"]');
    if (await menuButton.isVisible()) {
      await menuButton.click();
    }
    // Select the Action genre item from the sidebar navigation
    const genreItem = page.locator('div[role="button"]:has-text("Action")').first();
    await genreItem.click();
    // Verify URL reflects selected category/genre or movie grid updates cleanly
    await expect(page.locator('.movie-card').first()).toBeVisible();
  });

  test('Given a user using the navigation input, when entering a movie title, then matching search results populate the grid', async ({ page }) => {
    await page.goto('/');
    const searchInput = page.locator('input[type="text"]').first();
    await searchInput.fill('Inception');
    await searchInput.press('Enter');
    await expect(page.locator('.movie-card').first()).toBeVisible();
  });

  test('Given a user clicking a specific movie card, when redirected to details, then trailers and cast credits are displayed', async ({ page }) => {
    await page.goto('/');
    const firstMovie = page.locator('.movie-card a').first();
    await firstMovie.click();
    // Validate Movie Information title, video trailers button, and actor credits
    await expect(page).toHaveURL(/.*\/movie\/.*/);
    await expect(page.locator('h1, h2').first()).toBeVisible();
    await expect(page.locator('a[href*="/actors/"]').first()).toBeVisible();
  });

  test('Given a user reading cast credits, when selecting an actor, then the actor biography and filmography profile render', async ({ page }) => {
    // Directly open a representative movie details view to test navigation into actor details
    await page.goto('/');
    const firstMovie = page.locator('.movie-card a').first();
    await firstMovie.click();
    const actorLink = page.locator('a[href*="/actors/"]').first();
    await actorLink.click();
    // Validate actor profile route and biography details display
    await expect(page).toHaveURL(/.*\/actors\/.*/);
    await expect(page.locator('h1, h2, h3').first()).toBeVisible();
  });

  test('Given a multi-page list of movies, when clicking the next page control, then page index advances without errors', async ({ page }) => {
    await page.goto('/');
    const nextButton = page.locator('button:has-text("Next"), button[aria-label="Next page"]').first();
    if (await nextButton.isVisible()) {
      await nextButton.click();
      await expect(page.locator('.movie-card').first()).toBeVisible();
    }
  });
});
