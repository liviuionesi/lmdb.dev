import React from 'react';
import { screen, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import BackendStandbyModal from './BackendStandbyModal';
import { renderWithProviders } from '../../test-utils/render';
import { useBackendWakeup } from './useBackendWakeup';

vi.mock('./useBackendWakeup');

describe('BackendStandbyModal Cinematic Trailer & Logo Reveal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('does not open when backend status is ONLINE', () => {
    useBackendWakeup.mockReturnValue({
      status: 'ONLINE',
      secondsRemaining: 0,
      targetCloud: 'azure',
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.queryByTestId('trailer-iframe')).not.toBeInTheDocument();
  });

  it('renders YouTube trailer iframe with default video and announcer subtitles', () => {
    const wakeUpMock = vi.fn();
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 75,
      targetCloud: 'azure',
      wakeUp: wakeUpMock,
    });

    renderWithProviders(<BackendStandbyModal />);

    const iframe = screen.getByTestId('trailer-iframe');
    expect(iframe).toBeInTheDocument();
    expect(iframe.getAttribute('src')).toContain('h2QJMfXJZaY');

    // Announcer subtitle
    expect(screen.getByText(/Welcome to Filmpire Theaters! Starting the cloud backend for you/)).toBeInTheDocument();

    // Soundstage choices
    expect(screen.getByText('Screen 1: Azure AKS')).toBeInTheDocument();
    expect(screen.getByText('Screen 2: AWS EC2')).toBeInTheDocument();
    expect(screen.getByText('Screen 3: Minikube Tunnel')).toBeInTheDocument();

    // Click cloud switch button
    fireEvent.click(screen.getByText('Screen 2: AWS EC2'));
    expect(wakeUpMock).toHaveBeenCalledWith('aws');
  });

  it('allows switching trailer preset from the curated playlist', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 50,
      targetCloud: 'azure',
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);

    // Click Oppenheimer chip
    fireEvent.click(screen.getByText('Oppenheimer'));

    const iframe = screen.getByTestId('trailer-iframe');
    expect(iframe.getAttribute('src')).toContain('uYPbbksJxIg');
  });

  it('allows searching and loading custom trailer via YouTube URL', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 50,
      targetCloud: 'azure',
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);

    // Open search bar
    fireEvent.click(screen.getByText('Custom Trailer URL / ID'));

    const input = screen.getByPlaceholderText(/Paste YouTube Video Link/);
    fireEvent.change(input, { target: { value: 'https://youtu.be/zSWdZVtXT7E' } });
    fireEvent.click(screen.getByText('Load'));

    const iframe = screen.getByTestId('trailer-iframe');
    expect(iframe.getAttribute('src')).toContain('zSWdZVtXT7E');
  });

  it('displays Filmpire logo reveal when onReady callback is invoked', () => {
    let capturedOnReady;
    useBackendWakeup.mockImplementation(({ onReady }) => {
      capturedOnReady = onReady;
      return {
        status: 'WAKING_UP',
        secondsRemaining: 5,
        targetCloud: 'azure',
        wakeUp: vi.fn(),
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
      secondsRemaining: 80,
      targetCloud: 'azure',
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.getByTestId('trailer-iframe')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('close'));
    expect(screen.queryByTestId('trailer-iframe')).not.toBeInTheDocument();
  });
});
