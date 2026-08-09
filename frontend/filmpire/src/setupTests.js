// Vitest auto-loads this file (via the `test.setupFiles` entry in
// vite.config.js) before every test file.
import '@testing-library/jest-dom';

// jsdom's CSS.escape is spec-strict about its receiver: called as a detached
// function reference (no `CSS.` in front) it throws "called on an object
// that is not a valid instance of CSS". @mui/styles' JSS caches exactly such
// a detached reference (`CSS.escape` with no bound `this`) the first time it
// is imported, so any component using `makeStyles` blows up. Binding it here
// — before any test file (and therefore JSS) is imported — makes the cached
// reference itself carry the right `this`, which fixes it for every test.
if (typeof window !== 'undefined' && window.CSS && typeof window.CSS.escape === 'function') {
  window.CSS.escape = window.CSS.escape.bind(window.CSS);
}

// jsdom has no window.matchMedia implementation; MUI's useMediaQuery (used by
// NavBar/Movies/Sidebar) calls it on every render, so every test needs a
// stub. A plain function (not vi.fn()) survives Vitest's global
// `mockReset: true` between tests. Components that care about a specific
// breakpoint override this per-test.
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
