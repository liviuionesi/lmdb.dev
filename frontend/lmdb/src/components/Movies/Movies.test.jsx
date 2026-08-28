// Tests Movies: loading spinner, empty-results message, and the happy path
// (featured movie + list + pagination) wired off useGetMoviesQuery — plus, since #204, the
// parallel AI-search rendering path driven by currentGenreOrCategory's aiSearch* state instead.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Movies from './Movies';
import genreOrCategoryReducer, { aiSearchStarted, aiSearchSucceeded, aiSearchFailed } from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useGetMoviesQuery } from '../../services/TMDB';

vi.mock('../../services/TMDB', () => ({
  useGetMoviesQuery: vi.fn(),
}));

const buildStore = () => configureStore({ reducer: { currentGenreOrCategory: genreOrCategoryReducer } });

describe('Movies', () => {
  beforeEach(() => {
    // Default: not in AI search mode, so the old query path is exercised unless a test opts in.
    useGetMoviesQuery.mockReturnValue({ data: undefined, error: undefined, isFetching: false });
  });

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

  it('skips the old movie-service query entirely while an AI search is active (#204 AC1)', () => {
    const store = buildStore();
    store.dispatch(aiSearchStarted('batman'));
    renderWithProviders(<Movies />, { store });

    expect(useGetMoviesQuery).toHaveBeenCalledWith(expect.anything(), { skip: true });
  });

  it('shows the spinner while an AI search is loading, not the old query result', () => {
    // A stray old-query result must not leak through while AI search "loading" is the true state.
    useGetMoviesQuery.mockReturnValue({ data: { results: [{ id: 1, title: 'Stale' }], total_pages: 1 }, error: undefined, isFetching: false });
    const store = buildStore();
    store.dispatch(aiSearchStarted('batman'));
    renderWithProviders(<Movies />, { store });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    expect(screen.queryByText('Stale')).not.toBeInTheDocument();
  });

  it('shows an error message, not a blank screen, when the AI search fails (#204 AC3)', () => {
    const store = buildStore();
    store.dispatch(aiSearchStarted('batman'));
    store.dispatch(aiSearchFailed());
    renderWithProviders(<Movies />, { store });

    expect(screen.getByText('An error has occurred.')).toBeInTheDocument();
  });

  it('renders AI search results through the same featured/list/pagination UI as a normal query', () => {
    const results = Array.from({ length: 3 }, (_, i) => ({
      id: i, title: `AI Movie ${i}`, poster_path: '/p.jpg', vote_average: 5, overview: 'x', backdrop_path: '/b.jpg',
    }));
    const store = buildStore();
    store.dispatch(aiSearchStarted('batman'));
    store.dispatch(aiSearchSucceeded({ results }));
    renderWithProviders(<Movies />, { store });

    expect(screen.getAllByText('AI Movie 0')).toHaveLength(1);
    expect(screen.getByText('AI Movie 1')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('paginates a long AI result set client-side (20 per page) rather than showing it all at once', () => {
    const results = Array.from({ length: 25 }, (_, i) => ({
      id: i, title: `AI Movie ${i}`, poster_path: '/p.jpg', vote_average: 5, overview: 'x', backdrop_path: '/b.jpg',
    }));
    const store = buildStore();
    store.dispatch(aiSearchStarted('batman'));
    store.dispatch(aiSearchSucceeded({ results }));
    renderWithProviders(<Movies />, { store });

    // Page 1 is sliced from movies 0-19 (indices 0-19); MovieList's own display cap
    // (numberOfMovies, separate from AI_SEARCH_PAGE_SIZE) doesn't render every one of those 20 on
    // screen at once, so assert on an index safely within that cap rather than the page boundary
    // itself. Movie 20 (page 2's first item) is the one this test actually cares about proving
    // absent — it's outside the page-1 slice entirely, not just outside the display cap.
    expect(screen.getByText('AI Movie 5')).toBeInTheDocument();
    expect(screen.queryByText('AI Movie 20')).not.toBeInTheDocument();
  });

  it('lands the page-1/page-2 split exactly at index 19/20 — proven by actually paging forward,'
      + ' not just by absence on page 1 (which MovieList\'s own display cap could mask)', async () => {
    const results = Array.from({ length: 25 }, (_, i) => ({
      id: i, title: `AI Movie ${i}`, poster_path: '/p.jpg', vote_average: 5, overview: 'x', backdrop_path: '/b.jpg',
    }));
    const store = buildStore();
    store.dispatch(aiSearchStarted('batman'));
    store.dispatch(aiSearchSucceeded({ results }));
    renderWithProviders(<Movies />, { store });

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    // Movie 20 (page 2's first item, index 20) is the featured movie on page 2 — proves the split
    // is exactly at 19/20, not off by one in either direction. Movie 19 (page 1's last item) must
    // be gone from the screen entirely once page 2 is showing.
    expect(screen.getAllByText('AI Movie 20')).toHaveLength(1);
    expect(screen.queryByText('AI Movie 19')).not.toBeInTheDocument();
  });
});
