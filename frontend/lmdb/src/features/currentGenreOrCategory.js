import { createSlice } from '@reduxjs/toolkit';

export const genreOrCategory = createSlice({
  name: 'genreOrCategory',
  initialState: {
    genreIdOrCategoryName: '',
    page: 1,
    searchQuery: '',
    // AI-search state (#203/#204): a query submitted through Search.jsx resolves via ai-service's
    // POST /api/v1/ai/search/execute rather than the old direct movie-service title search (the
    // `searchQuery` field above, which VoiceControl.jsx still uses for its own not-yet-updated
    // flow — see #205). `aiSearchResults` is TMDB-list-shaped ({results, total_pages, ...}, built
    // by Search.jsx from ai-service's own response shape) so Movies.jsx can render it through the
    // exact same MovieList/FeaturedMovie/Pagination path as every other source.
    aiSearchQuery: '',
    aiSearchResults: null,
    aiSearchStatus: 'idle', // 'idle' | 'loading' | 'succeeded' | 'failed'
    // Live query-highlighting state (#207/#208, Story #199): the most recent span breakdown from
    // ai-service's POST /api/v1/ai/search/query, kept separate from aiSearchResults above — parsing
    // (this) and executing (aiSearchStarted/Succeeded/Failed) are two independent calls Search.jsx
    // fires on different triggers (debounced keystroke vs. Enter). Rendering the highlights
    // themselves is #209/#210's concern, not this Task's (#208).
    queryHighlightSpans: [],
  },
  reducers: {
    selectGenreOrCategory: (state, action) => {
      state.genreIdOrCategoryName = action.payload;
      state.searchQuery = '';
      // Leaving a search (typed or AI-resolved) behind when a genre/category is picked instead —
      // stale results from either path must not linger once the user has moved on.
      state.aiSearchQuery = '';
      state.aiSearchResults = null;
      state.aiSearchStatus = 'idle';
      state.queryHighlightSpans = [];
    },
    searchMovie: (state, action) => {
      state.searchQuery = action.payload;
      // VoiceControl.jsx's own (not yet #205-updated) flow dispatches this directly — exiting AI
      // search mode here is what lets Movies.jsx correctly fall back to the old query path instead
      // of continuing to show a stale AI result set from an earlier typed search.
      state.aiSearchQuery = '';
      state.aiSearchResults = null;
      state.aiSearchStatus = 'idle';
      state.queryHighlightSpans = [];
    },
    aiSearchStarted: (state, action) => {
      state.aiSearchQuery = action.payload;
      state.aiSearchStatus = 'loading';
      state.aiSearchResults = null;
    },
    aiSearchSucceeded: (state, action) => {
      state.aiSearchResults = action.payload;
      state.aiSearchStatus = 'succeeded';
    },
    aiSearchFailed: (state) => {
      state.aiSearchStatus = 'failed';
      state.aiSearchResults = null;
    },
    aiSearchCleared: (state) => {
      // Submitting an empty query (Enter on a cleared box) exits AI search mode entirely, rather
      // than calling the endpoint with blank input — Movies.jsx falls back to its old query path,
      // which already treats an empty searchQuery as "show popular movies" (#204 AC4).
      state.aiSearchQuery = '';
      state.aiSearchResults = null;
      state.aiSearchStatus = 'idle';
      state.queryHighlightSpans = [];
    },
    querySpansReceived: (state, action) => {
      // A debounced parse-as-you-type call (#208) resolved — replace the highlight spans wholesale
      // with this call's breakdown. Search.jsx only dispatches this after confirming the response
      // is still for the most recently typed value (#208 AC2), so no staleness guard belongs here.
      state.queryHighlightSpans = action.payload.spans;
    },
    querySpansCleared: (state) => {
      // Emptying the search box exits live-highlight mode immediately — same intent as
      // aiSearchCleared above, but for the separate parse-as-you-type flow.
      state.queryHighlightSpans = [];
    },
  },
});

export const {
  selectGenreOrCategory,
  searchMovie,
  aiSearchStarted,
  aiSearchSucceeded,
  aiSearchFailed,
  aiSearchCleared,
  querySpansReceived,
  querySpansCleared,
} = genreOrCategory.actions;

export default genreOrCategory.reducer;
