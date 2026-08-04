const { defineConfig, devices } = require('@playwright/test');

/**
 * Playwright test configuration for Filmpire E2E tests.
 * Assumes the local frontend React app is accessible at http://localhost:3000
 * and the backend API gateway is accessible at http://localhost:8080.
 */
module.exports = defineConfig({
  testDir: './tests',
  fullyParallel: false,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: [['html', { open: 'never' }], ['list']],
  use: {
    baseURL: process.env.FRONTEND_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
