import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import Footer from './Footer';
import { renderWithProviders } from '../../test-utils/render';
import * as apiUrlModule from '../../utils/apiUrl';

describe('Footer Component (Static Telemetry)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders default static provider, uptime spec, and auto-sleep policy', () => {
    renderWithProviders(<Footer />);
    expect(screen.getByText(/Powered by Microsoft Azure/i)).toBeInTheDocument();
    expect(screen.getByText(/Uptime: On-Demand/i)).toBeInTheDocument();
    expect(screen.getByText(/Auto-sleep: 1h idle/i)).toBeInTheDocument();
    expect(screen.getByText(/Filmpire Microservices • Multi-Cloud Architecture/i)).toBeInTheDocument();
  });

  it('updates provider label once on mount when actuator endpoint answers', async () => {
    vi.spyOn(apiUrlModule, 'resolveApiUrl').mockResolvedValue('http://localhost:8080');

    const mockTelemetry = {
      status: 'UP',
      cloudProvider: 'aws',
      cloudProviderLabel: 'Amazon Web Services (k3s)',
    };

    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => mockTelemetry,
    });

    renderWithProviders(<Footer />);

    await waitFor(() => {
      expect(screen.getByText(/Powered by Amazon Web Services \(k3s\)/i)).toBeInTheDocument();
    });
  });

  it('does not render modal or interactive switcher elements', () => {
    renderWithProviders(<Footer />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(screen.queryByText('Backend Cloud Provider')).not.toBeInTheDocument();
    expect(screen.queryByText('Apply Target')).not.toBeInTheDocument();
  });
});
