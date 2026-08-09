// Tests RatedCards: renders one Movie per resolved id, and skips ids whose
// movie details are still loading (the FavoriteMovie -> null-while-fetching path).
import React from 'react';
import { screen } from '@testing-library/react';

import RatedCards from './RatedCards';
import { renderWithProviders } from '../../test-utils/render';
import { useGetMovieQuery } from '../../services/TMDB';

vi.mock('../../services/TMDB', () => ({
  useGetMovieQuery: vi.fn(),
}));

describe('RatedCards', () => {
  it('renders the section title even with no movie ids', () => {
    renderWithProviders(<RatedCards title="Favorite Movies" movieIds={[]} />);
    expect(screen.getByText('Favorite Movies')).toBeInTheDocument();
  });

  it('renders a Movie card for each resolved movie id', () => {
    useGetMovieQuery.mockImplementation((movieId) => ({
      data: { id: movieId, title: `Movie ${movieId}`, poster_path: '/p.jpg', vote_average: 5 },
      isFetching: false,
    }));
    renderWithProviders(<RatedCards title="Watchlist" movieIds={[1, 2]} />);

    expect(screen.getByText('Movie 1')).toBeInTheDocument();
    expect(screen.getByText('Movie 2')).toBeInTheDocument();
  });

  it('renders nothing for a movie id that is still fetching', () => {
    useGetMovieQuery.mockReturnValue({ data: undefined, isFetching: true });
    renderWithProviders(<RatedCards title="Watchlist" movieIds={[99]} />);

    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });
});
