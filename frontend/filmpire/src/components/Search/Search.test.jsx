// Tests Search: hidden off the home route, and dispatches searchMovie on Enter.
import React from 'react';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Search from './Search';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';

const buildStore = () => configureStore({ reducer: { currentGenreOrCategory: genreOrCategoryReducer } });

describe('Search', () => {
  it('renders the search field on the home route', () => {
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('renders nothing off the home route', () => {
    const { container } = renderWithProviders(<Search />, { route: '/movie/1', store: buildStore() });
    expect(container).toBeEmptyDOMElement();
  });

  it('dispatches searchMovie with the typed query when Enter is pressed', () => {
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    userEvent.type(screen.getByRole('textbox'), 'batman{enter}');

    expect(store.getState().currentGenreOrCategory.searchQuery).toBe('batman');
  });

  it('does not dispatch on other key presses', () => {
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    userEvent.type(screen.getByRole('textbox'), 'batman');

    expect(store.getState().currentGenreOrCategory.searchQuery).toBe('');
  });
});
