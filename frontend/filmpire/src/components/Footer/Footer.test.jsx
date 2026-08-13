import React from 'react';
import { screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import Footer, { formatDuration } from './Footer';
import { renderWithProviders } from '../../test-utils/render';
import * as apiUrlModule from '../../utils/apiUrl';

describe('Footer Component', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('formatDuration', () => {
    it('formats durations accurately', () => {
      expect(formatDuration(0)).toBe('0m');
      expect(formatDuration(45)).toBe('45s');
      expect(formatDuration(120)).toBe('2m');
      expect(formatDuration(3600)).toBe('1h');
      expect(formatDuration(3720)).toBe('1h 2m');
      expect(formatDuration(90000)).toBe('1d 1h');
      expect(formatDuration(null)).toBe('0s');
      expect(formatDuration(-10)).toBe('0s');
    });
  });

  describe('Rendering and Telemetry', () => {
    it('renders provider badge and copyright', () => {
      renderWithProviders(<Footer />);
      expect(screen.getByText(/Powered by Microsoft Azure/i)).toBeInTheDocument();
      expect(screen.getByText(/Filmpire Microservices • Multi-Cloud Resilient Architecture/i)).toBeInTheDocument();
    });

    it('fetches telemetry and displays live uptime and time to sleep', async () => {
      vi.spyOn(apiUrlModule, 'resolveApiUrl').mockResolvedValue('http://localhost:8080');

      const mockTelemetry = {
        status: 'UP',
        cloudProvider: 'azure',
        cloudProviderLabel: 'Microsoft Azure (AKS)',
        uptimeSeconds: 7200,
        idleSeconds: 300,
        secondsUntilAutoStop: 3300,
        idleThresholdSeconds: 3600,
      };

      global.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => mockTelemetry,
      });

      renderWithProviders(<Footer />);

      await waitFor(() => {
        expect(screen.getByText(/Uptime: 2h/i)).toBeInTheDocument();
        expect(screen.getByText(/Auto-sleep: 55m/i)).toBeInTheDocument();
      });
    });

    it('opens backend target dialog and allows target switching', async () => {
      renderWithProviders(<Footer />);

      const badge = screen.getByRole('button', { name: /Backend status/i });
      fireEvent.click(badge);

      expect(screen.getByText('Backend Cloud Provider')).toBeInTheDocument();
      expect(screen.getByText('Apply Target')).toBeInTheDocument();

      fireEvent.click(screen.getByText('Cancel'));
      await waitFor(() => {
        expect(screen.queryByText('Backend Cloud Provider')).not.toBeInTheDocument();
      });
    });
  });
});
