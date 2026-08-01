import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';

// user-service is Filmpire's own account API (JWT auth + favorites/watchlist)
// and has no real-TMDB equivalent, so unlike services/TMDB.js this always
// targets the local gateway rather than falling back to api.themoviedb.org.
const filmpireApiUrl = process.env.REACT_APP_API_URL || 'http://localhost:8080';

export const userApi = createApi({
  reducerPath: 'userApi',
  baseQuery: fetchBaseQuery({
    baseUrl: `${filmpireApiUrl}/api/v1`,
    prepareHeaders: (headers) => {
      const accessToken = localStorage.getItem('access_token');

      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
      }

      return headers;
    },
  }),
  tagTypes: ['Favorites', 'Watchlist'],
  endpoints: (builder) => ({
    //* Register a new Filmpire account
    register: builder.mutation({
      query: (body) => ({ url: '/auth/register', method: 'POST', body }),
      transformResponse: (response) => response.data,
    }),

    //* Authenticate with username/password
    login: builder.mutation({
      query: (body) => ({ url: '/auth/login', method: 'POST', body }),
      transformResponse: (response) => response.data,
    }),

    //* Revoke the caller's refresh tokens
    logout: builder.mutation({
      query: () => ({ url: '/auth/logout', method: 'POST' }),
    }),

    //* Get the authenticated user's profile
    getProfile: builder.query({
      query: () => '/users/profile',
      transformResponse: (response) => response.data,
    }),

    //* List favorite movies (TMDB ids)
    getFavorites: builder.query({
      query: () => '/users/favorites',
      transformResponse: (response) => response.data,
      providesTags: ['Favorites'],
    }),

    addFavorite: builder.mutation({
      query: (movieId) => ({ url: `/users/favorites/${movieId}`, method: 'POST' }),
      invalidatesTags: ['Favorites'],
    }),

    removeFavorite: builder.mutation({
      query: (movieId) => ({ url: `/users/favorites/${movieId}`, method: 'DELETE' }),
      invalidatesTags: ['Favorites'],
    }),

    //* List watchlisted movies (TMDB ids)
    getWatchlist: builder.query({
      query: () => '/users/watchlist',
      transformResponse: (response) => response.data,
      providesTags: ['Watchlist'],
    }),

    addToWatchlist: builder.mutation({
      query: (movieId) => ({ url: `/users/watchlist/${movieId}`, method: 'POST' }),
      invalidatesTags: ['Watchlist'],
    }),

    removeFromWatchlist: builder.mutation({
      query: (movieId) => ({ url: `/users/watchlist/${movieId}`, method: 'DELETE' }),
      invalidatesTags: ['Watchlist'],
    }),
  }),
});

export const {
  useRegisterMutation,
  useLoginMutation,
  useLogoutMutation,
  useGetProfileQuery,
  useGetFavoritesQuery,
  useAddFavoriteMutation,
  useRemoveFavoriteMutation,
  useGetWatchlistQuery,
  useAddToWatchlistMutation,
  useRemoveFromWatchlistMutation,
} = userApi;
