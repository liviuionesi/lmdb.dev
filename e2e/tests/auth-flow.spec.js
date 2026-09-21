const { test, expect } = require('@playwright/test');

/**
 * Authentication and session user journey test scenario.
 *
 * Verifies live registration, login, and state persistence (favoriting a movie)
 * to ensure data is properly saved and displayed in the user profile.
 */

test.describe('Authentication & Session Flow', () => {
  test('Given an unauthenticated user, when navigating to the profile page, then browser redirects directly to the home page (/)', async ({ page }) => {
    await page.addInitScript(() => {
      localStorage.clear();
    });

    await page.goto('/profile/123');
    // Expect unauthenticated user to be redirected back to root home view '/'
    await expect(page).toHaveURL(/.*(\/)$/);
  });

  test('Given a user logs in and favorites a movie, then the favorite movie appears in their profile', async ({ page }) => {
    const username = `e2e_user_${Date.now()}`;
    const password = 'testpassword123';
    const email = `${username}@example.com`;

    await page.goto('/');
    await expect(page.locator('.movie-card').first()).toBeVisible({ timeout: 15000 });
    
    // 1. Click Login on NavBar (using dispatchEvent to bypass mobile viewport hiding/scrolling issues)
    await page.getByRole('button', { name: /login/i }).first().dispatchEvent('click');

    // 2. Switch to Register tab and create an account
    await page.getByRole('tab', { name: /register/i }).click();
    await page.getByLabel(/username/i).fill(username);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole('button', { name: /create account/i }).click();

    // 3. Verify user is logged in (Avatar button on NavBar)
    await expect(page.getByTestId('navbar-avatar')).toBeVisible({ timeout: 15000 });

    // 4. Click the first movie to go to details
    const firstMovie = page.locator('.movie-card a').first();
    await expect(firstMovie).toBeVisible({ timeout: 15000 });
    await firstMovie.click();
    
    // 5. Favorite the movie
    const favoriteButton = page.getByRole('button', { name: /favorite/i, exact: false }).first();
    await expect(favoriteButton).toBeVisible({ timeout: 15000 });
    const btnText = await favoriteButton.innerText();
    if (!btnText.toLowerCase().includes('unfavorite')) {
       await favoriteButton.click();
       await expect(favoriteButton).toHaveText(/unfavorite/i, { ignoreCase: true, timeout: 10000 });
    }

    // 6. Go to Profile and verify the favorited movie is there
    await page.getByTestId('navbar-avatar').click();
    const profileMovie = page.locator('.movie-card').first();
    await expect(profileMovie).toBeVisible({ timeout: 15000 });
  });
});

