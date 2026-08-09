// Tests ToggleColorMode's mode resolution (saved/system preference), the
// toggle action, that it persists the chosen mode to localStorage, and that
// its context value stays referentially stable across unrelated re-renders.
import React, { useContext, useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import ToggleColorMode, { ColorModeContext } from './ToggleColorMode';

function Probe() {
  const { mode, toggleColorMode } = useContext(ColorModeContext);
  return (
    <div>
      <span>Mode: {mode}</span>
      <button type="button" onClick={toggleColorMode}>Toggle</button>
    </div>
  );
}

// Reports every context value it sees (via `onValue`) without rendering
// anything itself, so a test can inspect reference identity across renders.
function ReferenceProbe({ onValue }) {
  const value = useContext(ColorModeContext);
  onValue(value);
  return null;
}

// Owns state *above* ToggleColorMode so clicking "Rerender" re-renders
// ToggleColorMode itself (not just one of its children) without touching
// `mode` — the state lives in this wrapper, not in ToggleColorMode or below
// it, which is what actually re-invokes ToggleColorMode's function body on
// each click and exercises its useCallback/useMemo memoization.
function RerenderWrapper({ children }) {
  const [, setTick] = useState(0);
  return (
    <>
      <button type="button" onClick={() => setTick((n) => n + 1)}>Rerender</button>
      <ToggleColorMode>{children}</ToggleColorMode>
    </>
  );
}

describe('ToggleColorMode', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it('defaults to light mode when there is no saved preference or system dark scheme', () => {
    window.matchMedia = (query) => ({ matches: false, media: query, addEventListener: () => {}, removeEventListener: () => {} });
    render(<ToggleColorMode><Probe /></ToggleColorMode>);

    expect(screen.getByText('Mode: light')).toBeInTheDocument();
  });

  it('defaults to dark mode when the system prefers dark and nothing is saved', () => {
    window.matchMedia = (query) => ({ matches: true, media: query, addEventListener: () => {}, removeEventListener: () => {} });
    render(<ToggleColorMode><Probe /></ToggleColorMode>);

    expect(screen.getByText('Mode: dark')).toBeInTheDocument();
  });

  it('uses the saved preference over the system scheme', () => {
    localStorage.setItem('themeMode', 'dark');
    window.matchMedia = (query) => ({ matches: false, media: query, addEventListener: () => {}, removeEventListener: () => {} });
    render(<ToggleColorMode><Probe /></ToggleColorMode>);

    expect(screen.getByText('Mode: dark')).toBeInTheDocument();
  });

  it('toggleColorMode flips the mode and persists it', async () => {
    window.matchMedia = (query) => ({ matches: false, media: query, addEventListener: () => {}, removeEventListener: () => {} });
    render(<ToggleColorMode><Probe /></ToggleColorMode>);

    // userEvent v14+ dispatches events asynchronously, so the click must be awaited.
    await userEvent.click(screen.getByText('Toggle'));

    expect(screen.getByText('Mode: dark')).toBeInTheDocument();
    expect(localStorage.getItem('themeMode')).toBe('dark');
  });

  it('keeps the context value and toggleColorMode reference stable across a re-render that does not change mode', async () => {
    window.matchMedia = (query) => ({ matches: false, media: query, addEventListener: () => {}, removeEventListener: () => {} });
    const seenValues = [];
    render(
      <RerenderWrapper>
        <ReferenceProbe onValue={(value) => seenValues.push(value)} />
      </RerenderWrapper>,
    );
    const beforeRerender = seenValues.at(-1);

    // Triggers RerenderWrapper's state update, which re-renders ToggleColorMode
    // itself without touching `mode` — without useCallback/useMemo in
    // ToggleColorMode, this would produce a brand-new context value object and
    // a brand-new toggleColorMode function every time, defeating memoization
    // for every consumer regardless of whether `mode` actually changed.
    await userEvent.click(screen.getByText('Rerender'));
    const afterRerender = seenValues.at(-1);

    expect(afterRerender).toBe(beforeRerender);
    expect(afterRerender.toggleColorMode).toBe(beforeRerender.toggleColorMode);
  });
});
