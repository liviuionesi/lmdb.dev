// Tests Movies: loading spinner, empty-results message, and the happy path
// (featured movie + list + pagination) wired off useGetMoviesQuery.
import React from 'react';
import { screen } from '@testing-library/react';
import { configureStore } from '@reduxjs/toolkit';

import Movies from './Movies';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useGetMoviesQuery } from '../../services/TMDB';

jest.mock('../../services/TMDB', () => ({
  useGetMoviesQuery: jest.fn(),
}));

const buildStore = () => configureStore({ reducer: { currentGenreOrCategory: genreOrCategoryReducer } });

describe('Movies', () => {
  it('shows a spinner while fetching', () => {
    useGetMoviesQuery.mockReturnValue({ data: undefined, error: undefined, isFetching: true });
    renderWithProviders(<Movies />, { store: buildStore() });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows a "no movies" message when the results are empty', () => {
    useGetMoviesQuery.mockReturnValue({ data: { results: [], total_pages: 0 }, error: undefined, isFetching: false });
    renderWithProviders(<Movies />, { store: buildStore() });

    expect(screen.getByText(/No movies that match that name/)).toBeInTheDocument();
  });

  it('renders the featured movie, list, and pagination once movies are loaded', () => {
    const results = Array.from({ length: 3 }, (_, i) => ({
      id: i, title: `Movie ${i}`, poster_path: '/p.jpg', vote_average: 5, overview: 'x', backdrop_path: '/b.jpg',
    }));
    useGetMoviesQuery.mockReturnValue({ data: { results, total_pages: 5 }, error: undefined, isFetching: false });
    renderWithProviders(<Movies />, { store: buildStore() });

    // Movie 0 is the featured movie; the list excludes it (excludeFirst).
    expect(screen.getAllByText('Movie 0')).toHaveLength(1);
    expect(screen.getByText('Movie 1')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument(); // current page
  });
});
