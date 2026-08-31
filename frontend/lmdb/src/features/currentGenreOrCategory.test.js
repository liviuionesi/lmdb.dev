// Tests the genreOrCategory redux slice: initial state and every reducer, including the AI-search
// state #204 added (aiSearchStarted/Succeeded/Failed) and how it interacts with the pre-existing
// genre/searchMovie reducers.
import reducer, {
  selectGenreOrCategory,
  searchMovie,
  aiSearchStarted,
  aiSearchSucceeded,
  aiSearchFailed,
  aiSearchCleared,
  querySpansReceived,
  querySpansCleared,
} from './currentGenreOrCategory';

describe('currentGenreOrCategory slice', () => {
  it('returns the initial state', () => {
    expect(reducer(undefined, { type: '@@INIT' })).toEqual({
      genreIdOrCategoryName: '',
      page: 1,
      searchQuery: '',
      aiSearchQuery: '',
      aiSearchResults: null,
      aiSearchStatus: 'idle',
      queryHighlightSpans: [],
    });
  });

  it('selectGenreOrCategory sets the genre/category and clears any active search', () => {
    const state = {
      genreIdOrCategoryName: '', page: 1, searchQuery: 'batman', aiSearchQuery: 'batman', aiSearchResults: { results: [] }, aiSearchStatus: 'succeeded', queryHighlightSpans: [],
    };
    const next = reducer(state, selectGenreOrCategory('popular'));
    expect(next.genreIdOrCategoryName).toBe('popular');
    expect(next.searchQuery).toBe('');
  });

  it('selectGenreOrCategory accepts a numeric genre id', () => {
    const next = reducer(undefined, selectGenreOrCategory(28));
    expect(next.genreIdOrCategoryName).toBe(28);
  });

  it('selectGenreOrCategory also clears any active AI search, not just the old searchQuery', () => {
    const state = {
      genreIdOrCategoryName: '', page: 1, searchQuery: '', aiSearchQuery: 'batman', aiSearchResults: { results: [{ id: 1 }] }, aiSearchStatus: 'succeeded',
    };
    const next = reducer(state, selectGenreOrCategory('popular'));
    expect(next.aiSearchQuery).toBe('');
    expect(next.aiSearchResults).toBeNull();
    expect(next.aiSearchStatus).toBe('idle');
  });

  it('searchMovie sets the search query without touching the selected genre', () => {
    const state = {
      genreIdOrCategoryName: 'popular', page: 1, searchQuery: '', aiSearchQuery: '', aiSearchResults: null, aiSearchStatus: 'idle',
    };
    const next = reducer(state, searchMovie('batman'));
    expect(next.searchQuery).toBe('batman');
    expect(next.genreIdOrCategoryName).toBe('popular');
  });

  it('searchMovie (VoiceControls own flow, #205) exits AI search mode, so Movies falls back to the old query path instead of showing a stale AI result set', () => {
    const state = {
      genreIdOrCategoryName: '', page: 1, searchQuery: '', aiSearchQuery: 'previous typed query', aiSearchResults: { results: [{ id: 1 }] }, aiSearchStatus: 'succeeded',
    };
    const next = reducer(state, searchMovie('spoken query'));
    expect(next.searchQuery).toBe('spoken query');
    expect(next.aiSearchQuery).toBe('');
    expect(next.aiSearchResults).toBeNull();
    expect(next.aiSearchStatus).toBe('idle');
  });

  it('aiSearchStarted records the query and enters the loading state, clearing any prior results', () => {
    const state = {
      genreIdOrCategoryName: '', page: 1, searchQuery: '', aiSearchQuery: '', aiSearchResults: { results: [{ id: 1 }] }, aiSearchStatus: 'succeeded',
    };
    const next = reducer(state, aiSearchStarted('new query'));
    expect(next.aiSearchQuery).toBe('new query');
    expect(next.aiSearchStatus).toBe('loading');
    expect(next.aiSearchResults).toBeNull();
  });

  it('aiSearchSucceeded stores the results and enters the succeeded state', () => {
    const state = {
      genreIdOrCategoryName: '', page: 1, searchQuery: '', aiSearchQuery: 'query', aiSearchResults: null, aiSearchStatus: 'loading',
    };
    const next = reducer(state, aiSearchSucceeded({ results: [{ id: 550 }] }));
    expect(next.aiSearchResults).toEqual({ results: [{ id: 550 }] });
    expect(next.aiSearchStatus).toBe('succeeded');
  });

  it('aiSearchFailed clears any results and enters the failed state', () => {
    const state = {
      genreIdOrCategoryName: '', page: 1, searchQuery: '', aiSearchQuery: 'query', aiSearchResults: null, aiSearchStatus: 'loading',
    };
    const next = reducer(state, aiSearchFailed());
    expect(next.aiSearchStatus).toBe('failed');
    expect(next.aiSearchResults).toBeNull();
  });

  it('aiSearchCleared resets AI-search state to idle (Enter on an empty search box, #204 AC4)', () => {
    const state = {
      genreIdOrCategoryName: 'popular', page: 1, searchQuery: '', aiSearchQuery: 'batman', aiSearchResults: { results: [{ id: 1 }] }, aiSearchStatus: 'succeeded',
    };
    const next = reducer(state, aiSearchCleared());
    expect(next.aiSearchQuery).toBe('');
    expect(next.aiSearchResults).toBeNull();
    expect(next.aiSearchStatus).toBe('idle');
    // Only the AI-search fields are touched — the currently-selected genre/category is untouched,
    // matching the pre-#204 "clear search falls back to whatever was already selected" behavior.
    expect(next.genreIdOrCategoryName).toBe('popular');
  });

  it('querySpansReceived (#208) stores the latest span breakdown for live search-bar highlighting', () => {
    const state = { genreIdOrCategoryName: '', page: 1, searchQuery: '', aiSearchQuery: '', aiSearchResults: null, aiSearchStatus: 'idle', queryHighlightSpans: [] };
    const spans = [{ text: 'and', category: 'CONNECTOR', start: 5, end: 8 }];

    const next = reducer(state, querySpansReceived({ spans }));

    expect(next.queryHighlightSpans).toEqual(spans);
  });

  it('querySpansReceived replaces any previous span breakdown wholesale, not merges', () => {
    const state = {
      genreIdOrCategoryName: '',
      page: 1,
      searchQuery: '',
      aiSearchQuery: '',
      aiSearchResults: null,
      aiSearchStatus: 'idle',
      queryHighlightSpans: [{ text: 'stale', category: 'ENTITY', start: 0, end: 5 }],
    };
    const spans = [{ text: 'or', category: 'CONNECTOR', start: 0, end: 2 }];

    const next = reducer(state, querySpansReceived({ spans }));

    expect(next.queryHighlightSpans).toEqual(spans);
  });

  it('querySpansCleared (#208) resets the highlight spans, e.g. when the search box is emptied', () => {
    const state = {
      genreIdOrCategoryName: '',
      page: 1,
      searchQuery: '',
      aiSearchQuery: '',
      aiSearchResults: null,
      aiSearchStatus: 'idle',
      queryHighlightSpans: [{ text: 'and', category: 'CONNECTOR', start: 5, end: 8 }],
    };

    const next = reducer(state, querySpansCleared());

    expect(next.queryHighlightSpans).toEqual([]);
  });

  it.each([
    ['selectGenreOrCategory', () => selectGenreOrCategory('popular')],
    ['searchMovie', () => searchMovie('spoken query')],
    ['aiSearchCleared', () => aiSearchCleared()],
  ])('%s also clears any stale highlight spans left over from a previous search', (_name, buildAction) => {
    const state = {
      genreIdOrCategoryName: '',
      page: 1,
      searchQuery: '',
      aiSearchQuery: '',
      aiSearchResults: null,
      aiSearchStatus: 'idle',
      queryHighlightSpans: [{ text: 'and', category: 'CONNECTOR', start: 5, end: 8 }],
    };

    const next = reducer(state, buildAction());

    expect(next.queryHighlightSpans).toEqual([]);
  });
});
