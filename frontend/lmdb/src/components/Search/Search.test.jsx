// Tests Search: hidden off the home route, and on Enter resolves the typed query through
// ai-service's search-execute endpoint (#204) rather than the old direct movie-service search.
import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Search, { toTmdbMovieShape } from './Search';
import genreOrCategoryReducer, { aiSearchSucceeded } from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useExecuteSearchMutation } from '../../services/AI';

vi.mock('../../services/AI', () => ({
  useExecuteSearchMutation: vi.fn(),
}));

const buildStore = () => configureStore({ reducer: { currentGenreOrCategory: genreOrCategoryReducer } });

// Builds the [trigger, result] tuple useExecuteSearchMutation() returns, with `trigger` resolving
// (or rejecting) the same way RTK Query's real mutation trigger does — a function returning an
// object with `.unwrap()`.
const mockMutation = ({ resolve, reject } = {}) => {
  const trigger = vi.fn(() => ({
    unwrap: () => (reject ? Promise.reject(reject) : Promise.resolve(resolve ?? { results: [] })),
  }));
  useExecuteSearchMutation.mockReturnValue([trigger, {}]);
  return trigger;
};

describe('Search', () => {
  it('renders the search field on the home route', () => {
    mockMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    expect(screen.getByRole('textbox')).toBeInTheDocument();
  });

  it('renders nothing off the home route', () => {
    mockMutation();
    const { container } = renderWithProviders(<Search />, { route: '/movie/1', store: buildStore() });
    expect(container).toBeEmptyDOMElement();
  });

  it('calls executeSearch with the typed query when Enter is pressed', async () => {
    const trigger = mockMutation({ resolve: { results: [] } });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    // userEvent v14+ dispatches events asynchronously, so typing must be awaited.
    await userEvent.type(screen.getByRole('textbox'), 'batman{enter}');

    expect(trigger).toHaveBeenCalledWith('batman');
  });

  it('does not call executeSearch on other key presses', async () => {
    const trigger = mockMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });

    await userEvent.type(screen.getByRole('textbox'), 'batman');

    expect(trigger).not.toHaveBeenCalled();
  });

  it('dispatches aiSearchStarted, then aiSearchSucceeded with TMDB-shaped results, on success', async () => {
    const rawResult = {
      movieId: 550, title: 'Fight Club', overview: 'An insomniac...', releaseDate: '1999-10-15', posterPath: '/p.jpg', voteAverage: 8.4,
    };
    mockMutation({ resolve: { results: [rawResult] } });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    await userEvent.type(screen.getByRole('textbox'), 'fight club{enter}');
    // The mutation resolves asynchronously; wait for the dispatched state to settle.
    await waitFor(() => {
      expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('succeeded');
    });

    const { aiSearchQuery, aiSearchResults } = store.getState().currentGenreOrCategory;
    expect(aiSearchQuery).toBe('fight club');
    expect(aiSearchResults.results).toEqual([toTmdbMovieShape(rawResult)]);
  });

  it('dispatches aiSearchFailed, not a blank/stuck state, when the endpoint call rejects', async () => {
    mockMutation({ reject: new Error('network error') });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    await userEvent.type(screen.getByRole('textbox'), 'batman{enter}');
    await waitFor(() => {
      expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('failed');
    });

    expect(store.getState().currentGenreOrCategory.aiSearchResults).toBeNull();
  });

  it('pressing Enter on an empty box clears the search instead of calling the endpoint (#204 AC4)', async () => {
    const trigger = mockMutation();
    const store = buildStore();
    // Seed a prior search result, the way it would look mid-session.
    store.dispatch(aiSearchSucceeded({ results: [{ id: 1 }] }));
    renderWithProviders(<Search />, { route: '/', store });

    await userEvent.type(screen.getByRole('textbox'), '{enter}');

    expect(trigger).not.toHaveBeenCalled();
    expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('idle');
    expect(store.getState().currentGenreOrCategory.aiSearchResults).toBeNull();
  });

  it('pressing Enter on a whitespace-only box also clears rather than searching for blank text', async () => {
    const trigger = mockMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });

    await userEvent.type(screen.getByRole('textbox'), '   {enter}');

    expect(trigger).not.toHaveBeenCalled();
  });

  it('a slower, earlier search cannot overwrite a faster, later one (out-of-order responses)', async () => {
    let resolveFirst;
    let resolveSecond;
    const trigger = vi.fn()
      .mockImplementationOnce(() => ({ unwrap: () => new Promise((resolve) => { resolveFirst = resolve; }) }))
      .mockImplementationOnce(() => ({ unwrap: () => new Promise((resolve) => { resolveSecond = resolve; }) }));
    useExecuteSearchMutation.mockReturnValue([trigger, {}]);
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    const input = screen.getByRole('textbox');
    await userEvent.type(input, 'batman{enter}');
    await userEvent.clear(input);
    await userEvent.type(input, 'superman{enter}');

    // The newer ("superman") search resolves first; the older ("batman") one resolves after —
    // simulating the exact out-of-order-response scenario the latestQueryRef guard exists for.
    resolveSecond({ results: [{ movieId: 2, title: 'Superman' }] });
    await waitFor(() => {
      expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('succeeded');
    });
    resolveFirst({ results: [{ movieId: 1, title: 'Batman' }] });
    // Give the stale "batman" promise's .then a turn to run, if it were (incorrectly) going to.
    await new Promise((resolve) => { setTimeout(resolve, 0); });

    const { aiSearchQuery, aiSearchResults } = store.getState().currentGenreOrCategory;
    expect(aiSearchQuery).toBe('superman');
    expect(aiSearchResults.results).toHaveLength(1);
    expect(aiSearchResults.results[0].title).toBe('Superman');
  });
});

describe('toTmdbMovieShape', () => {
  it('maps ai-service field names to the TMDB shape every rendering component expects', () => {
    expect(toTmdbMovieShape({
      movieId: 27205, title: 'Inception', overview: 'A thief...', releaseDate: '2010-07-16', posterPath: '/c.jpg', voteAverage: 8.4,
    })).toEqual({
      id: 27205,
      title: 'Inception',
      overview: 'A thief...',
      poster_path: '/c.jpg',
      backdrop_path: '/c.jpg',
      release_date: '2010-07-16',
      vote_average: 8.4,
    });
  });
});
