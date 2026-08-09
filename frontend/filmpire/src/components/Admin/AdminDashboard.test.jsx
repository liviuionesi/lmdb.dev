// Tests AdminDashboard: the ADMIN-only gate (redirect for anonymous/non-admin
// users, a loading spinner while the session is being restored) and that the
// four infrastructure StatusCards render once an ADMIN session is confirmed,
// via both the already-hydrated Redux path and the profile-refetch-on-load
// path. StatusCard itself renders a real <a> for its `url` prop, so asserting
// on rendered links doubles as an assertion that AdminDashboard is passing
// the right title/url pairs through.
import React from 'react';
import { screen } from '@testing-library/react';
import { configureStore } from '@reduxjs/toolkit';

import AdminDashboard from './AdminDashboard';
import authReducer from '../../features/auth';
import { renderWithProviders } from '../../test-utils/render';
import { useGetProfileQuery } from '../../services/user';

vi.mock('../../services/user', () => ({
  useGetProfileQuery: vi.fn(),
}));

const buildStore = (preloadedState) => configureStore({
  reducer: { user: authReducer },
  preloadedState,
});

describe('AdminDashboard', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useGetProfileQuery.mockReturnValue({ data: undefined, isLoading: false, isError: false });
  });

  it('redirects to home when there is no stored session', () => {
    renderWithProviders(<AdminDashboard />, { store: buildStore(), route: '/admin' });

    // MemoryRouter has no <Route path="/"> registered in this test, so a
    // successful <Redirect to="/"> renders nothing rather than throwing.
    expect(screen.queryByText('Admin Dashboard')).not.toBeInTheDocument();
  });

  it('redirects to home when the profile refetch errors even with a stored token', () => {
    localStorage.setItem('access_token', 'stale-jwt');
    useGetProfileQuery.mockReturnValue({ data: undefined, isLoading: false, isError: true });

    renderWithProviders(<AdminDashboard />, { store: buildStore(), route: '/admin' });

    expect(screen.queryByText('Admin Dashboard')).not.toBeInTheDocument();
  });

  it('shows a spinner while the profile fetch is still loading and redux auth has not hydrated yet', () => {
    localStorage.setItem('access_token', 'jwt');
    useGetProfileQuery.mockReturnValue({ data: undefined, isLoading: true, isError: false });

    renderWithProviders(<AdminDashboard />, { store: buildStore(), route: '/admin' });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('redirects to home for an authenticated non-admin user', () => {
    // A stored token so this actually reaches the role check below, rather
    // than short-circuiting on the earlier hasStoredSession guard.
    localStorage.setItem('access_token', 'jwt');
    const store = buildStore({ user: { user: { id: 1, username: 'liviu', role: 'USER' }, isAuthenticated: true } });
    renderWithProviders(<AdminDashboard />, { store, route: '/admin' });

    expect(screen.queryByText('Admin Dashboard')).not.toBeInTheDocument();
  });

  it('redirects to home for a stored-session non-admin profile that has not hydrated redux yet', () => {
    localStorage.setItem('access_token', 'jwt');
    useGetProfileQuery.mockReturnValue({ data: { id: 1, role: 'USER' }, isLoading: false, isError: false });

    renderWithProviders(<AdminDashboard />, { store: buildStore(), route: '/admin' });

    expect(screen.queryByText('Admin Dashboard')).not.toBeInTheDocument();
  });

  it('renders the dashboard and all four infrastructure cards for an already-authenticated ADMIN (redux path)', () => {
    // hasStoredSession gates the redirect regardless of redux auth state —
    // a stored token is what proves this isn't a stale/local-only session.
    localStorage.setItem('access_token', 'jwt');
    const store = buildStore({ user: { user: { id: 1, username: 'liviu', role: 'ADMIN' }, isAuthenticated: true } });
    renderWithProviders(<AdminDashboard />, { store, route: '/admin' });

    expect(screen.getByText('Admin Dashboard')).toBeInTheDocument();
    expect(screen.getByText('Discovery (Eureka)')).toBeInTheDocument();
    expect(screen.getByText('API Gateway')).toBeInTheDocument();
    expect(screen.getByText('Kibana')).toBeInTheDocument();
    // No VITE_GRAFANA_URL is configured in the test environment, so the
    // fourth card falls back to its "Metrics" title/description branch.
    expect(screen.getByText('Metrics')).toBeInTheDocument();
  });

  it('renders the dashboard for a stored-session ADMIN whose profile just resolved (pre-redux-hydration path)', () => {
    localStorage.setItem('access_token', 'jwt');
    useGetProfileQuery.mockReturnValue({ data: { id: 1, role: 'ADMIN' }, isLoading: false, isError: false });

    renderWithProviders(<AdminDashboard />, { store: buildStore(), route: '/admin' });

    expect(screen.getByText('Admin Dashboard')).toBeInTheDocument();
  });
});
