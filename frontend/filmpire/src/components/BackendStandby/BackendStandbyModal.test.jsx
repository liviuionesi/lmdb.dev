import React from 'react';
import { screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import BackendStandbyModal from './BackendStandbyModal';
import { renderWithProviders } from '../../test-utils/render';
import { useBackendWakeup } from './useBackendWakeup';

vi.mock('./useBackendWakeup');

describe('BackendStandbyModal User-Oriented Subtitles & Pure Trailer', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers();
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('does not open when backend status is ONLINE', () => {
    useBackendWakeup.mockReturnValue({
      status: 'ONLINE',
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.queryByTestId('trailer-iframe')).not.toBeInTheDocument();
  });

  it('renders clean YouTube trailer and initial user-friendly subtitle', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
    });

    renderWithProviders(<BackendStandbyModal />);

    const iframe = screen.getByTestId('trailer-iframe');
    expect(iframe).toBeInTheDocument();
    expect(iframe.getAttribute('src')).toContain('https://www.youtube-nocookie.com/embed/');

    const subtitle = screen.getByTestId('standby-subtitle');
    expect(subtitle).toBeInTheDocument();
    expect(screen.getByText('Welcome to Filmpire Theaters. Getting your movie experience ready...')).toBeInTheDocument();
  });

  it('advances user-friendly subtitles every 5 seconds and disappears after all finish', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
    });

    renderWithProviders(<BackendStandbyModal />);

    expect(screen.getByText('Welcome to Filmpire Theaters. Getting your movie experience ready...')).toBeInTheDocument();

    // Advance 5 seconds
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText('Preparing top blockbuster trailers and theater collections...')).toBeInTheDocument();

    // Advance another 5 seconds
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText('Bringing together thousands of movies, cast spotlights, and reviews...')).toBeInTheDocument();

    // Advance another 5 seconds
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.getByText('Dimming the lights. Enjoy the show...')).toBeInTheDocument();

    // Advance another 5 seconds -> subtitles disappear
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(screen.queryByTestId('standby-subtitle')).not.toBeInTheDocument();
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

    // Simulate backend reaching healthy state
    act(() => {
      capturedOnReady();
    });

    expect(screen.getByTestId('filmpire-logo-reveal')).toBeInTheDocument();
    expect(screen.getByText(/Now Showing/)).toBeInTheDocument();
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
