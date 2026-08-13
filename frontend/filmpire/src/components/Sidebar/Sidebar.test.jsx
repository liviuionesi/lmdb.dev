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

  it('renders each genre once loaded and dispatches its id on click', async () => {
    useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 28, name: 'Action' }, { id: 35, name: 'Comedy' }] },
      isFetching: false,
    });
    const store = buildStore();
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store });

    expect(screen.getByText('Action')).toBeInTheDocument();
    // userEvent v14+ dispatches events asynchronously, so the click must be awaited.
    await userEvent.click(screen.getByText('Comedy'));

    expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe(35);
  });

  it('dispatches "popular" and clears the search query when a category is clicked', async () => {
    const store = buildStore({ currentGenreOrCategory: { genreIdOrCategoryName: '', page: 1, searchQuery: 'batman' } });
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store });

    await userEvent.click(screen.getByText('Top Rated'));

    expect(store.getState().currentGenreOrCategory.genreIdOrCategoryName).toBe('top_rated');
    expect(store.getState().currentGenreOrCategory.searchQuery).toBe('');
  });

  it('closes the mobile drawer via setMobileOpen on mount', () => {
    const setMobileOpen = vi.fn();
    renderWithProviders(<Sidebar setMobileOpen={setMobileOpen} />, { store: buildStore() });

    expect(setMobileOpen).toHaveBeenCalledWith(false);
  });

  // Guards the ListItem→ListItemButton conversion (#130): the old `button`
  // prop was deprecated in MUI v6, but `selected` still needs to reach the
  // rendered element as MUI's `Mui-selected` class, not just the click
  // handler that the tests above already cover.
  it('marks only the currently-selected genre with the MUI selected class', () => {
    useGetGenresQuery.mockReturnValue({
      data: { genres: [{ id: 28, name: 'Action' }, { id: 35, name: 'Comedy' }] },
      isFetching: false,
    });
    const store = buildStore({ currentGenreOrCategory: { genreIdOrCategoryName: 35, page: 1, searchQuery: '' } });
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store });

    expect(screen.getByText('Comedy').closest('.MuiListItemButton-root')).toHaveClass('Mui-selected');
    expect(screen.getByText('Action').closest('.MuiListItemButton-root')).not.toHaveClass('Mui-selected');
  });

  it('defaults to marking "Popular" selected when no genre/category is set', () => {
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store: buildStore() });

    expect(screen.getByText('Popular').closest('.MuiListItemButton-root')).toHaveClass('Mui-selected');
    expect(screen.getByText('Top Rated').closest('.MuiListItemButton-root')).not.toHaveClass('Mui-selected');
  });

  it('renders About & Credits platform link in the sidebar', () => {
    renderWithProviders(<Sidebar setMobileOpen={() => {}} />, { store: buildStore() });
    expect(screen.getByText('About & Credits')).toBeInTheDocument();
  });
});
