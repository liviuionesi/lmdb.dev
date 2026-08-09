import { fileURLToPath, URL } from 'node:url';
import { defineConfig, coverageConfigDefaults } from 'vitest/config';
import react from '@vitejs/plugin-react';

/**
 * Vite build/dev-server configuration for the Filmpire frontend, plus the
 * Vitest `test` block (#127) that replaces CRA's Jest setup.
 *
 * Replaces the previous CRA (react-scripts) toolchain (#125). `@vitejs/plugin-react`
 * enables JSX transform + Fast Refresh; the `@` alias mirrors the common Vite
 * convention for absolute imports rooted at `src/` (the codebase itself currently
 * only uses relative imports, but the alias is set up so future code doesn't have
 * to fall back to CRA-style implicit resolution). `defineConfig` comes from
 * `vitest/config` rather than plain `vite` so the `test` key below is
 * recognized alongside the regular Vite options — it re-exports Vite's own
 * `defineConfig`, so nothing about the build/dev behavior changes.
 *
 * See https://vite.dev/config/ for the full Vite options reference and
 * https://vitest.dev/config/ for the full Vitest `test` options reference.
 */
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    // 1. Match the CRA dev server's default port so existing local workflows
    //    (docs, bookmarks, the gateway's CORS allow-list) keep working.
    port: 3000,
  },
  build: {
    // 2. `dist/` is the Vite convention (replacing CRA's `build/`) and is
    //    already covered by the repo's .gitignore.
    outDir: 'dist',
  },
  test: {
    // 3. `globals: true` keeps describe/it/expect/vi available without an
    //    import in every test file, matching how the existing suite (ported
    //    from Jest, which is always-global) is already written.
    globals: true,
    // 4. jsdom supplies the DOM the component tests render into; Vitest
    //    doesn't default to it the way CRA's Jest preset did.
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.js'],
    // 5. Mirrors CRA's Jest preset default (`resetMocks: true`): every mock's
    //    calls *and* implementation are cleared between tests, so a
    //    `mockReturnValue`/`mockImplementation` from one test can't leak into
    //    the next.
    mockReset: true,
    coverage: {
      provider: 'v8',
      include: ['src/**/*.{js,jsx}'],
      // 6. Same exclusion list as the old `jest.collectCoverageFrom` config
      //    (test files, test-only helpers, and bootstrap/static files with
      //    no branch logic worth gating on), layered on top of Vitest's own
      //    coverage defaults (node_modules, config files, etc.).
      exclude: [
        ...coverageConfigDefaults.exclude,
        'src/**/*.test.{js,jsx}',
        'src/setupTests.js',
        'src/test-utils/**',
        'src/index.jsx',
        'src/app/store.js',
        'src/assets/**',
        'src/components/index.js',
        'src/components/styles.js',
        'src/components/App.jsx',
      ],
      thresholds: {
        branches: 80,
        functions: 80,
        lines: 80,
        statements: 80,
      },
    },
  },
});
