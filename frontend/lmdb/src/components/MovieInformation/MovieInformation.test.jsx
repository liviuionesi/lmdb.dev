// Tests MovieInformation: loading/error states, core detail rendering, the
// favorite/watchlist toggle mutations, the trailer modal fallback, and
// resilience to sparse movie data (missing spoken_languages).
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import MovieInformation from './MovieInformation';
import authReducer from '../../features/auth';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useGetMovieQuery, useGetRecommendationsQuery } from '../../services/TMDB';
import {
  useGetFavoritesQuery,
  useGetWatchlistQuery,
  useAddFavoriteMutation,
  useRemoveFavoriteMutation,
  useAddToWatchlistMutation,
  useRemoveFromWatchlistMutation,
} from '../../services/user';
import { useGetMediaForEntityQuery, useUploadMediaMutation, getMediaUrl } from '../../services/media';

vi.mock('../../services/TMDB', () => ({
  useGetMovieQuery: vi.fn(),
  useGetRecommendationsQuery: vi.fn(),
}));

vi.mock('../../services/user', () => ({
  useGetFavoritesQuery: vi.fn(),
  useGetWatchlistQuery: vi.fn(),
  useAddFavoriteMutation: vi.fn(),
  useRemoveFavoriteMutation: vi.fn(),
  useAddToWatchlistMutation: vi.fn(),
  useRemoveFromWatchlistMutation: vi.fn(),
}));

vi.mock('../../services/media', () => ({
  useGetMediaForEntityQuery: vi.fn(),
  useUploadMediaMutation: vi.fn(),
  getMediaUrl: vi.fn(),
}));

const buildStore = (isAuthenticated = false) => configureStore({
  reducer: { user: authReducer, currentGenreOrCategory: genreOrCategoryReducer },
  preloadedState: { user: { user: {}, isAuthenticated } },
});

const fullMovie = {
  id: 550,
  title: 'Fight Club',
  release_date: '1999-10-15',
  tagline: 'Mischief. Mayhem. Soap.',
  vote_average: 8.4,
  runtime: 139,
  spoken_languages: [{ name: 'English' }],
  genres: [{ id: 18, name: 'Drama' }],
  overview: 'An insomniac office worker...',
  homepage: 'https://example.com',
  imdb_id: 'tt0137523',
  poster_path: '/poster.jpg',
  videos: { results: [] },
  credits: { cast: [{ id: 1, name: 'Brad Pitt', character: 'Tyler Durden', profile_path: '/bp.jpg' }] },
};

