// Tests aiApi's endpoint definitions (URL/method/body) by dispatching them against a real store
// with `fetch` mocked, the same way services/user.test.js verifies userApi — RTK Query doesn't
// expose `query` as a directly callable function, and it builds a single whatwg Request object, so
// running the request and reading it back is the only way to verify what it produces.
import { configureStore } from '@reduxjs/toolkit';
import { aiApi } from './AI';
import { userApi } from './user';
import { tmdbApi } from './TMDB';

const baseUrl = 'http://localhost:8080/api/v1/ai';
// createDynamicBaseQuery resolves the backend through apiUrl.js's async, health-checked waterfall
// (see apiUrl.test.js) — pinning a static override short-circuits that so the endpoint request is
// always the only fetch call, matching user.test.js's own setup.
const pinStaticApiUrl = () => localStorage.setItem('lmdb_api_url', 'http://localhost:8080');

const buildStore = () => configureStore({
  reducer: { [aiApi.reducerPath]: aiApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(aiApi.middleware),
});

// getMovieRecommendations' queryFn also dispatches userApi.getFavorites and tmdbApi.getMovie, so
// its store needs those two APIs mounted as well — the plain aiApi-only store above is enough for
// executeSearch/parseQuery but would throw "reducerPath not found" for this endpoint.
const buildRecommendationsStore = () => configureStore({
  reducer: {
    [aiApi.reducerPath]: aiApi.reducer,
    [userApi.reducerPath]: userApi.reducer,
    [tmdbApi.reducerPath]: tmdbApi.reducer,
  },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(
    aiApi.middleware,
    userApi.middleware,
    tmdbApi.middleware,
  ),
});

const jsonResponse = (body) => ({
  ok: true,
  status: 200,
  headers: new Headers({ 'content-type': 'application/json' }),
  json: async () => body,
  text: async () => JSON.stringify(body),
  clone() { return this; },
});

describe('aiApi endpoints', () => {
  let store;

  beforeEach(() => {
    pinStaticApiUrl();
    store = buildStore();
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ results: [] }));
  });

  afterEach(() => {
    delete global.fetch;
    localStorage.clear();
  });

  it('executeSearch posts the raw query to /search/execute', async () => {
    await store.dispatch(aiApi.endpoints.executeSearch.initiate('movies Tom Hanks directed'));

    const request = global.fetch.mock.calls[0][0];
    expect(request.url).toBe(`${baseUrl}/search/execute`);
    expect(request.method).toBe('POST');
    expect(JSON.parse(await request.text())).toEqual({ query: 'movies Tom Hanks directed' });
  });

  it('executeSearch resolves with the response body unwrapped (no ApiResponse envelope)', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({
      results: [{ movieId: 550, title: 'Fight Club' }],
    }));

    const result = await store.dispatch(aiApi.endpoints.executeSearch.initiate('Fight Club'));

    expect(result.data).toEqual({ results: [{ movieId: 550, title: 'Fight Club' }] });
  });

  it('parseQuery posts the raw query to /search/query, not /search/execute (#208)', async () => {
    await store.dispatch(aiApi.endpoints.parseQuery.initiate('movies Tom Hanks directed'));

    const request = global.fetch.mock.calls[0][0];
    expect(request.url).toBe(`${baseUrl}/search/query`);
    expect(request.method).toBe('POST');
    expect(JSON.parse(await request.text())).toEqual({ query: 'movies Tom Hanks directed' });
  });

  it('parseQuery resolves with the filter/spans response body unwrapped', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({
      filter: null,
      spans: [{ text: 'and', category: 'CONNECTOR', start: 5, end: 8 }],
    }));

    const result = await store.dispatch(aiApi.endpoints.parseQuery.initiate('Batman and Robin'));

    expect(result.data).toEqual({
      filter: null,
      spans: [{ text: 'and', category: 'CONNECTOR', start: 5, end: 8 }],
    });
  });
});

