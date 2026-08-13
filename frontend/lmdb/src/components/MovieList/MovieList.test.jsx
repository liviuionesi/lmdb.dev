// Tests MovieList's slicing behavior: numberOfMovies caps the list and
// excludeFirst skips the featured movie already shown elsewhere.
import React from 'react';
import { screen } from '@testing-library/react';

import MovieList from './MovieList';
import { renderWithProviders } from '../../test-utils/render';

const buildMovies = (count) => ({
  results: Array.from({ length: count }, (_, i) => ({
    id: i,
    title: `Movie ${i}`,
    poster_path: '/p.jpg',
    vote_average: 5,
  })),
});

describe('MovieList', () => {
  it('renders every movie up to numberOfMovies', () => {
    renderWithProviders(<MovieList movies={buildMovies(5)} numberOfMovies={3} excludeFirst={false} />);

    expect(screen.getByText('Movie 0')).toBeInTheDocument();
    expect(screen.getByText('Movie 1')).toBeInTheDocument();
    expect(screen.getByText('Movie 2')).toBeInTheDocument();
    expect(screen.queryByText('Movie 3')).not.toBeInTheDocument();
  });

  it('skips the first movie when excludeFirst is set (it is already shown as the featured movie)', () => {
    renderWithProviders(<MovieList movies={buildMovies(4)} numberOfMovies={4} excludeFirst />);

    expect(screen.queryByText('Movie 0')).not.toBeInTheDocument();
    expect(screen.getByText('Movie 1')).toBeInTheDocument();
    expect(screen.getByText('Movie 3')).toBeInTheDocument();
  });

  it('renders nothing when the results list is empty', () => {
    renderWithProviders(<MovieList movies={buildMovies(0)} numberOfMovies={10} excludeFirst={false} />);

    expect(screen.queryAllByRole('link')).toHaveLength(0);
  });
});
