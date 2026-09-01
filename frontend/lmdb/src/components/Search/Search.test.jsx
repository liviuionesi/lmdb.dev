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
import genreOrCategoryReducer, { aiSearchSucceeded, querySpansReceived, dictatedQuerySubmitted } from '../../features/currentGenreOrCategory';
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

describe('Search - live highlight rendering (#209)', () => {
  // Matches the '#208' block above: fireEvent.change schedules a real #208 debounce timer these
  // tests don't care about (they only assert on `query`-driven UI, not on a parseQuery response) —
  // fake timers keep that timer from firing for real in the background after each test ends.
  beforeEach(() => {
    mockMutation();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders no highlight legend or overlay while the search box is empty', () => {
    mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });

    expect(screen.queryByRole('button', { name: /search highlights/i })).not.toBeInTheDocument();
    expect(screen.queryByTestId('query-highlight-overlay')).not.toBeInTheDocument();
  });

  it('renders no highlight overlay while empty, even with stale spans left over in Redux state', () => {
    // A prior search's spans that querySpansCleared() should have wiped, still present here to
    // prove overlay visibility is gated on the CURRENT query being non-blank, not on whether spans
    // happen to exist — otherwise a future refactor that lost the querySpansCleared() dispatch
    // would leave stale highlights rendered over an empty box with nothing to catch it.
    mockParseMutation();
    const store = buildStore();
    store.dispatch(querySpansReceived({ spans: [{ text: 'x', category: 'ENTITY', start: 0, end: 1 }] }));
    renderWithProviders(<Search />, { route: '/', store });

    expect(screen.queryByTestId('query-highlight-overlay')).not.toBeInTheDocument();
  });

  it('shows the legend and overlay, and hides the real input\'s own text, once a query is typed (AC3, overlay approach)', () => {
    mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    const input = screen.getByRole('textbox');

    fireEvent.change(input, { target: { value: 'batman' } });

    expect(screen.getByRole('button', { name: /search highlights/i })).toBeInTheDocument();
    expect(screen.getByTestId('query-highlight-overlay')).toBeInTheDocument();
    // The real <input>'s glyphs are hidden so only the overlay's rendition is visible — the caret
    // itself must stay visible, so only `color` (not `caretColor`) goes transparent.
    expect(input.style.color).toBe('transparent');
    expect(input.style.caretColor).not.toBe('');
  });

  it('renders the current highlight spans from Redux state, overlaid on the typed text (AC1)', () => {
    mockParseMutation();
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });
    const input = screen.getByRole('textbox');

    fireEvent.change(input, { target: { value: 'a and b' } });
    act(() => {
      store.dispatch(querySpansReceived({
        spans: [{
          text: 'and', category: 'CONNECTOR', start: 2, end: 5,
        }],
      }));
    });

    expect(screen.getByText('and')).toBeInTheDocument();
  });

  it("positions the overlay over the real input's measured box, relative to the field wrapper (AC2)", () => {
    // jsdom's own getBoundingClientRect always returns an all-zero rect, which would let a
    // backwards subtraction (wrapper-relative-to-input instead of input-relative-to-wrapper) or a
    // dropped offset pass unnoticed — stub distinct, non-zero rects for the wrapper and the real
    // input so the geometry math in Search.jsx's syncOverlayRect is actually exercised.
    mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    const input = screen.getByRole('textbox');
    const wrapper = screen.getByTestId('search-field-wrapper');

    wrapper.getBoundingClientRect = () => ({
      left: 0, top: 0, width: 300, height: 40, right: 300, bottom: 40,
    });
    input.getBoundingClientRect = () => ({
      left: 40, top: 8, width: 220, height: 24, right: 260, bottom: 32,
    });

    fireEvent.change(input, { target: { value: 'batman' } });

    expect(screen.getByTestId('query-highlight-overlay')).toHaveStyle({
      left: '40px', top: '8px', width: '220px', height: '24px',
    });
  });

  it("mirrors the real input's horizontal scroll onto the overlay as typing scrolls past the field's visible width (AC2)", () => {
    mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    const input = screen.getByRole('textbox');

    fireEvent.change(input, { target: { value: 'a query long enough to scroll past the visible field width' } });
    const overlay = screen.getByTestId('query-highlight-overlay');
    expect(overlay.scrollLeft).toBe(0);

    // jsdom doesn't scroll a real <input> on its own — set scrollLeft directly, the way the
    // browser would as the user keeps typing past the field's width, then fire the 'scroll' event
    // handleInputScroll listens for.
    input.scrollLeft = 42;
    fireEvent.scroll(input);

    expect(overlay.scrollLeft).toBe(42);
  });

  it('clears the legend and overlay, and restores the real input\'s text color, when the box is emptied', () => {
    mockParseMutation();
    renderWithProviders(<Search />, { route: '/', store: buildStore() });
    const input = screen.getByRole('textbox');

    fireEvent.change(input, { target: { value: 'batman' } });
    expect(screen.getByRole('button', { name: /search highlights/i })).toBeInTheDocument();

    fireEvent.change(input, { target: { value: '' } });

    expect(screen.queryByRole('button', { name: /search highlights/i })).not.toBeInTheDocument();
    expect(screen.queryByTestId('query-highlight-overlay')).not.toBeInTheDocument();
    expect(input.style.color).not.toBe('transparent');
  });

  describe('with ResizeObserver available (AC2, a layout shift window resize would miss)', () => {
    // jsdom doesn't implement ResizeObserver at all — Search.jsx guards against exactly that (see
    // its Notes), which also means the guarded branch gets no coverage without a stand-in here.
    // This mock lets the test both exercise that branch and verify what it actually does: observe
    // the field wrapper (not some other node) and disconnect cleanly on unmount.
    let observeSpy;
    let disconnectSpy;
    let originalResizeObserver;

    beforeEach(() => {
      observeSpy = vi.fn();
      disconnectSpy = vi.fn();
      originalResizeObserver = global.ResizeObserver;
      // `new ResizeObserver(...)` requires a real constructor — a plain arrow-returning vi.fn()
      // isn't new-able and throws "is not a constructor" the instant Search.jsx's effect runs.
      global.ResizeObserver = vi.fn().mockImplementation(function MockResizeObserver() {
        this.observe = observeSpy;
        this.disconnect = disconnectSpy;
      });
    });

    afterEach(() => {
      global.ResizeObserver = originalResizeObserver;
    });

    it('observes the field wrapper so a non-window layout shift re-syncs the overlay too', () => {
      mockParseMutation();
      renderWithProviders(<Search />, { route: '/', store: buildStore() });

      expect(observeSpy).toHaveBeenCalledWith(screen.getByTestId('search-field-wrapper'));
    });

    it('disconnects the observer on unmount, alongside the window resize listener', () => {
      mockParseMutation();
      const { unmount } = renderWithProviders(<Search />, { route: '/', store: buildStore() });

      unmount();

      expect(disconnectSpy).toHaveBeenCalledTimes(1);
    });
  });
});