describe('aiApi.getMovieRecommendations (#220)', () => {
  // Routes the shared fetch mock by URL substring, since one dispatch of getMovieRecommendations
  // fans out into three real requests: favorites, then one TMDB lookup per favorite, then the
  // actual recommendations POST.
  const routeFetch = (routes) => vi.fn((request) => {
    const match = Object.entries(routes).find(([urlFragment]) => request.url.includes(urlFragment));
    if (!match) {
      return Promise.reject(new Error(`unexpected fetch: ${request.url}`));
    }
    return Promise.resolve(jsonResponse(match[1]));
  });

  beforeEach(() => {
    pinStaticApiUrl();
  });

  afterEach(() => {
    delete global.fetch;
    localStorage.clear();
  });

  it('resolves favorites to titles via TMDB and posts them as recentMovies, plus count', async () => {
    global.fetch = routeFetch({
      '/users/favorites': { data: [{ movieId: 550 }, { movieId: 680 }] },
      '/movie/550': { id: 550, title: 'Fight Club' },
      '/movie/680': { id: 680, title: 'Pulp Fiction' },
      '/recommendations': { recommendations: [{ movieId: '27205', score: 0.9, reason: 'Similar tone' }] },
    });

    const result = await buildRecommendationsStore()
      .dispatch(aiApi.endpoints.getMovieRecommendations.initiate(5));

    const recommendationsCall = global.fetch.mock.calls
      .map(([request]) => request)
      .find((request) => request.url.includes('/recommendations'));
    expect(recommendationsCall.method).toBe('POST');
    expect(JSON.parse(await recommendationsCall.text())).toEqual({
      recentMovies: ['Fight Club', 'Pulp Fiction'],
      count: 5,
    });
    expect(result.data).toEqual({
      recommendations: [{ movieId: '27205', score: 0.9, reason: 'Similar tone' }],
      isEmpty: false,
    });
  });

  it('reports isEmpty and never calls /recommendations when the user has no favorites (#219)', async () => {
    global.fetch = routeFetch({ '/users/favorites': { data: [] } });

    const result = await buildRecommendationsStore()
      .dispatch(aiApi.endpoints.getMovieRecommendations.initiate());

    expect(result.data).toEqual({ recommendations: [], isEmpty: true });
    expect(global.fetch.mock.calls.some(([request]) => request.url.includes('/recommendations'))).toBe(false);
  });

  it('surfaces the favorites-fetch error without calling /recommendations', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({ message: 'boom' }),
      text: async () => JSON.stringify({ message: 'boom' }),
      clone() { return this; },
    });

    const result = await buildRecommendationsStore()
      .dispatch(aiApi.endpoints.getMovieRecommendations.initiate());

    expect(result.error).toBeDefined();
    expect(global.fetch.mock.calls.some(([request]) => request.url.includes('/recommendations'))).toBe(false);
  });

  it('drops a favorite whose TMDB lookup fails instead of failing the whole request', async () => {
    global.fetch = vi.fn((request) => {
      if (request.url.includes('/users/favorites')) {
        return Promise.resolve(jsonResponse({ data: [{ movieId: 550 }, { movieId: 999 }] }));
      }
      if (request.url.includes('/movie/550')) {
        return Promise.resolve(jsonResponse({ id: 550, title: 'Fight Club' }));
      }
      if (request.url.includes('/movie/999')) {
        return Promise.resolve({
          ok: false,
          status: 404,
          headers: new Headers({ 'content-type': 'application/json' }),
          json: async () => ({ message: 'not found' }),
          text: async () => JSON.stringify({ message: 'not found' }),
          clone() { return this; },
        });
      }
      if (request.url.includes('/recommendations')) {
        return Promise.resolve(jsonResponse({ recommendations: [] }));
      }
      return Promise.reject(new Error(`unexpected fetch: ${request.url}`));
    });

    await buildRecommendationsStore().dispatch(aiApi.endpoints.getMovieRecommendations.initiate());

    const recommendationsCall = global.fetch.mock.calls
      .map(([request]) => request)
      .find((request) => request.url.includes('/recommendations'));
    expect(JSON.parse(await recommendationsCall.text())).toEqual({ recentMovies: ['Fight Club'] });
  });
});
