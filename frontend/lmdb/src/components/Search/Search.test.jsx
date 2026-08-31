// Tests Search: hidden off the home route, and on Enter resolves the typed query through
// ai-service's search-execute endpoint (#204) rather than the old direct movie-service search.
// Also covers the separate debounced parse-as-you-type flow (#208) that feeds Story #199's live
// search-bar highlighting — wiring/race-condition behavior only, not the highlight rendering
// itself (#209/#210).
import React from 'react';
import {
  screen, waitFor, fireEvent, act,
} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { configureStore } from '@reduxjs/toolkit';

import Search, { toTmdbMovieShape, QUERY_HIGHLIGHT_DEBOUNCE_MS } from './Search';
import genreOrCategoryReducer, { aiSearchSucceeded, querySpansReceived } from '../../features/currentGenreOrCategory';
import { renderWithProviders } from '../../test-utils/render';
import { useExecuteSearchMutation, useParseQueryMutation } from '../../services/AI';

vi.mock('../../services/AI', () => ({
  useExecuteSearchMutation: vi.fn(),
  useParseQueryMutation: vi.fn(),
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

// Same shape as mockMutation above, but for useParseQueryMutation — the separate #208
// parse-as-you-type call. Every test needs this mocked (Search.jsx calls the hook unconditionally
// on every render), even tests that don't care about its behavior, hence the benign default.
const mockParseMutation = ({ resolve, reject } = {}) => {
  const trigger = vi.fn(() => ({
    unwrap: () => (reject ? Promise.reject(reject) : Promise.resolve(resolve ?? { filter: null, spans: [] })),
  }));
  useParseQueryMutation.mockReturnValue([trigger, {}]);
  return trigger;
};

describe('Search', () => {
  beforeEach(() => {
    mockParseMutation();
  });

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

describe('Search - live query highlighting (#208)', () => {
  beforeEach(() => {
    mockMutation();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('debounces for a pause within AC1\'s ~300-500ms range, not an arbitrarily short or long one', () => {
    // The other tests in this block only assert behavior relative to QUERY_HIGHLIGHT_DEBOUNCE_MS
    // itself (e.g. "advancing by the debounce fires the call"), which would still pass even if the
    // constant regressed to something absurd like 0ms or 10000ms — this pins the actual value AC1
    // requires.
    expect(QUERY_HIGHLIGHT_DEBOUNCE_MS).toBeGreaterThanOrEqual(300);
    expect(QUERY_HIGHLIGHT_DEBOUNCE_MS).toBeLessThanOrEqual(500);
  });

  it('collapses rapid typing into a single debounced parseQuery call, not one per keystroke (AC1)', () => {
    const parseTrigger = mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    const input = screen.getByRole('textbox');

    // Each keystroke arrives well within the debounce pause of the one before it.
    fireEvent.change(input, { target: { value: 'b' } });
    fireEvent.change(input, { target: { value: 'ba' } });
    fireEvent.change(input, { target: { value: 'bat' } });

    expect(parseTrigger).not.toHaveBeenCalled();

    act(() => {
      vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS);
    });

    expect(parseTrigger).toHaveBeenCalledTimes(1);
    expect(parseTrigger).toHaveBeenCalledWith('bat');
  });

  it('fires a new debounced call once the pause after the last keystroke actually elapses', () => {
    const parseTrigger = mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    const input = screen.getByRole('textbox');

    fireEvent.change(input, { target: { value: 'batman' } });
    act(() => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });
    fireEvent.change(input, { target: { value: 'batman begins' } });
    act(() => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });

    expect(parseTrigger).toHaveBeenCalledTimes(2);
    expect(parseTrigger).toHaveBeenNthCalledWith(1, 'batman');
    expect(parseTrigger).toHaveBeenNthCalledWith(2, 'batman begins');
  });

  it('discards a stale, out-of-order parseQuery response rather than overwriting newer highlights (AC2)', async () => {
    let resolveFirst;
    let resolveSecond;
    const parseTrigger = vi.fn()
      .mockImplementationOnce(() => ({ unwrap: () => new Promise((resolve) => { resolveFirst = resolve; }) }))
      .mockImplementationOnce(() => ({ unwrap: () => new Promise((resolve) => { resolveSecond = resolve; }) }));
    useParseQueryMutation.mockReturnValue([parseTrigger, {}]);
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });
    const input = screen.getByRole('textbox');

    // Two separate debounce windows, each producing its own in-flight call.
    fireEvent.change(input, { target: { value: 'batman' } });
    act(() => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });
    fireEvent.change(input, { target: { value: 'superman' } });
    act(() => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });

    expect(parseTrigger).toHaveBeenCalledTimes(2);

    const supermanSpans = [{ text: 'superman', category: 'ENTITY', start: 0, end: 8 }];
    // The newer ("superman") call resolves first; the older, slower ("batman") one resolves after
    // — the response Search.jsx must discard as stale.
    await act(async () => { resolveSecond({ filter: null, spans: supermanSpans }); });
    await act(async () => {
      resolveFirst({ filter: null, spans: [{ text: 'batman', category: 'ENTITY', start: 0, end: 6 }] });
    });

    expect(store.getState().currentGenreOrCategory.queryHighlightSpans).toEqual(supermanSpans);
  });

  it('leaves the last valid highlight spans in place when a parseQuery call fails (AC3)', async () => {
    const goodSpans = [{ text: 'and', category: 'CONNECTOR', start: 2, end: 5 }];
    // One trigger, two calls with different outcomes — not two separate mockReturnValue swaps,
    // which wouldn't reliably reach the second debounced call: Search.jsx only picks up a new
    // useParseQueryMutation() return value on its own next render, not the instant a test reassigns
    // the mock, so the closure scheduled by the second fireEvent.change below could otherwise still
    // be holding the first render's trigger reference.
    const parseTrigger = vi.fn()
      .mockImplementationOnce(() => ({ unwrap: () => Promise.resolve({ filter: null, spans: goodSpans }) }))
      .mockImplementationOnce(() => ({ unwrap: () => Promise.reject(new Error('network error')) }));
    useParseQueryMutation.mockReturnValue([parseTrigger, {}]);
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });
    const input = screen.getByRole('textbox');

    fireEvent.change(input, { target: { value: 'a and b' } });
    await act(async () => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });
    expect(store.getState().currentGenreOrCategory.queryHighlightSpans).toEqual(goodSpans);

    // A subsequent keystroke's debounced call now fails.
    fireEvent.change(input, { target: { value: 'a and b or c' } });
    await act(async () => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });

    // Still the last successfully-parsed spans — not cleared, not stuck loading.
    expect(store.getState().currentGenreOrCategory.queryHighlightSpans).toEqual(goodSpans);
  });

  it('clears highlight spans immediately when the box is emptied, without waiting for the debounce', () => {
    mockParseMutation();
    const store = buildStore();
    store.dispatch(querySpansReceived({ spans: [{ text: 'and', category: 'CONNECTOR', start: 0, end: 3 }] }));
    renderWithProviders(<Search />, { route: '/', store });
    const input = screen.getByRole('textbox');

    // The field starts empty; type something first so the change back to blank is a real DOM value
    // transition (React's controlled-input change detection ignores a fireEvent.change that leaves
    // the value unchanged).
    fireEvent.change(input, { target: { value: 'batman' } });
    fireEvent.change(input, { target: { value: '' } });

    expect(store.getState().currentGenreOrCategory.queryHighlightSpans).toEqual([]);
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
