// Tests the TMDB RTK Query endpoints by dispatching them against a real store
// with `fetch` mocked — this exercises the real query-URL builders (RTK Query
// doesn't expose `query` as a callable function on the built endpoint object,
// so the only way to verify the URL a builder produces is to actually run it).
import { configureStore } from '@reduxjs/toolkit';
import { tmdbApi } from './TMDB';

const apiKey = import.meta.env.VITE_TMDB_KEY;
const baseUrl = import.meta.env.VITE_API_URL || 'https://api.themoviedb.org/3';

const buildStore = () => configureStore({
  reducer: { [tmdbApi.reducerPath]: tmdbApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(tmdbApi.middleware),
});

const jsonResponse = (body) => ({
  ok: true,
  status: 200,
  headers: new Headers({ 'content-type': 'application/json' }),
  json: async () => body,
  text: async () => JSON.stringify(body),
  clone() { return this; },
});

describe('tmdbApi endpoint query builders', () => {
  let store;

  beforeEach(() => {
    store = buildStore();
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ ok: true }));
  });

  afterEach(() => {
    delete global.fetch;
  });

  // fetchBaseQuery passes fetch a Request object (not a plain string) once
  // headers are involved, so the URL has to be read off `.url`.
  const fetchedUrl = () => {
    const [request] = global.fetch.mock.calls[0];
    return typeof request === 'string' ? request : request.url;
  };

  it('getGenres requests the genre list', async () => {
    await store.dispatch(tmdbApi.endpoints.getGenres.initiate());
    expect(fetchedUrl()).toBe(`${baseUrl}/genre/movie/list?api_key=${apiKey}`);
  });

  it('getMovies searches by query when a searchQuery is present', async () => {
    await store.dispatch(tmdbApi.endpoints.getMovies.initiate({ genreIdOrCategoryName: '', page: 2, searchQuery: 'batman' }));
    expect(fetchedUrl()).toBe(`${baseUrl}/search/movie?query=batman&page=2&api_key=${apiKey}`);
  });

  it('getMovies searches by query even when a genre/category is also set (search wins)', async () => {
    await store.dispatch(tmdbApi.endpoints.getMovies.initiate({ genreIdOrCategoryName: 'popular', page: 1, searchQuery: 'batman' }));
    expect(fetchedUrl()).toBe(`${baseUrl}/search/movie?query=batman&page=1&api_key=${apiKey}`);
  });

  it('getMovies requests a fixed category when genreIdOrCategoryName is a string', async () => {
    await store.dispatch(tmdbApi.endpoints.getMovies.initiate({ genreIdOrCategoryName: 'top_rated', page: 3, searchQuery: '' }));
    expect(fetchedUrl()).toBe(`${baseUrl}/movie/top_rated?page=3&api_key=${apiKey}`);
  });

  it('getMovies requests a genre discover query when genreIdOrCategoryName is numeric', async () => {
    await store.dispatch(tmdbApi.endpoints.getMovies.initiate({ genreIdOrCategoryName: 28, page: 1, searchQuery: '' }));
    expect(fetchedUrl()).toBe(`${baseUrl}/discover/movie?with_genres=28&page=1&api_key=${apiKey}`);
  });

  it('getMovies falls back to popular movies with no genre/category/search', async () => {
    await store.dispatch(tmdbApi.endpoints.getMovies.initiate({ genreIdOrCategoryName: '', page: 1, searchQuery: '' }));
    expect(fetchedUrl()).toBe(`${baseUrl}/movie/popular?page=1&api_key=${apiKey}`);
  });

  it('getMovie requests a single movie with videos/credits appended', async () => {
    await store.dispatch(tmdbApi.endpoints.getMovie.initiate(550));
    expect(fetchedUrl()).toBe(`${baseUrl}/movie/550?append_to_response=videos,credits&api_key=${apiKey}`);
  });

  it('getRecommendations requests the given list for a movie', async () => {
    await store.dispatch(tmdbApi.endpoints.getRecommendations.initiate({ movie_id: 550, list: '/recommendations' }));
    expect(fetchedUrl()).toBe(`${baseUrl}/movie/550/recommendations?api_key=${apiKey}`);
  });

  it('getActorsDetails requests a person by id', async () => {
    await store.dispatch(tmdbApi.endpoints.getActorsDetails.initiate(42));
    expect(fetchedUrl()).toBe(`${baseUrl}/person/42?api_key=${apiKey}`);
  });

  it('getMoviesByActorId requests movies discovered by cast id and page', async () => {
    await store.dispatch(tmdbApi.endpoints.getMoviesByActorId.initiate({ id: 42, page: 2 }));
    expect(fetchedUrl()).toBe(`${baseUrl}/discover/movie?with_cast=42&page=2&api_key=${apiKey}`);
  });
});
