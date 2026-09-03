import { createApi } from '@reduxjs/toolkit/query/react';
import { createDynamicBaseQuery } from '../utils/apiUrl';
import { userApi } from './user';
import { tmdbApi } from './TMDB';

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
    //* Parse a natural-language movie query WITHOUT executing it (#207) — the search bar's
    //* debounced parse-as-you-type call (#208), which only needs the token/span breakdown for live
    //* highlighting (#209), not actual search results. A mutation (not a query endpoint) to match
    //* executeSearch's own manual-trigger, manual-stale-response-guard pattern above, rather than
    //* mixing RTK Query's arg-keyed caching into a call fired on every debounced keystroke.
    parseQuery: builder.mutation({
      query: (searchQuery) => ({ url: '/search/query', method: 'POST', body: { query: searchQuery } }),
    }),
    //* AI-generated movie recommendations (#220). recentMovies is deliberately assembled here,
    //* not passed in by the caller: #219 settled that it comes from Favorites (never Watchlist,
    //* which means "not yet watched") and that user-service only ever returns a movieId, never a
    //* title, so each favorite's title has to be resolved through the TMDB facade first. A custom
    //* queryFn (rather than `query`) is what makes that possible — it can dispatch other APIs'
    //* endpoints and call the real baseQuery itself, all before deciding what to send.
    //* `count` is the only caller-supplied argument; omitting it leaves ai-service's own default
    //* (`RecommendationRequestDto#countOrDefault`) in charge.
    //* `keepUnusedDataFor: 0`: the backend refreshes the user's UserTasteProfile as a side effect
    //* of every call (see RecommendationRequestBodyDto's Javadoc), so this is not a cacheable read
    //* in the usual RTK Query sense — it must run again on every fresh mount rather than serve a
    //* result computed from favorites that may since have changed. Zero keeps that guarantee
    //* (evicting the cache entry the moment nothing is subscribed) without giving up `useQuery`'s
    //* auto-fetch-on-mount, which is what #221's view needs. It does not cover a favorite changing
    //* while the view stays mounted — #221 (or a later cache-tag-bridging task, if this proves not
    //* enough) is responsible for calling `refetch()` after that.
    getMovieRecommendations: builder.query({
      keepUnusedDataFor: 0,
      async queryFn(count, api, extraOptions, baseQuery) {
        // 1. Resolve the user's favorited movie ids (#219).
        const favoritesRequest = api.dispatch(userApi.endpoints.getFavorites.initiate());
        const favoritesResult = await favoritesRequest;
        favoritesRequest.unsubscribe();
        if (favoritesResult.error) {
          return { error: favoritesResult.error };
        }
        const favoriteIds = (favoritesResult.data ?? []).map((entry) => entry.movieId);

        // 2. Empty history (#219) is a real, distinct state — a new user with nothing favorited
        //    yet — not an error, so it's reported as one rather than calling ai-service with an
        //    empty recentMovies list.
        if (favoriteIds.length === 0) {
          return { data: { recommendations: [], isEmpty: true } };
        }

        // 3. Resolve each favorite's title via the TMDB facade (#219); a favorite whose lookup
        //    fails is dropped rather than failing the whole request.
        const movieRequests = favoriteIds.map((id) => api.dispatch(tmdbApi.endpoints.getMovie.initiate(id)));
        const movieResults = await Promise.all(movieRequests);
        movieRequests.forEach((request) => request.unsubscribe());
        const recentMovies = movieResults
          .filter((result) => !result.error && result.data?.title)
          .map((result) => result.data.title);

        // Distinct from "empty history": the user does have favorites, but every TMDB lookup for
        // them failed (e.g. the facade is down). Sending an empty recentMovies list here would
        // silently look identical to a successful, well-personalized response, so this is reported
        // as an error instead of falling through to the recommendations call below.
        if (recentMovies.length === 0) {
          return {
            error: { status: 'CUSTOM_ERROR', error: 'Could not resolve any favorited movie titles via TMDB' },
          };
        }

        // 4. The actual recommendations call, through the same dynamic base query every other
        //    ai-service endpoint here uses.
        const response = await baseQuery({
          url: '/recommendations',
          method: 'POST',
          body: { recentMovies, count },
        });
        if (response.error) {
          return { error: response.error };
        }
        return { data: { recommendations: response.data?.recommendations ?? [], isEmpty: false } };
      },
    }),
  }),
});

export const {
  useExecuteSearchMutation,
  useParseQueryMutation,
  useGetMovieRecommendationsQuery,
} = aiApi;
