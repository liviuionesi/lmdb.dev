import { createApi } from '@reduxjs/toolkit/query/react';
import { createDynamicBaseQuery } from '../utils/apiUrl';

export const userApi = createApi({
  reducerPath: 'userApi',
  baseQuery: createDynamicBaseQuery('/api/v1'),
  tagTypes: ['Favorites', 'Watchlist'],
  endpoints: (builder) => ({
    //* Register a new LMDB account
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
