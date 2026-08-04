// Tests NavBar: theme toggle, login/logout affordances, admin link
// visibility, and the session-restore-from-stored-JWT effects.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import NavBar from './NavBar';
import authReducer from '../../features/auth';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useGetProfileQuery, useLoginMutation, useRegisterMutation } from '../../services/user';
import { useGetGenresQuery } from '../../services/TMDB';
import { clearAuthTokens } from '../../utils';
import { ColorModeContext } from '../../utils/ToggleColorMode';

// NavBar renders LoginDialog too, which needs the login/register hooks.
jest.mock('../../services/user', () => ({
  useGetProfileQuery: jest.fn(),
  useLoginMutation: jest.fn(),
  useRegisterMutation: jest.fn(),
}));

jest.mock('../../services/TMDB', () => ({
  useGetGenresQuery: jest.fn(),
}));

jest.mock('../../utils', () => ({
  ...jest.requireActual('../../utils'),
  clearAuthTokens: jest.fn(),
}));

const buildStore = (preloadedState) => configureStore({
  reducer: { user: authReducer, currentGenreOrCategory: genreOrCategoryReducer },
  preloadedState,
});

const renderNavBar = (options) => renderWithProviders(
  <ColorModeContext.Provider value={{ mode: 'light', toggleColorMode: jest.fn() }}>
    <NavBar />
  </ColorModeContext.Provider>,
  options,
);

describe('NavBar', () => {
  beforeEach(() => {
    localStorage.clear();
    jest.clearAllMocks();
    useGetGenresQuery.mockReturnValue({ data: undefined, isFetching: false });
    useGetProfileQuery.mockReturnValue({ data: undefined, error: undefined });
    useLoginMutation.mockReturnValue([jest.fn(), { isLoading: false, error: undefined }]);
    useRegisterMutation.mockReturnValue([jest.fn(), { isLoading: false, error: undefined }]);
  });

  it('shows a Login button when unauthenticated', () => {
    renderNavBar({ store: buildStore() });
    expect(screen.getByRole('button', { name: /login/i })).toBeInTheDocument();
  });

  it('opens the login dialog when Login is clicked', async () => {
    renderNavBar({ store: buildStore() });
    await userEvent.click(screen.getByRole('button', { name: /login/i }));

    expect(screen.getByText('Filmpire account')).toBeInTheDocument();
  });

  it('shows the "My Movies" link and avatar initial when authenticated', () => {
    const store = buildStore({ user: { user: { id: 1, username: 'liviu' }, isAuthenticated: true } });
    renderNavBar({ store });

    expect(screen.getByText('L')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /login/i })).not.toBeInTheDocument();
  });

  it('shows an Admin link only for users with the ADMIN role', () => {
    const store = buildStore({ user: { user: { id: 1, username: 'liviu', role: 'ADMIN' }, isAuthenticated: true } });
    renderNavBar({ store });

    expect(screen.getByRole('link', { name: /admin/i })).toHaveAttribute('href', '/admin');
  });

  it('does not show an Admin link for a non-admin user', () => {
    const store = buildStore({ user: { user: { id: 1, username: 'liviu', role: 'USER' }, isAuthenticated: true } });
    renderNavBar({ store });

    expect(screen.queryByRole('link', { name: /admin/i })).not.toBeInTheDocument();
  });

  it('restores the redux session from a stored JWT profile fetch', () => {
    localStorage.setItem('access_token', 'jwt');
    useGetProfileQuery.mockReturnValue({ data: { id: 1, username: 'liviu' }, error: undefined });
    const store = buildStore();
    renderNavBar({ store });

    expect(store.getState().user.isAuthenticated).toBe(true);
  });

  it('clears stored tokens when the restored-session profile fetch errors', () => {
    localStorage.setItem('access_token', 'stale-jwt');
    useGetProfileQuery.mockReturnValue({ data: undefined, error: { status: 401 } });
    renderNavBar({ store: buildStore() });

    expect(clearAuthTokens).toHaveBeenCalled();
  });

  it('skips the profile fetch entirely when there is no stored session and no redux user', () => {
    renderNavBar({ store: buildStore() });

    expect(useGetProfileQuery).toHaveBeenCalledWith(undefined, expect.objectContaining({ skip: true }));
  });
});
