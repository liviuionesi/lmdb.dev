import { createApi } from '@reduxjs/toolkit/query/react';
import { createDynamicBaseQuery } from '../utils/apiUrl';

// RTK Query slice for ai-service's natural-language search (#203). Separate from tmdbApi
// (services/TMDB.js), which only ever talks to movie-service's TMDB-shaped facade — ai-service is
// a genuinely different backend, reached through the gateway's own /api/v1/ai prefix rather than an
// empty one.
export const aiApi = createApi({
  reducerPath: 'aiApi',
  baseQuery: createDynamicBaseQuery('/api/v1/ai'),
  endpoints: (builder) => ({
    //* Parse and execute a natural-language movie query in one call (#203) — the search bar's
    //* submit action, replacing the old direct movie-service title search (#204).
    executeSearch: builder.mutation({
      query: (searchQuery) => ({ url: '/search/execute', method: 'POST', body: { query: searchQuery } }),
    }),
  }),
});

export const { useExecuteSearchMutation } = aiApi;
