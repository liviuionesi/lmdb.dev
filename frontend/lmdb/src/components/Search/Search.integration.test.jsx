// #206: an integration-level test for typed search end to end — component -> dispatched action ->
// API call shape, in one flow. Search.test.jsx already covers the component's own dispatch logic by
// mocking useExecuteSearchMutation, and services/AI.test.js already covers aiApi's request shape by
// dispatching the endpoint directly, but neither exercises the two wired together: this renders
// Search against the REAL aiApi reducer/middleware (no mocked hook) with only `fetch` stubbed out,
// the same real-store-with-fetch-mocked technique services/AI.test.js and services/TMDB.test.js use
// for verifying RTK Query's actual request shape.
import React from 'react';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Search, { toTmdbMovieShape } from './Search';
import genreOrCategoryReducer from '../../features/currentGenreOrCategory';
import { aiApi } from '../../services/AI';
import { renderWithProviders } from '../../test-utils/render';

const baseUrl = 'http://localhost:8080/api/v1/ai';
// Same short-circuit AI.test.js/TMDB.test.js use — createDynamicBaseQuery's async health-check
// waterfall would otherwise issue its own fetch() before the endpoint request, stealing
// mock.calls[0] out from under the assertions below.
const pinStaticApiUrl = () => localStorage.setItem('lmdb_api_url', 'http://localhost:8080');

const jsonResponse = (body) => ({
  ok: true,
  status: 200,
  headers: new Headers({ 'content-type': 'application/json' }),
  json: async () => body,
  text: async () => JSON.stringify(body),
  clone() { return this; },
});

// A real store wiring both slices Search.jsx actually touches — currentGenreOrCategory (the
// dispatched-action half) and aiApi (the API-call-shape half) — rather than either being mocked.
const buildRealStore = () => configureStore({
  reducer: { currentGenreOrCategory: genreOrCategoryReducer, [aiApi.reducerPath]: aiApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(aiApi.middleware),
});

describe('Search integration: component -> dispatched action -> API call shape', () => {
  beforeEach(() => {
    pinStaticApiUrl();
  });

  afterEach(() => {
    delete global.fetch;
    localStorage.clear();
  });

  it('typing a query and pressing Enter issues the real POST /search/execute request and lands the mapped results in state', async () => {
    const rawResult = {
      movieId: 550, title: 'Fight Club', overview: 'An insomniac...', releaseDate: '1999-10-15', posterPath: '/p.jpg', voteAverage: 8.4,
    };
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ results: [rawResult] }));
    const store = buildRealStore();
    renderWithProviders(<Search />, { route: '/', store });

    await userEvent.type(screen.getByRole('textbox'), 'fight club{enter}');

    // 1. API call shape: the real aiApi query builder produced this exact request.
    await waitFor(() => expect(global.fetch).toHaveBeenCalled());
    const request = global.fetch.mock.calls[0][0];
    expect(request.url).toBe(`${baseUrl}/search/execute`);
    expect(request.method).toBe('POST');
    expect(JSON.parse(await request.text())).toEqual({ query: 'fight club' });

    // 2. Dispatched action: the real response round-trip lands the mapped results in the real store.
    await waitFor(() => {
      expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('succeeded');
    });
    const { aiSearchQuery, aiSearchResults } = store.getState().currentGenreOrCategory;
    expect(aiSearchQuery).toBe('fight club');
    expect(aiSearchResults.results).toEqual([toTmdbMovieShape(rawResult)]);
  });

  it('a real 500 from the endpoint dispatches aiSearchFailed rather than leaving state loading', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ message: 'boom' }),
      text: async () => JSON.stringify({ message: 'boom' }),
      clone() { return this; },
    });
    const store = buildRealStore();
    renderWithProviders(<Search />, { route: '/', store });

    await userEvent.type(screen.getByRole('textbox'), 'batman{enter}');

    await waitFor(() => {
      expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('failed');
    });
    expect(store.getState().currentGenreOrCategory.aiSearchResults).toBeNull();
  });
});