describe('MovieInformation', () => {
  beforeEach(() => {
    useAddFavoriteMutation.mockReturnValue([vi.fn()]);
    useRemoveFavoriteMutation.mockReturnValue([vi.fn()]);
    useAddToWatchlistMutation.mockReturnValue([vi.fn()]);
    useRemoveFromWatchlistMutation.mockReturnValue([vi.fn()]);
    useGetFavoritesQuery.mockReturnValue({ data: undefined });
    useGetWatchlistQuery.mockReturnValue({ data: undefined });
    useGetRecommendationsQuery.mockReturnValue({ data: undefined });
    useGetMediaForEntityQuery.mockReturnValue({ data: [], refetch: vi.fn(), isFetching: false });
    useUploadMediaMutation.mockReturnValue([vi.fn().mockReturnValue({ unwrap: vi.fn().mockResolvedValue({}) }), { isLoading: false }]);
    getMediaUrl.mockImplementation((url) => url);
  });

  it('shows a spinner while fetching', () => {
    useGetMovieQuery.mockReturnValue({ data: undefined, isFetching: true, error: undefined });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore() });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows a "go back" link when the movie request errors', () => {
    useGetMovieQuery.mockReturnValue({ data: undefined, isFetching: false, error: { status: 500 } });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore() });

    expect(screen.getByText(/Something has gone wrong/)).toBeInTheDocument();
  });

  it('renders the movie title, cast, and "no trailer" fallback in the modal', async () => {
    useGetMovieQuery.mockReturnValue({ data: fullMovie, isFetching: false, error: undefined });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore() });

    expect(screen.getByText('Fight Club (1999)')).toBeInTheDocument();
    expect(screen.getByText('Brad Pitt')).toBeInTheDocument();
    expect(screen.getByText('Sorry, nothing was found.')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('link', { name: /trailer/i }));
    expect(screen.getByText('No trailer available.')).toBeInTheDocument();
  });

  it('renders an iframe trailer when videos are available', async () => {
    useGetMovieQuery.mockReturnValue({
      data: { ...fullMovie, videos: { results: [{ key: 'abc123' }] } },
      isFetching: false,
      error: undefined,
    });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore() });

    await userEvent.click(screen.getByRole('link', { name: /trailer/i }));

    expect(screen.getByTitle('Trailer')).toHaveAttribute('src', 'https://www.youtube.com/embed/abc123');
  });

  it('shows recommendations when provided', () => {
    useGetMovieQuery.mockReturnValue({ data: fullMovie, isFetching: false, error: undefined });
    useGetRecommendationsQuery.mockReturnValue({
      data: { results: [{ id: 2, title: 'Se7en', poster_path: '/s.jpg', vote_average: 7 }] },
    });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore() });

    expect(screen.getByText('Se7en')).toBeInTheDocument();
  });

  it('adds the movie to favorites when unauthenticated-favorited and the button is clicked', async () => {
    const addFavorite = vi.fn();
    useAddFavoriteMutation.mockReturnValue([addFavorite]);
    useGetMovieQuery.mockReturnValue({ data: fullMovie, isFetching: false, error: undefined });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore(true) });

    await userEvent.click(screen.getByRole('button', { name: /^favorite$/i }));

    expect(addFavorite).toHaveBeenCalledWith('550');
  });

  it('removes the movie from favorites when it is already favorited', async () => {
    const removeFavorite = vi.fn();
    useRemoveFavoriteMutation.mockReturnValue([removeFavorite]);
    useGetFavoritesQuery.mockReturnValue({ data: [{ movieId: 550 }] });
    useGetMovieQuery.mockReturnValue({ data: fullMovie, isFetching: false, error: undefined });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore(true) });

    expect(screen.getByRole('button', { name: /unfavorite/i })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /unfavorite/i }));

    expect(removeFavorite).toHaveBeenCalledWith('550');
  });

  it('toggles the watchlist mutation depending on current watchlist state', async () => {
    const addToWatchlist = vi.fn();
    useAddToWatchlistMutation.mockReturnValue([addToWatchlist]);
    useGetMovieQuery.mockReturnValue({ data: fullMovie, isFetching: false, error: undefined });
    renderWithProviders(<MovieInformation />, { route: '/movie/550', path: '/movie/:id', store: buildStore(true) });

    await userEvent.click(screen.getByRole('button', { name: /watchlist/i }));

    expect(addToWatchlist).toHaveBeenCalledWith('550');
  });

  /**
   * Regression check for the "movie-detail crash on list-sourced movies" class of bug (#34,
   * commit 2deaeb2): a movie can reach this page with a sparse `spoken_languages` array (or none
   * at all) when the backend hasn't populated it, since every other detail-only field is already
   * read with optional chaining. `data?.spoken_languages[0].name` was not — indexing an empty
   * array yields `undefined`, and reading `.name` off that throws, crashing the whole page instead
   * of just omitting the language.
   */
  it('renders without crashing when spoken_languages is empty', () => {
    useGetMovieQuery.mockReturnValue({
      data: { ...fullMovie, spoken_languages: [] },
      isFetching: false,
      error: undefined,
    });

    expect(() => renderWithProviders(<MovieInformation />, {
      route: '/movie/550', path: '/movie/:id', store: buildStore(),
    })).not.toThrow();
    expect(screen.getByText('Fight Club (1999)')).toBeInTheDocument();
  });
});
