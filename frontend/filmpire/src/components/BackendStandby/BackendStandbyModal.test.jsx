import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import BackendStandbyModal from './BackendStandbyModal';
import { renderWithProviders } from '../../test-utils/render';
import { useBackendWakeup } from './useBackendWakeup';

vi.mock('./useBackendWakeup');

describe('BackendStandbyModal', () => {
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
    expect(screen.queryByText('Waking Up Cloud Cluster')).not.toBeInTheDocument();
  });

  it('renders modal with countdown and step progression when status is WAKING_UP', () => {
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

    expect(screen.getByText('Waking Up Cloud Cluster')).toBeInTheDocument();
    expect(screen.getByText('75s')).toBeInTheDocument();
    expect(screen.getByText(/1. Initializing AZURE compute nodes/)).toBeInTheDocument();
    expect(screen.getByText('Azure AKS')).toBeInTheDocument();
    expect(screen.getByText('AWS EC2 (k3s)')).toBeInTheDocument();

    // Click cloud switch button
    fireEvent.click(screen.getByText('AWS EC2 (k3s)'));
    expect(wakeUpMock).toHaveBeenCalledWith('aws');
  });

  it('displays Backend Online! when status is READY', () => {
    useBackendWakeup.mockReturnValue({
      status: 'READY',
      secondsRemaining: 0,
      progressPercentage: 100,
      targetCloud: 'azure',
      currentStep: 3,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.getByText('Backend Online!')).toBeInTheDocument();
    expect(screen.getByText(/Services are fully initialized/)).toBeInTheDocument();
  });

  it('allows dismissing the modal via close button', () => {
    useBackendWakeup.mockReturnValue({
      status: 'WAKING_UP',
      secondsRemaining: 80,
      progressPercentage: 10,
      targetCloud: 'azure',
      currentStep: 1,
      wakeUp: vi.fn(),
    });

    renderWithProviders(<BackendStandbyModal />);
    expect(screen.getByText('Waking Up Cloud Cluster')).toBeInTheDocument();

    fireEvent.click(screen.getByLabelText('close'));
    expect(screen.queryByText('Waking Up Cloud Cluster')).not.toBeInTheDocument();
  });
});
