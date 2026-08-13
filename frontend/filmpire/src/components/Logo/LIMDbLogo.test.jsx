import React from 'react';
import { screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import LIMDbLogo from './LIMDbLogo';
import { renderWithProviders } from '../../test-utils/render';

describe('LIMDbLogo Component', () => {
  it('renders full brandmark logo with LIVIU IONESI text', () => {
    renderWithProviders(<LIMDbLogo />);
    const logo = screen.getByTestId('limdb-logo');
    expect(logo).toBeInTheDocument();
    expect(screen.getByText('LIMDb')).toBeInTheDocument();
    expect(screen.getByText('LIVIU IONESI')).toBeInTheDocument();
    expect(screen.getByText('MOVIES DB')).toBeInTheDocument();
  });

  it('renders compact variant badge', () => {
    renderWithProviders(<LIMDbLogo variant="compact" />);
    const logo = screen.getByTestId('limdb-logo');
    expect(logo).toBeInTheDocument();
    expect(logo.getAttribute('aria-label')).toBe('LIMDb Logo');
  });
});
