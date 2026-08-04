// CRA auto-loads this file (via setupFilesAfterEnv) before every test file.
import '@testing-library/jest-dom';

// jsdom has no window.matchMedia implementation; MUI's useMediaQuery (used by
// NavBar/Movies/Sidebar) calls it on every render, so every test needs a
// stub. A plain function (not jest.fn()) survives Jest's global
// `resetMocks: true` between tests. Components that care about a specific
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
