// Tests the Recommendations view (#221): the unauthenticated redirect, loading/error/empty-history
// states, the "zero picks" case distinct from empty-history, and rendering each result with
// ai-service's explanation text via Movie's optional caption.
import React from 'react';
import { render, screen } from '@testing-library/react';
import { configureStore } from '@reduxjs/toolkit';
import { Provider } from 'react-redux';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';

import Recommendations from './Recommendations';
import authReducer from '../../features/auth';
import { renderWithProviders, theme } from '../../test-utils/render';
import { useGetMovieRecommendationsQuery } from '../../services/AI';
import { useGetMovieQuery } from '../../services/TMDB';

vi.mock('../../services/AI', () => ({
  useGetMovieRecommendationsQuery: vi.fn(),
}));

vi.mock('../../services/TMDB', () => ({
  useGetMovieQuery: vi.fn(),
}));

const buildStore = (authenticated) => configureStore({
  reducer: { user: authReducer },
  preloadedState: authenticated ? { user: { user: { id: 1 }, isAuthenticated: true } } : undefined,
});

// Renders Recommendations alongside a "/" sentinel route, so an unauthenticated redirect is
// observable as "the Home sentinel replaced Recommendations", matching Profile.test.jsx's own
// pattern for the same `<Navigate replace>` check, rather than inferred from absent content (which
// would also be true of a crash or a null render).
function renderAtRecommendationsRoute(store) {
  return render(
    <ThemeProvider theme={theme}>
      <Provider store={store}>
        <MemoryRouter initialEntries={['/recommendations']}>
          <Routes>
            <Route path="/" element="Home" />
            <Route path="/recommendations" element={<Recommendations />} />
          </Routes>
        </MemoryRouter>
      </Provider>
    </ThemeProvider>,
  );
}

describe('Recommendations', () => {
  beforeEach(() => {
    useGetMovieQuery.mockReturnValue({ data: undefined, isFetching: true });
  });

  it('redirects an unauthenticated visitor to the home page', () => {
    useGetMovieRecommendationsQuery.mockReturnValue({ data: undefined, isFetching: false });
    renderAtRecommendationsRoute(buildStore(false));

    expect(screen.getByText('Home')).toBeInTheDocument();
    expect(screen.queryByText('Recommended For You')).not.toBeInTheDocument();
  });

  it('shows a centered spinner while the recommendations call is in flight', () => {
    useGetMovieRecommendationsQuery.mockReturnValue({ data: undefined, isFetching: true });
    renderWithProviders(<Recommendations />, { store: buildStore(true) });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('shows a distinct error state when the call fails, not a blank screen', () => {
    useGetMovieRecommendationsQuery.mockReturnValue({ error: { status: 500 }, isFetching: false });
    renderWithProviders(<Recommendations />, { store: buildStore(true) });

    expect(screen.getByText(/Couldn't load recommendations/)).toBeInTheDocument();
  });

  it('shows a distinct empty-history state for a new user with no favorites (#219/#220)', () => {
    useGetMovieRecommendationsQuery.mockReturnValue({
      data: { recommendations: [], isEmpty: true },
      isFetching: false,
    });
    renderWithProviders(<Recommendations />, { store: buildStore(true) });

    expect(screen.getByText(/Favorite a few movies/)).toBeInTheDocument();
  });

  it('shows a "nothing right now" message when history exists but zero picks come back', () => {
    // Distinct from the isEmpty (no-history) case above — same "nothing to show" shape, different
    // cause and copy, so this must not collide with the isEmpty branch.
    useGetMovieRecommendationsQuery.mockReturnValue({
      data: { recommendations: [], isEmpty: false },
      isFetching: false,
    });
    renderWithProviders(<Recommendations />, { store: buildStore(true) });

    expect(screen.getByText(/No recommendations available right now/)).toBeInTheDocument();
    expect(screen.queryByText(/Favorite a few movies/)).not.toBeInTheDocument();
  });

  it('renders each recommendation with its ai-service explanation text, not a bare card (#196 AC4)', () => {
    useGetMovieRecommendationsQuery.mockReturnValue({
      data: {
        isEmpty: false,
        recommendations: [
          { movieId: '550', score: 0.92, reason: 'Because you liked Se7en' },
        ],
      },
      isFetching: false,
    });
    useGetMovieQuery.mockReturnValue({
      data: { id: 550, title: 'Fight Club', poster_path: '/x.jpg', vote_average: 8.4 },
      isFetching: false,
    });

    renderWithProviders(<Recommendations />, { store: buildStore(true) });

    expect(screen.getByText('Fight Club')).toBeInTheDocument();
    expect(screen.getByText('Because you liked Se7en')).toBeInTheDocument();
  });
});
