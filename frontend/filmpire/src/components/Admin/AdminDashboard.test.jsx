// Tests AdminDashboard: the ADMIN-only gate (redirect for anonymous/non-admin
// users, a loading spinner while the session is being restored) and that the
// four infrastructure StatusCards render once an ADMIN session is confirmed,
// via both the already-hydrated Redux path and the profile-refetch-on-load
// path. `useServiceStatus` is mocked (same as StatusCard.test.jsx) so cards
// render deterministically without real network probes to localhost:8761 etc.
import React from 'react';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes, useNavigationType } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import { configureStore } from '@reduxjs/toolkit';

import AdminDashboard from './AdminDashboard';
import authReducer from '../../features/auth';
import { theme } from '../../test-utils/render';
import { useGetProfileQuery } from '../../services/user';
import useServiceStatus from './useServiceStatus';

vi.mock('../../services/user', () => ({
  useGetProfileQuery: vi.fn(),
}));

vi.mock('./useServiceStatus');

const buildStore = (preloadedState) => configureStore({
  reducer: { user: authReducer },
  preloadedState,
});

// Surfaces react-router's own classification of the last navigation
// ('PUSH' | 'REPLACE' | 'POP') so a test can assert AdminDashboard's redirect
// used <Navigate replace> rather than a regular push — the two render
// identically at the destination, so nothing else here would catch a
// regression that dropped `replace`.
const NavigationTypeProbe = () => {
  const navigationType = useNavigationType();
  return <div data-testid="nav-type">{navigationType}</div>;
};

/**
 * Renders AdminDashboard at "/admin" alongside a sentinel "/" route, so a
 * `<Navigate to="/">` is observable as "the Home sentinel replaced
 * AdminDashboard" rather than inferred from AdminDashboard's absence (which
 * would also be true if the component just crashed or rendered null).
 */
const renderAtAdminRoute = (store) => render(
  <ThemeProvider theme={theme}>
    <Provider store={store}>
      <MemoryRouter initialEntries={['/admin']}>
        <NavigationTypeProbe />
        <Routes>
          <Route path="/" element="Home" />
          <Route path="/admin" element={<AdminDashboard />} />
        </Routes>
      </MemoryRouter>
    </Provider>
  </ThemeProvider>,
);

describe('AdminDashboard', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    useGetProfileQuery.mockReturnValue({ data: undefined, isLoading: false, isError: false });
    useServiceStatus.mockReturnValue('up');
  });

  it('redirects to home when there is no stored session, via history replace', () => {
    renderAtAdminRoute(buildStore());

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.queryByText('Admin Dashboard')).not.toBeInTheDocument();
    // <Navigate replace> must classify as REPLACE, not PUSH — otherwise the
    // gated /admin route would stay in history, reachable again via Back.
    expect(screen.getByTestId('nav-type')).toHaveTextContent('REPLACE');
  });

  it('redirects to home when the profile refetch errors even with a stored token', () => {
    localStorage.setItem('access_token', 'stale-jwt');
    useGetProfileQuery.mockReturnValue({ data: undefined, isLoading: false, isError: true });

    renderAtAdminRoute(buildStore());

    expect(screen.getByText('Home')).toBeInTheDocument();
  });

  it('shows a spinner while the profile fetch is still loading and redux auth has not hydrated yet', () => {
    localStorage.setItem('access_token', 'jwt');
    useGetProfileQuery.mockReturnValue({ data: undefined, isLoading: true, isError: false });

    renderAtAdminRoute(buildStore());

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
    // Loading, not yet redirected and not yet showing dashboard content.
    expect(screen.queryByText('Home')).not.toBeInTheDocument();
    expect(screen.queryByText('Admin Dashboard')).not.toBeInTheDocument();
  });

  it('redirects to home for an authenticated non-admin user', () => {
    // A stored token so this actually reaches the role check below, rather
    // than short-circuiting on the earlier hasStoredSession guard.
    localStorage.setItem('access_token', 'jwt');
    const store = buildStore({ user: { user: { id: 1, username: 'liviu', role: 'USER' }, isAuthenticated: true } });

    renderAtAdminRoute(store);

    expect(screen.getByText('Home')).toBeInTheDocument();
    // This exercises the role-check's own Navigate call site (distinct from
    // the hasStoredSession/isError one above) — confirm it also replaces.
    expect(screen.getByTestId('nav-type')).toHaveTextContent('REPLACE');
  });

  it('redirects to home for a stored-session non-admin profile that has not hydrated redux yet', () => {
    localStorage.setItem('access_token', 'jwt');
    useGetProfileQuery.mockReturnValue({ data: { id: 1, role: 'USER' }, isLoading: false, isError: false });

    renderAtAdminRoute(buildStore());

    expect(screen.getByText('Home')).toBeInTheDocument();
  });

  it('renders the dashboard and all four infrastructure cards for an already-authenticated ADMIN (redux path)', () => {
    // hasStoredSession gates the redirect regardless of redux auth state —
    // a stored token is what proves this isn't a stale/local-only session.
    localStorage.setItem('access_token', 'jwt');
    const store = buildStore({ user: { user: { id: 1, username: 'liviu', role: 'ADMIN' }, isAuthenticated: true } });

    renderAtAdminRoute(store);

    expect(screen.queryByText('Home')).not.toBeInTheDocument();
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

    renderAtAdminRoute(buildStore());

    expect(screen.getByText('Admin Dashboard')).toBeInTheDocument();
  });
});
