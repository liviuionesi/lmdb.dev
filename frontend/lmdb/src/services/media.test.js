// Tests mediaApi's endpoint definitions (URL/method/body, the optional
// `description` field in uploadMedia's FormData, and the Authorization-header
// injection in prepareHeaders) by dispatching each endpoint against a real
// store with `fetch` mocked — same approach as user.test.js. RTK Query
// doesn't expose `query` as a callable on the built endpoint object, and it
// calls fetch with a single whatwg Request object (not separate url/init
// args), so actually running the request and reading it back is the only way
// to verify what an endpoint produces.
import { configureStore } from '@reduxjs/toolkit';
import { mediaApi, getMediaUrl, buildUploadMediaQuery } from './media';

const baseUrl = 'http://localhost:8080/api/v1';
// createDynamicBaseQuery resolves the backend through apiUrl.js's async,
// health-checked waterfall (see apiUrl.test.js) - left to run on its own it
// would issue a real fetch() health probe before the endpoint request,
// stealing mock.calls[0] out from under fetchedRequest() below. Pinning the
// manual localStorage override short-circuits that waterfall synchronously
// so the endpoint request is always the only fetch call.
const pinStaticApiUrl = () => localStorage.setItem('lmdb_api_url', 'http://localhost:8080');

const buildStore = () => configureStore({
  reducer: { [mediaApi.reducerPath]: mediaApi.reducer },
  middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(mediaApi.middleware),
});

const jsonResponse = (body) => ({
  ok: true,
  status: 200,
  headers: new Headers({ 'content-type': 'application/json' }),
  json: async () => body,
  text: async () => JSON.stringify(body),
  clone() { return this; },
});

describe('mediaApi endpoints', () => {
  let store;

  beforeEach(() => {
    pinStaticApiUrl();
    store = buildStore();
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: {} }));
  });

  afterEach(() => {
    delete global.fetch;
    localStorage.clear();
  });

  const fetchedRequest = () => global.fetch.mock.calls[0][0];

  it('getMediaUrl helper returns null when URL is empty or undefined', () => {
    expect(getMediaUrl(null)).toBeNull();
    expect(getMediaUrl(undefined)).toBeNull();
    expect(getMediaUrl('')).toBeNull();
  });

  it('getMediaUrl helper returns original URL when already absolute', () => {
    const absoluteUrl = 'http://external-storage.com/photo.jpg';
    expect(getMediaUrl(absoluteUrl)).toBe(absoluteUrl);
  });

  it('getMediaUrl helper prepends the API gateway URL when URL is relative', () => {
    const relativeUrl = '/api/v1/media/uuid-123/download';
    expect(getMediaUrl(relativeUrl)).toBe(`${baseUrl.replace('/api/v1', '')}${relativeUrl}`);
  });

  describe('buildUploadMediaQuery query builder', () => {
    it('builds multipart query defaulting optional fields and omitting description', () => {
      const file = new File(['dummy content'], 'avatar.png', { type: 'image/png' });
      const queryResult = buildUploadMediaQuery({ file });

      expect(queryResult.url).toBe('/media/upload');
      expect(queryResult.method).toBe('POST');
      expect(queryResult.body).toBeInstanceOf(FormData);
      expect(queryResult.body.get('file')).toBe(file);
      expect(queryResult.body.get('entityId')).toBe('general');
      expect(queryResult.body.get('entityType')).toBe('USER');
      expect(queryResult.body.get('mediaType')).toBe('IMAGE');
      expect(queryResult.body.get('uploadedBy')).toBe('anonymous');
      expect(queryResult.body.has('description')).toBe(false);
    });

    it('builds multipart query with custom metadata and description included', () => {
      const file = new File(['dummy content'], 'review.png', { type: 'image/png' });
      const queryResult = buildUploadMediaQuery({
        file,
        entityId: '123',
        entityType: 'REVIEW',
        mediaType: 'ATTACHMENT',
        uploadedBy: 'liviu',
        description: 'Screenshot of the bug',
      });

      expect(queryResult.url).toBe('/media/upload');
      expect(queryResult.method).toBe('POST');
      expect(queryResult.body).toBeInstanceOf(FormData);
      expect(queryResult.body.get('file')).toBe(file);
      expect(queryResult.body.get('entityId')).toBe('123');
      expect(queryResult.body.get('entityType')).toBe('REVIEW');
      expect(queryResult.body.get('mediaType')).toBe('ATTACHMENT');
      expect(queryResult.body.get('uploadedBy')).toBe('liviu');
      expect(queryResult.body.get('description')).toBe('Screenshot of the bug');
    });
  });

  it('uploadMedia dispatches request to /media/upload with POST method', async () => {
    const file = new File(['dummy content'], 'avatar.png', { type: 'image/png' });

    await store.dispatch(mediaApi.endpoints.uploadMedia.initiate({ file }));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/media/upload`);
    expect(request.method).toBe('POST');
    expect(request.headers.get('content-type')).toContain('multipart/form-data');
  });

  it('getMediaForEntity reads /media/entity/:id', async () => {
    await store.dispatch(mediaApi.endpoints.getMediaForEntity.initiate('42'));

    expect(fetchedRequest().url).toBe(`${baseUrl}/media/entity/42`);
  });

  it('deleteMedia deletes /media/:id', async () => {
    await store.dispatch(mediaApi.endpoints.deleteMedia.initiate('media-1'));

    const request = fetchedRequest();
    expect(request.url).toBe(`${baseUrl}/media/media-1`);
    expect(request.method).toBe('DELETE');
  });
});

describe('mediaApi baseQuery Authorization header injection', () => {
  const buildAuthStore = () => configureStore({
    reducer: { [mediaApi.reducerPath]: mediaApi.reducer },
    middleware: (getDefaultMiddleware) => getDefaultMiddleware().concat(mediaApi.middleware),
  });

  afterEach(() => {
    localStorage.clear();
    delete global.fetch;
  });

  it('attaches a Bearer Authorization header when an access token is stored', async () => {
    pinStaticApiUrl();
    localStorage.setItem('access_token', 'my-jwt');
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: {} }));

    await buildAuthStore().dispatch(mediaApi.endpoints.getMediaForEntity.initiate('42'));

    const request = global.fetch.mock.calls[0][0];
    expect(request.headers.get('authorization')).toBe('Bearer my-jwt');
  });

  it('omits the Authorization header when no access token is stored', async () => {
    pinStaticApiUrl();
    global.fetch = vi.fn().mockResolvedValue(jsonResponse({ data: {} }));

    await buildAuthStore().dispatch(mediaApi.endpoints.getMediaForEntity.initiate('42'));

    const request = global.fetch.mock.calls[0][0];
    expect(request.headers.has('authorization')).toBe(false);
  });
});
