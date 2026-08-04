// Tests FeaturedMovie's rendering of the hero card and its null-movie guard.
import React from 'react';
import { screen } from '@testing-library/react';

import FeaturedMovie from './FeaturedMovie';
import { renderWithProviders } from '../../test-utils/render';

describe('FeaturedMovie', () => {
  it('renders nothing when no movie is given', () => {
    const { container } = renderWithProviders(<FeaturedMovie movie={undefined} />);
    expect(container).toBeEmptyDOMElement();
  });

  it('renders the title, overview, and a link to the movie page', () => {
    const movie = { id: 42, title: 'Interstellar', overview: 'A space odyssey.', backdrop_path: '/bg.jpg' };
    renderWithProviders(<FeaturedMovie movie={movie} />);

    expect(screen.getByText('Interstellar')).toBeInTheDocument();
    expect(screen.getByText('A space odyssey.')).toBeInTheDocument();
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movie/42');
  });
});