describe('Search - dictated query hand-off (#199 AC5 / #210)', () => {
  // Matches the '#208'/'#209' blocks above: fake timers keep a real setTimeout from firing in the
  // background once a test ends, and let the "cancels a pending typed debounce" test below actually
  // advance past QUERY_HIGHLIGHT_DEBOUNCE_MS without waiting for it in real time.
  beforeEach(() => {
    mockMutation({ resolve: { results: [] } });
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('populates the field, runs the parse call immediately (not debounced), and executes the search — as if the query had been typed and Enter pressed', async () => {
    const rawResult = { movieId: 550, title: 'Fight Club' };
    // Overrides this block's beforeEach default with a captured trigger + a real result, so the
    // "executes the search" half of this test's own title is actually asserted below — not just
    // implied by a beforeEach call whose return value nothing here used.
    const searchTrigger = mockMutation({ resolve: { results: [rawResult] } });
    const parseTrigger = mockParseMutation({
      resolve: { filter: null, spans: [{ text: 'and', category: 'CONNECTOR', start: 2, end: 5 }] },
    });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    await act(async () => {
      store.dispatch(dictatedQuerySubmitted('a and b'));
    });

    // The field shows the dictated text, exactly like a typed query would.
    expect(screen.getByRole('textbox')).toHaveValue('a and b');
    // The parse call fired without waiting out QUERY_HIGHLIGHT_DEBOUNCE_MS — a dictated utterance
    // arrives whole, so there's no keystroke-by-keystroke pause to debounce.
    expect(parseTrigger).toHaveBeenCalledWith('a and b');
    // The search itself actually ran too, all the way through to a mapped, succeeded result —
    // not just the highlighting half of the pipeline.
    expect(searchTrigger).toHaveBeenCalledWith('a and b');
    expect(store.getState().currentGenreOrCategory.aiSearchStatus).toBe('succeeded');
    expect(store.getState().currentGenreOrCategory.aiSearchQuery).toBe('a and b');
    expect(store.getState().currentGenreOrCategory.aiSearchResults).toEqual({
      results: [toTmdbMovieShape(rawResult)],
    });
  });

  it("renders the resulting highlight spans over the dictated text, the same overlay a typed query would get (#210's 'as if it had been typed')", async () => {
    mockParseMutation({
      resolve: { filter: null, spans: [{ text: 'and', category: 'CONNECTOR', start: 2, end: 5 }] },
    });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    await act(async () => {
      store.dispatch(dictatedQuerySubmitted('a and b'));
    });

    // A plain synchronous query, not findByText: findBy*'s internal polling relies on real
    // setTimeout, which never advances under this block's fake timers (the response is already
    // resolved by the `act` above, so there's nothing to actually wait for).
    expect(screen.getByTestId('query-highlight-overlay')).toBeInTheDocument();
    expect(screen.getByText('and')).toBeInTheDocument();
  });

  it('clears highlight spans AND any prior AI search results for a whitespace-only dictated query, mirroring handleKeyPress\'s own empty-Enter branch in full', async () => {
    // Defensive parity with handleKeyPress's empty-Enter branch (#204 AC4) — ai-service's
    // voice-command endpoint should never actually classify blank speech as a SEARCH command, but
    // this guards against calling parseQuery/executeSearch with input @NotBlank would reject
    // anyway, and against leaving a stale result set on screen if it somehow did.
    const searchTrigger = mockMutation();
    const parseTrigger = mockParseMutation();
    const store = buildStore();
    store.dispatch(querySpansReceived({ spans: [{ text: 'x', category: 'ENTITY', start: 0, end: 1 }] }));
    store.dispatch(aiSearchSucceeded({ results: [{ id: 1 }] }));
    renderWithProviders(<Search />, { route: '/', store });

    await act(async () => {
      store.dispatch(dictatedQuerySubmitted('   '));
    });

    expect(parseTrigger).not.toHaveBeenCalled();
    expect(searchTrigger).not.toHaveBeenCalled();
    const state = store.getState().currentGenreOrCategory;
    expect(state.queryHighlightSpans).toEqual([]);
    expect(state.aiSearchStatus).toBe('idle');
    expect(state.aiSearchResults).toBeNull();
    expect(state.dictatedQuery).toBeNull();
  });

  it('consumes the dictatedQuery marker once acted on, so the same transcript cannot re-trigger itself on a later, unrelated render', async () => {
    mockParseMutation({ resolve: { filter: null, spans: [] } });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    await act(async () => {
      store.dispatch(dictatedQuerySubmitted('batman'));
    });

    expect(store.getState().currentGenreOrCategory.dictatedQuery).toBeNull();
  });

  it('cancels a still-pending typed-query debounce timer when a dictated query lands mid-pause, so the stale typed value cannot fire after it', async () => {
    const parseTrigger = mockParseMutation({ resolve: { filter: null, spans: [] } });
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });
    const input = screen.getByRole('textbox');

    // A typed keystroke schedules a debounced parse call that hasn't fired yet...
    fireEvent.change(input, { target: { value: 'super' } });
    // ...then a dictated query lands before that pause elapses.
    await act(async () => {
      store.dispatch(dictatedQuerySubmitted('batman'));
    });
    // If the typed debounce timer had NOT been cancelled, letting it elapse now would fire a
    // second, stale parse call for 'super' after the dictated one for 'batman'.
    act(() => { vi.advanceTimersByTime(QUERY_HIGHLIGHT_DEBOUNCE_MS); });

    expect(parseTrigger).toHaveBeenCalledTimes(1);
    expect(parseTrigger).toHaveBeenCalledWith('batman');
  });

  it('a slower, earlier dictated query cannot overwrite a faster, later one — the shared staleness guard applies to back-to-back dictated queries too', async () => {
    let resolveFirst;
    let resolveSecond;
    const parseTrigger = vi.fn()
      .mockImplementationOnce(() => ({ unwrap: () => new Promise((resolve) => { resolveFirst = resolve; }) }))
      .mockImplementationOnce(() => ({ unwrap: () => new Promise((resolve) => { resolveSecond = resolve; }) }));
    useParseQueryMutation.mockReturnValue([parseTrigger, {}]);
    const store = buildStore();
    renderWithProviders(<Search />, { route: '/', store });

    // Two dictated queries land back to back, before the first's parse call has resolved —
    // e.g. the user re-dictates a correction before ai-service answers the first attempt.
    await act(async () => { store.dispatch(dictatedQuerySubmitted('batman')); });
    await act(async () => { store.dispatch(dictatedQuerySubmitted('superman')); });

    const supermanSpans = [{ text: 'superman', category: 'ENTITY', start: 0, end: 8 }];
    // The newer ("superman") call resolves first; the older, slower ("batman") one resolves after
    // — the response Search.jsx must discard as stale, same guard the typed-path AC2 test above
    // already proves for keystrokes.
    await act(async () => { resolveSecond({ filter: null, spans: supermanSpans }); });
    await act(async () => {
      resolveFirst({ filter: null, spans: [{ text: 'batman', category: 'ENTITY', start: 0, end: 6 }] });
    });

    expect(screen.getByRole('textbox')).toHaveValue('superman');
    expect(store.getState().currentGenreOrCategory.queryHighlightSpans).toEqual(supermanSpans);
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
