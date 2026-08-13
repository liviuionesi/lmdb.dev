import React from 'react';
import { screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import LMDBLogo from './LMDBLogo';
import { renderWithProviders } from '../../test-utils/render';

describe('LMDBLogo Component', () => {
  it('renders full brandmark logo with LIVE MOVIES DB text', () => {
    renderWithProviders(<LMDBLogo />);
    const logo = screen.getByTestId('lmdb-logo');
    expect(logo).toBeInTheDocument();
    expect(screen.getByText('LMDB')).toBeInTheDocument();
    expect(screen.getByText('LIVE')).toBeInTheDocument();
    expect(screen.getByText('MOVIES DB')).toBeInTheDocument();
  });

  it('renders compact variant badge', () => {
    renderWithProviders(<LMDBLogo variant="compact" />);
    const logo = screen.getByTestId('lmdb-logo');
    expect(logo).toBeInTheDocument();
    expect(logo.getAttribute('aria-label')).toBe('LMDB Logo');
  });
});
