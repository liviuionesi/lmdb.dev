// Tests Sidebar: fixed categories, genre list (loading/loaded), selection
// dispatches, and that navigating closes the mobile drawer.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Sidebar from './Sidebar';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useGetGenresQuery } from '../../services/TMDB';

vi.mock('../../services/TMDB', () => ({
  useGetGenresQuery: vi.fn(),
}));

const buildStore = (preloadedState) => configureStore({
  reducer: { currentGenreOrCategory: genreOrCategoryReducer },
  preloadedState,
});

describe('Sidebar', () => {
  beforeEach(() => {
    useGetGenresQuery.mockReturnValue({ data: undefined, isFetching: false });
  });

  it('renders the three fixed categories', () => {
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store: buildStore() });

    expect(screen.getByText('Popular')).toBeInTheDocument();
    expect(screen.getByText('Top Rated')).toBeInTheDocument();
    expect(screen.getByText('Upcoming')).toBeInTheDocument();
  });

  it('shows a spinner while genres are loading', () => {
    useGetGenresQuery.mockReturnValue({ data: undefined, isFetching: true });
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store: buildStore() });

    expect(screen.getByRole('progressbar')).toBeInTheDocument();
  });

  it('renders each genre once loaded and dispatches its id on click', () => {
    useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 28, name: 'Action' }, { id: 35, name: 'Comedy' }] },
      isFetching: false,
    });
    const store = buildStore();
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store });

    expect(screen.getByText('Action')).toBeInTheDocument();
    userEvent.click(screen.getByText('Comedy'));

    expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe(35);
  });

  it('dispatches "popular" and clears the search query when a category is clicked', () => {
    const store = buildStore({ currentGenreOrCategory: { genreIdOrCategoryName: '', page: 1, searchQuery: 'batman' } });
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store });

    userEvent.click(screen.getByText('Top Rated'));

    expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe('top_rated');
    expect(store.getState().currentGenreOrCategory.searchQuery).toBe('');
  });

  it('closes the mobile drawer via setMobileOpen on mount', () => {
    const setMobileOpen = vi.fn();
    renderWithProviders(<Sidebar setMobileOpen={setMobileOpen} />, { store: buildStore() });

    expect(setMobileOpen).toHaveBeenCalledWith(false);
  });
});
