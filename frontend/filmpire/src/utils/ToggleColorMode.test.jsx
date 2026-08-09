// Tests ToggleColorMode's mode resolution (saved/system preference), the
// toggle action, and that it persists the chosen mode to localStorage.
import React, { useContext } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import ToggleColorMode, { ColorModeContext } from './ToggleColorMode';

const Probe = () => {
  const { mode, toggleColorMode } = useContext(ColorModeContext);
  return (
    <div>
      <span>Mode: {mode}</span>
      <button type="button" onClick={toggleColorMode}>Toggle</button>
    </div>
  );
};

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
});
