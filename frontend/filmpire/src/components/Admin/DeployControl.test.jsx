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

  it('renders target selector options and control buttons', async () => {
    renderDeployControl();

    expect(screen.getByText(/1-Click Cloud Deployment & Teardown Control/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/Azure AKS/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/AWS EC2/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Deploy Backend to Azure AKS/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Tear Down Backend/i })).toBeInTheDocument();
  });

  it('shows an error message if deploy is clicked without a GitHub token', async () => {
    renderDeployControl();

    const deployBtn = screen.getByRole('button', { name: /Deploy Backend to Azure AKS/i });
    fireEvent.click(deployBtn);

    await waitFor(() => {
      expect(screen.getByText(/GitHub Personal Access Token is required/i)).toBeInTheDocument();
    });
  });

  it('saves token to localStorage and dispatches deployment workflow', async () => {
    renderDeployControl();

    const tokenInput = screen.getByLabelText(/GitHub Personal Access Token/i);
    fireEvent.change(tokenInput, { target: { value: 'ghp_testtoken123' } });

    expect(localStorage.getItem('filmpire_gh_token')).toBe('ghp_testtoken123');

    const deployBtn = screen.getByRole('button', { name: /Deploy Backend to Azure AKS/i });
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
      expect(screen.getByText(/Deployment workflow dispatched on GitHub/i)).toBeInTheDocument();
    });
  });

  it('dispatches destroy workflow when teardown button is clicked', async () => {
    localStorage.setItem('filmpire_gh_token', 'ghp_testtoken123');
    renderDeployControl();

    const destroyBtn = screen.getByRole('button', { name: /Tear Down Backend/i });
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
      expect(screen.getByText(/Teardown workflow dispatched on GitHub/i)).toBeInTheDocument();
    });
  });

  it('switches cloud target when AWS radio button is selected', async () => {
    renderDeployControl();

    const awsRadio = screen.getByLabelText(/AWS EC2/i);
    fireEvent.click(awsRadio);

    expect(screen.getByRole('button', { name: /Deploy Backend to AWS k3s/i })).toBeInTheDocument();
  });
});
