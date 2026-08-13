import React from 'react';
import { screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import About from './About';
import { renderWithProviders } from '../../test-utils/render';

describe('About Page Component', () => {
  it('renders brand identity, creator portfolio links, and TMDB attribution', () => {
    renderWithProviders(<About />);

    expect(screen.getByTestId('about-page')).toBeInTheDocument();
    expect(screen.getByText('Live Movies Database')).toBeInTheDocument();
    expect(screen.getByText(/Architect & Engineering: Liviu Ionesi/i)).toBeInTheDocument();

    // Verify creator links
    const websiteLink = screen.getByTestId('creator-website-link');
    expect(websiteLink).toHaveAttribute('href', 'https://liviuionesi.com');

    const linkedInLink = screen.getByTestId('creator-linkedin-link');
    expect(linkedInLink).toHaveAttribute('href', 'https://www.linkedin.com/in/liviu-ionesi/');

    const githubLink = screen.getByTestId('project-github-link');
    expect(githubLink).toHaveAttribute('href', 'https://github.com/pehlivanu/filmpire-microservices');

    // Verify TMDB legal attribution
    expect(screen.getByText(/The Movie Database \(TMDB\) public API/i)).toBeInTheDocument();
    expect(
      screen.getByText(/"This product uses the TMDB API but is not endorsed or certified by TMDB\."/i)
    ).toBeInTheDocument();
    expect(screen.getByTestId('tmdb-logo')).toBeInTheDocument();
    expect(screen.getByTestId('lmdb-logo')).toBeInTheDocument();
  });
});
