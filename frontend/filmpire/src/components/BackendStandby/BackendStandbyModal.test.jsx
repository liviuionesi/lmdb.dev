import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest';
import BackendStandbyModal from './BackendStandbyModal';
import { renderWithProviders } from '../../test-utils/render';
import { useBackendWakeup } from './useBackendWakeup';
import { useCinemaAudio } from './useCinemaAudio';

vi.mock('./useBackendWakeup');
vi.mock('./useCinemaAudio');

// Mock HTML5 Canvas getContext
beforeAll(() => {
  HTMLCanvasElement.prototype.getContext = vi.fn(() => ({
    clearRect: vi.fn(),
    createRadialGradient: vi.fn(() => ({
      addColorStop: vi.fn(),
    })),
    beginPath: vi.fn(),
    moveTo: vi.fn(),
    lineTo: vi.fn(),
    closePath: vi.fn(),
    fill: vi.fn(),
    arc: vi.fn(),
    stroke: vi.fn(),
    save: vi.fn(),
    restore: vi.fn(),
    setLineDash: vi.fn(),
  }));
});

describe('BackendStandbyModal Cinematic Film Leader Experience', () => {
  beforeEach(() => {
    useCinemaAudio.mockReturnValue({
      isPlaying: false,
      toggleAudio: vi.fn(),
      stopAudio: vi.fn(),
    });
  });

  it('does not open when backend status is ONLINE', () => {
    useBackendWakeup.mockReturnValue({
      status: 'ONLINE',
      secondsRemaining: 0,
      progressPercentage: 100,
      targetCloud: 'azure',
      currentStep: 3,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.queryByText('Feature Presentation')).not.toBeInTheDocument();
  });

  it('renders cinematic 35mm film leader with countdown, clapperboard, and act 1', () => {
    const wakeUpMock = vi.fn();
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 75,
      progressPercentage: 20,
      targetCloud: 'azure',
      currentStep: 1,
      wakeUp: wakeUpMock,
    });

    renderWithProviders(<BackendStandbyModal />);

    expect(screen.getByText('Feature Presentation')).toBeInTheDocument();
    expect(screen.getByText('★ FILMPIRE STUDIOS PRESENTS ★')).toBeInTheDocument();
    expect(screen.getByText('75')).toBeInTheDocument();
    expect(screen.getByText(/ACT I \/\/ SCENE 01/)).toBeInTheDocument();
    expect(screen.getByText(/THE PROJECTION BOOTH/)).toBeInTheDocument();
    expect(screen.getByText(/Dimming the House Lights/)).toBeInTheDocument();
    expect(screen.getByText('IMAX Screen 1: Azure AKS')).toBeInTheDocument();
    expect(screen.getByText('Dolby Screen 2: AWS EC2')).toBeInTheDocument();

    // Click cloud switch button
    fireEvent.click(screen.getByText('Dolby Screen 2: AWS EC2'));
    expect(wakeUpMock).toHaveBeenCalledWith('aws');
  });

  it('toggles cinema synthesizer audio on click', () => {
    const toggleAudioMock = vi.fn();
    useCinemaAudio.mockReturnValue({
      isPlaying: false,
      toggleAudio: toggleAudioMock,
      stopAudio: vi.fn(),
    });

    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 60,
      progressPercentage: 30,
      targetCloud: 'azure',
      currentStep: 2,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);

    const audioButton = screen.getByTitle('Play 35mm Projector Sound');
    fireEvent.click(audioButton);
    expect(toggleAudioMock).toHaveBeenCalled();
  });

  it('removes modal immediately when status transitions to READY or ONLINE', () => {
    useBackendWakeup.mockReturnValue({
      status: 'READY',
      secondsRemaining: 0,
      progressPercentage: 100,
      targetCloud: 'azure',
      currentStep: 3,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.queryByText('Feature Presentation')).not.toBeInTheDocument();
  });

  it('allows dismissing the pre-show via close button', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 80,
      progressPercentage: 10,
      targetCloud: 'azure',
      currentStep: 1,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.getByText('Feature Presentation')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('close'));
    expect(screen.queryByText('Feature Presentation')).not.toBeInTheDocument();
  });
});
