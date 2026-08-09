// Tests userApi's endpoint definitions (URL/method/body, transformResponse,
// and the Authorization-header injection in prepareHeaders) by dispatching
// each endpoint against a real store with `fetch` mocked. RTK Query doesn't
// expose `query` as a callable on the built endpoint object, and it calls
// fetch with a single whatwg Request object (not separate url/init args), so
// actually running the request and reading it back is the only way to
// verify what an endpoint produces.
import { configureStore } from '@reduxjs/toolkit';
import { userApi } from './user';

const baseUrl = 'http://localhost:8080/api/v1';

const buildStore = () => configureStore({
  reducer: { [userApi.reducerPath]: userApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(userApi.middleware),
});

const jsonResponse = (body) => ({
  ok: true,
  status: 200,
  headers: new Headers({ 'content-type': 'application/json' }),
  json: async () => body,
  text: async () => JSON.stringify(body),
  clone() { return this; },
});

describe('userApi endpoints', () => {
  let store;

  beforeEach(() => {
    store = buildStore();
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { id: 1 } }));
  });

  afterEach(() => {
    delete global.fetch;
    localStorage.clear();
  });

  const fetchedRequest = () => global.fetch.mock.calls[0][0];

  it('register posts the registration form to /auth/register', async () => {
    const body = { username: 'liviu', email: 'l@example.com', password: 'pw' };
    await store.dispatch(userApi.endpoints.register.initiate(body));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/auth/register`);
    expect(request.method).toBe('POST');
    expect(JSON.parse(await request.text())).toEqual(body);
  });

  it('login posts credentials to /auth/login', async () => {
    const body = { username: 'liviu', password: 'pw' };
    await store.dispatch(userApi.endpoints.login.initiate(body));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/auth/login`);
    expect(request.method).toBe('POST');
  });

  it('logout posts to /auth/logout with no body', async () => {
    await store.dispatch(userApi.endpoints.logout.initiate());

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/auth/logout`);
    expect(request.method).toBe('POST');
  });

  it('getProfile reads /users/profile and unwraps the response envelope', async () => {
    const result = await store.dispatch(userApi.endpoints.getProfile.initiate());

    expect(fetchedRequest().url).toBe(`${baseUrl}/users/profile`);
    expect(result.data).toEqual({ id: 1 });
  });

  it('getFavorites reads /users/favorites', async () => {
    await store.dispatch(userApi.endpoints.getFavorites.initiate());
    expect(fetchedRequest().url).toBe(`${baseUrl}/users/favorites`);
  });

  it('addFavorite posts the movie id', async () => {
    await store.dispatch(userApi.endpoints.addFavorite.initiate(550));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/users/favorites/550`);
    expect(request.method).toBe('POST');
  });

  it('removeFavorite deletes the movie id', async () => {
    await store.dispatch(userApi.endpoints.removeFavorite.initiate(550));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/users/favorites/550`);
    expect(request.method).toBe('DELETE');
  });

  it('getWatchlist reads /users/watchlist', async () => {
    await store.dispatch(userApi.endpoints.getWatchlist.initiate());
    expect(fetchedRequest().url).toBe(`${baseUrl}/users/watchlist`);
  });

  it('addToWatchlist posts the movie id', async () => {
    await store.dispatch(userApi.endpoints.addToWatchlist.initiate(550));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/users/watchlist/550`);
    expect(request.method).toBe('POST');
  });

  it('removeFromWatchlist deletes the movie id', async () => {
    await store.dispatch(userApi.endpoints.removeFromWatchlist.initiate(550));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/users/watchlist/550`);
    expect(request.method).toBe('DELETE');
  });
});

describe('userApi baseQuery Authorization header injection', () => {
  const buildAuthStore = () => configureStore({
    reducer: { [userApi.reducerPath]: userApi.reducer },
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(userApi.middleware),
  });

  afterEach(() => {
    localStorage.clear();
    delete global.fetch;
  });

  it('attaches a Bearer Authorization header when an access token is stored', async () => {
    localStorage.setItem('access_token', 'my-jwt');
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: { id: 1 } }));

    await buildAuthStore().dispatch(userApi.endpoints.getProfile.initiate());

    const request = global.fetch.mock.calls[0][0];
    expect(request.headers.get('authorization')).toBe('Bearer my-jwt');
  });

  it('omits the Authorization header when no access token is stored', async () => {
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: {} }));

    await buildAuthStore().dispatch(userApi.endpoints.getProfile.initiate());

    const request = global.fetch.mock.calls[0][0];
    expect(request.headers.has('authorization')).toBe(false);
  });
});
