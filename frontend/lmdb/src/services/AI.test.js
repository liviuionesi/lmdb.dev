// Tests aiApi's executeSearch endpoint definition (URL/method/body) by dispatching it against a
// real store with `fetch` mocked, the same way services/user.test.js verifies userApi — RTK Query
// doesn't expose `query` as a directly callable function, and it builds a single whatwg Request
// object, so running the request and reading it back is the only way to verify what it produces.
import { configureStore } from '@reduxjs/toolkit';
import { aiApi } from './AI';

const baseUrl = 'http://localhost:8080/api/v1/ai';
// createDynamicBaseQuery resolves the backend through apiUrl.js's async, health-checked waterfall
// (see apiUrl.test.js) — pinning a static override short-circuits that so the endpoint request is
// always the only fetch call, matching user.test.js's own setup.
const pinStaticApiUrl = () => localStorage.setItem('lmdb_api_url', 'http://localhost:8080');

const buildStore = () => configureStore({
  reducer: { [aiApi.reducerPath]: aiApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(aiApi.middleware),
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
});
