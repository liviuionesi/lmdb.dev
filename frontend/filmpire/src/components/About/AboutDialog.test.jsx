import React from 'react';
import { screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import AboutDialog from './AboutDialog';
import { renderWithProviders } from '../../test-utils/render';

describe('AboutDialog & TMDB Attribution', () => {
  it('renders official TMDB attribution and legal disclaimer when open', () => {
    renderWithProviders(<AboutDialog open={true} onClose={() => {}} />);

    expect(screen.getByTestId('about-dialog')).toBeInTheDocument();
    expect(screen.getByText(/About LMDB & Data Attribution/i)).toBeInTheDocument();
    expect(screen.getByText(/Live Movies Database/i)).toBeInTheDocument();
    expect(screen.getByText(/The Movie Database \(TMDB\) API/i)).toBeInTheDocument();
    expect(
      screen.getByText(/"This product uses the TMDB API but is not endorsed or certified by TMDB\."/i)
    ).toBeInTheDocument();
    expect(screen.getByTestId('tmdb-logo')).toBeInTheDocument();
    expect(screen.getByTestId('lmdb-logo')).toBeInTheDocument();
  });

  it('renders nothing when open is false', () => {
    renderWithProviders(<AboutDialog open={false} onClose={() => {}} />);
    expect(screen.queryByTestId('about-dialog')).not.toBeInTheDocument();
  });

  it('invokes onClose when Close button is clicked', () => {
    const handleClose = vi.fn();
    renderWithProviders(<AboutDialog open={true} onClose={handleClose} />);

    fireEvent.click(screen.getByRole('button', { name: /close/i }));
    expect(handleClose).toHaveBeenCalledTimes(1);
  });
});
