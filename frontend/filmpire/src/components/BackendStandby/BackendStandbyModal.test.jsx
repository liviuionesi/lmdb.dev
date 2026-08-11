import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import BackendStandbyModal from './BackendStandbyModal';
import { renderWithProviders } from '../../test-utils/render';
import { useBackendWakeup } from './useBackendWakeup';

vi.mock('./useBackendWakeup');

describe('BackendStandbyModal Cinematic Pre-Show', () => {
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

  it('renders cinematic pre-show with countdown and act 1 progression when waking up', () => {
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
    expect(screen.getByText('75s until showtime')).toBeInTheDocument();
    expect(screen.getByText('ACT I: SCENE SETUP')).toBeInTheDocument();
    expect(screen.getByText('Dimming the House Lights')).toBeInTheDocument();
    expect(screen.getByText(/Powering up the cloud projector/)).toBeInTheDocument();
    expect(screen.getByText('Screen 1: Azure AKS')).toBeInTheDocument();
    expect(screen.getByText('Screen 2: AWS EC2')).toBeInTheDocument();

    // Click cloud switch button
    fireEvent.click(screen.getByText('Screen 2: AWS EC2'));
    expect(wakeUpMock).toHaveBeenCalledWith('aws');
  });

  it('renders act 2 details when currentStep is 2', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 45,
      progressPercentage: 50,
      targetCloud: 'azure',
      currentStep: 2,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);

    expect(screen.getByText('ACT II: COMING ATTRACTIONS')).toBeInTheDocument();
    expect(screen.getByText('Rolling the Film Reels')).toBeInTheDocument();
    expect(screen.getByText(/Loading thousands of movies, cast details/)).toBeInTheDocument();
  });

  it('removes modal immediately when status is READY or ONLINE', () => {
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
