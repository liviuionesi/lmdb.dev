// Tests Profile: the empty state, rendering favorites/watchlist, and that
// logging out clears auth state and redirects home.
import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Profile from './Profile';
import authReducer from '../../features/auth';
import { renderWithProviders } from '../../test-utils/render';
import { useGetFavoritesQuery, useGetWatchlistQuery, useLogoutMutation } from '../../services/user';
import { useGetMovieQuery } from '../../services/TMDB';

jest.mock('../../services/user', () => ({
  useGetFavoritesQuery: jest.fn(),
  useGetWatchlistQuery: jest.fn(),
  useLogoutMutation: jest.fn(),
}));

// RatedCards -> Movie needs TMDB's getMovie hook; RatedCards imports it from
// services/TMDB, not services/user, so mock that module too.
jest.mock('../../services/TMDB', () => ({
  useGetMovieQuery: jest.fn(),
}));

const buildStore = (authenticated) => configureStore({
  reducer: { user: authReducer },
  preloadedState: authenticated ? { user: { user: { id: 1, username: 'liviu' }, isAuthenticated: true } } : undefined,
});

describe('Profile', () => {
  beforeEach(() => {
    useLogoutMutation.mockReturnValue([jest.fn().mockResolvedValue({})]);
    localStorage.clear();
  });

  it('shows a placeholder message when there are no favorites or watchlist entries', () => {
    useGetFavoritesQuery.mockReturnValue({ data: [] });
    useGetWatchlistQuery.mockReturnValue({ data: [] });
    renderWithProviders(<Profile />, { store: buildStore(true) });

    expect(screen.getByText(/Add favorites or watchlist some movies/)).toBeInTheDocument();
  });

  it('renders RatedCards sections when favorites/watchlist have entries', () => {
    useGetFavoritesQuery.mockReturnValue({ data: [{ movieId: 1 }] });
    useGetWatchlistQuery.mockReturnValue({ data: [{ movieId: 2 }] });
    useGetMovieQuery.mockReturnValue({ data: undefined, isFetching: true });
    renderWithProviders(<Profile />, { store: buildStore(true) });

    expect(screen.getByText('Favorite Movies')).toBeInTheDocument();
    expect(screen.getByText('Watchlist')).toBeInTheDocument();
  });

  it('logs out: revokes the session, clears local tokens/redux state, and redirects home', async () => {
    localStorage.setItem('access_token', 'jwt');
    const logout = jest.fn().mockResolvedValue({});
    useLogoutMutation.mockReturnValue([logout]);
    useGetFavoritesQuery.mockReturnValue({ data: [] });
    useGetWatchlistQuery.mockReturnValue({ data: [] });

    delete window.location;
    window.location = { href: '' };

    renderWithProviders(<Profile />, { store: buildStore(true) });

    await userEvent.click(screen.getByRole('button', { name: /logout/i }));

    expect(logout).toHaveBeenCalled();
    await waitFor(() => expect(localStorage.getItem('access_token')).toBeNull());
  });
});
