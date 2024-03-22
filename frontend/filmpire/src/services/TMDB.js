import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
//      --url 'https://api.themoviedb.org/3/search/movie?query=Jack+Reacher&api_key=***REDACTED_TMDB_KEY***'

const tmdbApiKey = process.env.REACT_APP_IMDB_KEY;
const page = 1;

export const tmdbApi = createApi({
  reducerPath: 'tmdbApi',
  baseQuery: fetchBaseQuery({ baseUrl: 'https://api.themoviedb.org/3' }),
  endpoints: (builder) => ({
    //* Get Movies by [Type]
    getMovies: builder.query({
      query: () => `/movie/popular?page=${page}&api_key=${tmdbApiKey}`,
    }),
  }),
});

//* This hook is created autmatically by Redux Toolkit Query, and must have excatly this name use+GetMovies+Query
export const {
  useGetMoviesQuery,
} = tmdbApi;
