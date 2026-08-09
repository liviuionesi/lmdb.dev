import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import DeployControl from './DeployControl';
import { theme } from '../../test-utils/render';

const renderDeployControl = (props = { apiUrl: 'http://localhost:8080' }) => render(
  <ThemeProvider theme={theme}>
    <DeployControl {...props} />
  </ThemeProvider>,
);

describe('DeployControl', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    global.fetch = vi.fn().mockImplementation((url) => {
      if (url.includes('/actuator/health')) {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ status: 'UP' }) });
      }
      if (url === '/api/dispatch') {
        return Promise.resolve({ ok: true, status: 200, json: () => Promise.resolve({ ok: true }) });
      }
      return Promise.resolve({ ok: true, status: 200 });
    });
  });

  afterEach(() => {
    delete global.fetch;
    vi.restoreAllMocks();
  });

  it('renders target selector options and control buttons in executive layout', async () => {
    renderDeployControl();

    expect(screen.getByText(/Live Infrastructure Orchestrator/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Select Azure AKS/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Select AWS EC2/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Select Local Tunnel/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Launch Azure AKS/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Tear Down/i })).toBeInTheDocument();
  });

  it('shows error if deploy is triggered and the admin passphrase prompt is cancelled', async () => {
    vi.spyOn(window, 'prompt').mockReturnValue(null);
    renderDeployControl();

    const deployBtn = screen.getByRole('button', { name: /Launch Azure AKS/i });
    fireEvent.click(deployBtn);

    await waitFor(() => {
      expect(screen.getByText(/Admin passphrase is required/i)).toBeInTheDocument();
    });
  });

  it('dispatches deployment workflow via the serverless proxy using the cached admin passphrase', async () => {
    localStorage.setItem('filmpire_admin_key', 'test-passphrase');
    renderDeployControl();

    const deployBtn = screen.getByRole('button', { name: /Launch Azure AKS/i });
    fireEvent.click(deployBtn);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        '/api/dispatch',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'x-admin-key': 'test-passphrase',
          }),
          body: JSON.stringify({ workflow: 'deploy.yml', inputs: { cloud: 'azure' } }),
        }),
      );
      expect(screen.getByText(/Automated deployment dispatched for AZURE/i)).toBeInTheDocument();
    });
  });

  it('dispatches destroy workflow when teardown button is clicked', async () => {
    localStorage.setItem('filmpire_admin_key', 'test-passphrase');
    renderDeployControl();

    const destroyBtn = screen.getByRole('button', { name: /Tear Down/i });
    fireEvent.click(destroyBtn);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        '/api/dispatch',
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            'x-admin-key': 'test-passphrase',
          }),
          body: JSON.stringify({ workflow: 'destroy.yml', inputs: { cloud: 'azure' } }),
        }),
      );
      expect(screen.getByText(/Teardown dispatched for AZURE/i)).toBeInTheDocument();
    });
  });

  it('switches cloud target when AWS button is selected', async () => {
    renderDeployControl();

    const awsBtn = screen.getByRole('button', { name: /Select AWS EC2/i });
    fireEvent.click(awsBtn);

    expect(screen.getByRole('button', { name: /Launch AWS EC2/i })).toBeInTheDocument();
  });

  it('switches to local mode and displays connect button', async () => {
    renderDeployControl();

    const localBtn = screen.getByRole('button', { name: /Select Local Tunnel/i });
    fireEvent.click(localBtn);

    expect(screen.getByRole('button', { name: /Connect Local Backend/i })).toBeInTheDocument();
  });
});
