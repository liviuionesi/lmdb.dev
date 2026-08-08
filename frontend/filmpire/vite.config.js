import { fileURLToPath, URL } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/**
 * Vite build/dev-server configuration for the Filmpire frontend.
 *
 * Replaces the previous CRA (react-scripts) toolchain (#125). `@vitejs/plugin-react`
 * enables JSX transform + Fast Refresh; the `@` alias mirrors the common Vite
 * convention for absolute imports rooted at `src/` (the codebase itself currently
 * only uses relative imports, but the alias is set up so future code doesn't have
 * to fall back to CRA-style implicit resolution).
 *
 * See https://vite.dev/config/ for the full options reference.
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
});
