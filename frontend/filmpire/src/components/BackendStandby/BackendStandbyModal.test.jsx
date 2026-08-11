import React from 'react';
import { screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import BackendStandbyModal from './BackendStandbyModal';
import { renderWithProviders } from '../../test-utils/render';
import { useBackendWakeup } from './useBackendWakeup';

vi.mock('./useBackendWakeup');

describe('BackendStandbyModal Pure Cinema Trailer & Logo Reveal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('does not open when backend status is ONLINE', () => {
    useBackendWakeup.mockReturnValue({
      status: 'ONLINE',
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.queryByTestId('trailer-iframe')).not.toBeInTheDocument();
  });

  it('renders clean YouTube trailer iframe without text overlay distractions', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
    });

    renderWithProviders(<BackendStandbyModal />);

    const iframe = screen.getByTestId('trailer-iframe');
    expect(iframe).toBeInTheDocument();
    expect(iframe.getAttribute('src')).toContain('https://www.youtube-nocookie.com/embed/');
  });

  it('displays Filmpire logo reveal when onReady callback is invoked', () => {
    let capturedOnReady;
    useBackendWakeup.mockImplementation(({ onReady }) => {
      capturedOnReady = onReady;
      return {
        status: 'WAKING_UP',
      };
    });

    const onBackendReadyMock = vi.fn();
    renderWithProviders(<BackendStandbyModal onBackendReady={onBackendReadyMock} />);

    expect(screen.getByTestId('trailer-iframe')).toBeInTheDocument();

    // Simulate backend reaching healthy state inside act()
    act(() => {
      capturedOnReady();
    });

    expect(screen.getByTestId('filmpire-logo-reveal')).toBeInTheDocument();
    expect(screen.getByText(/Backend Online • Rolling Feature/)).toBeInTheDocument();
    expect(onBackendReadyMock).toHaveBeenCalled();
  });

  it('allows dismissing the trailer modal via close button', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.getByTestId('trailer-iframe')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('close'));
    expect(screen.queryByTestId('trailer-iframe')).not.toBeInTheDocument();
  });
});
