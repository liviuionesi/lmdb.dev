// Tests the Movie poster card: title/rating rendering, the fallback poster
// image, and the link it points to.
import React from 'react';
import { screen } from '@testing-library/react';

import Movie from './Movie';
import { renderWithProviders } from '../../test-utils/render';

describe('Movie', () => {
  it('renders the title, poster image, and a link to the movie page', () => {
    const movie = { id: 550, title: 'Fight Club', poster_path: '/poster.jpg', vote_average: 8.4 };
    renderWithProviders(<Movie movie={movie} i={0} />);

    expect(screen.getByText('Fight Club')).toBeInTheDocument();
    const image = screen.getByAltText('Fight Club');
    expect(image).toHaveAttribute('src', 'https://image.tmdb.org/t/p/w500//poster.jpg');
    expect(screen.getByRole('link')).toHaveAttribute('href', '/movie/550');
  });

  it('falls back to a placeholder image when the movie has no poster_path', () => {
    const movie = { id: 1, title: 'No Poster', poster_path: null, vote_average: 5 };
    renderWithProviders(<Movie movie={movie} i={0} />);

    expect(screen.getByAltText('No Poster')).toHaveAttribute('src', 'https://www.fillmurray.com/200/300');
  });

  it('shows the vote average out of 10 in the rating tooltip', () => {
    const movie = { id: 2, title: 'Rated', poster_path: '/x.jpg', vote_average: 7.2 };
    renderWithProviders(<Movie movie={movie} i={0} />);

    // MUI's Tooltip exposes its `title` text via aria-label on the wrapped
    // child rather than a plain HTML `title` attribute.
    expect(screen.getByLabelText('7.2 / 10')).toBeInTheDocument();
  });

  it('renders an optional caption (#221: ai-service\'s recommendation reason)', () => {
    const movie = { id: 3, title: 'Explained', poster_path: '/x.jpg', vote_average: 6 };
    renderWithProviders(<Movie movie={movie} i={0} caption="Because you liked Fight Club" />);

    expect(screen.getByText('Because you liked Fight Club')).toBeInTheDocument();
  });

  it('renders no caption text when none is passed (every other caller)', () => {
    const movie = { id: 4, title: 'Plain', poster_path: '/x.jpg', vote_average: 6 };
    const { container } = renderWithProviders(<Movie movie={movie} i={0} />);

    // The caption is the only `body2` (rendered as a `<p>`) text this component ever shows —
    // MUI's `variant="h5"` title renders as an `<h5>`, not a `<p>`.
    expect(container.querySelectorAll('p').length).toBe(0);
  });
});
