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
      if (url.includes('/actions/workflows/')) {
        return Promise.resolve({ ok: true, status: 204 });
      }
      return Promise.resolve({ ok: true, status: 200 });
    });
  });

  afterEach(() => {
    delete global.fetch;
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

  it('shows error if deploy is triggered without a token configured', async () => {
    renderDeployControl();

    const deployBtn = screen.getByRole('button', { name: /Launch Azure AKS/i });
    fireEvent.click(deployBtn);

    await waitFor(() => {
      expect(screen.getByText(/GitHub Personal Access Token or VITE_GITHUB_TOKEN/i)).toBeInTheDocument();
    });
  });

  it('dispatches deployment workflow when token is present in localStorage or environment', async () => {
    localStorage.setItem('filmpire_gh_token', 'ghp_testtoken123');
    renderDeployControl();

    const deployBtn = screen.getByRole('button', { name: /Launch Azure AKS/i });
    fireEvent.click(deployBtn);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/actions/workflows/deploy.yml/dispatches'),
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            Authorization: 'Bearer ghp_testtoken123',
          }),
        }),
      );
      expect(screen.getByText(/Automated deployment dispatched for AZURE/i)).toBeInTheDocument();
    });
  });

  it('dispatches destroy workflow when teardown button is clicked', async () => {
    localStorage.setItem('filmpire_gh_token', 'ghp_testtoken123');
    renderDeployControl();

    const destroyBtn = screen.getByRole('button', { name: /Tear Down/i });
    fireEvent.click(destroyBtn);

    await waitFor(() => {
      expect(global.fetch).toHaveBeenCalledWith(
        expect.stringContaining('/actions/workflows/destroy.yml/dispatches'),
        expect.objectContaining({
          method: 'POST',
          headers: expect.objectContaining({
            Authorization: 'Bearer ghp_testtoken123',
          }),
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
