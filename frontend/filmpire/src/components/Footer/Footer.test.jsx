import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import Footer from './Footer';
import { renderWithProviders } from '../../test-utils/render';
import * as apiUrlModule from '../../utils/apiUrl';

describe('Footer Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('renders default provider badge and copyright', () => {
    renderWithProviders(<Footer />);
    expect(screen.getByText(/Powered by Microsoft Azure/i)).toBeInTheDocument();
    expect(screen.getByText(/LMDB \(Live Movies Database\) • Multi-Cloud Architecture/i)).toBeInTheDocument();
  });

  it('updates provider label when actuator endpoint answers with a different cloud', async () => {
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

  it('renders TMDB attribution and opens About dialog when clicked', async () => {
    renderWithProviders(<Footer />);
    expect(screen.getByText(/Movie data and imagery provided by/i)).toBeInTheDocument();
    expect(screen.getByText(/This product uses the TMDB API but is not endorsed or certified by TMDB/i)).toBeInTheDocument();

    const creditsBtn = screen.getByTestId('about-credits-button');
    expect(creditsBtn).toBeInTheDocument();
  });
});
