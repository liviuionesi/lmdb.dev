// Tests apiUrl.js's backend URL resolution waterfall and the joinUrl logic
// exercised through createDynamicBaseQuery. Each network-waterfall test
// resets modules (vi.resetModules + dynamic import) so the internal
// resolution cache from one test can't leak into the next - the cache is
// intentionally module-level, not exported, to keep resolveApiUrl()'s
// public contract simple. VITE_API_URL is explicitly stubbed empty in every
// test so local dev's own .env.local (which sets it to localhost:8080)
// can't shadow the branch actually under test.
const stubNonLocalhost = () => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { hostname: 'lmdb.dev' },
  });
};

const restoreLocalhost = () => {
  Object.defineProperty(window, 'location', {
    writable: true,
    value: { hostname: 'localhost' },
  });
};

describe('apiUrl static overrides (no network involved)', () => {
  let getApiUrl;
  let resolveApiUrl;

  beforeEach(async () => {
    vi.resetModules();
    vi.stubEnv('VITE_API_URL', '');
    localStorage.clear();
    restoreLocalhost();
    ({ getApiUrl, resolveApiUrl } = await import('./apiUrl'));
  });

  afterEach(() => {
    localStorage.clear();
    delete global.fetch;
    vi.unstubAllEnvs();
  });

  it('getApiUrl and resolveApiUrl both return the manual localStorage override when set', async () => {
    localStorage.setItem('lmdb_api_url', 'https://manually-pinned.example.com');

    expect(getApiUrl()).toBe('https://manually-pinned.example.com');
    await expect(resolveApiUrl()).resolves.toBe('https://manually-pinned.example.com');
  });

  it('prefers VITE_API_URL when set at build time', async () => {
    vi.stubEnv('VITE_API_URL', 'https://pinned-build-target.example.com');
    ({ getApiUrl, resolveApiUrl } = await import('./apiUrl'));

    expect(getApiUrl()).toBe('https://pinned-build-target.example.com');
    await expect(resolveApiUrl()).resolves.toBe('https://pinned-build-target.example.com');
  });
});

describe('getBackendTarget and setBackendTarget resolution', () => {
  beforeEach(() => {
    vi.resetModules();
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
  });

  it('defaults to azure when no env or localStorage is configured', async () => {
    const { getBackendTarget } = await import('./apiUrl');
    expect(getBackendTarget()).toBe('azure');
  });

  it('respects VITE_BACKEND_TARGET environment variable', async () => {
    vi.stubEnv('VITE_BACKEND_TARGET', 'aws');
    const { getBackendTarget } = await import('./apiUrl');
    expect(getBackendTarget()).toBe('aws');
  });

  it('normalizes tunnel to minikube', async () => {
    vi.stubEnv('VITE_BACKEND_TARGET', 'tunnel');
    const { getBackendTarget } = await import('./apiUrl');
    expect(getBackendTarget()).toBe('minikube');
  });

  it('prefers localStorage override over env variable', async () => {
    vi.stubEnv('VITE_BACKEND_TARGET', 'aws');
    const { getBackendTarget, setBackendTarget } = await import('./apiUrl');
    setBackendTarget('minikube');
    expect(getBackendTarget()).toBe('minikube');
  });
});

describe('apiUrl health-checked waterfall (non-localhost)', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_API_URL', '');
    localStorage.clear();
    stubNonLocalhost();
  });

  afterEach(() => {
    localStorage.clear();
    restoreLocalhost();
    delete global.fetch;
    vi.unstubAllEnvs();
  });

  it('resolves to the cloud URL when its health check succeeds', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true });
    const { resolveApiUrl } = await import('./apiUrl');

    await expect(resolveApiUrl()).resolves.toBe('https://api.lmdb.dev');
    expect(global.fetch).toHaveBeenCalledWith(
      'https://api.lmdb.dev/actuator/health',
      expect.objectContaining({ method: 'GET' }),
    );
  });

  it('falls back to the published tunnel URL when the cloud health check fails and the tunnel is healthy', async () => {
    global.fetch = vi.fn().mockImplementation((url) => {
      if (url.startsWith('https://api.lmdb.dev')) {
        return Promise.reject(new Error('unreachable'));
      }
      if (url.startsWith('https://raw.githubusercontent.com')) {
        return Promise.resolve({ ok: true, text: () => Promise.resolve('https://current-tunnel.trycloudflare.com') });
      }
      if (url.startsWith('https://current-tunnel.trycloudflare.com')) {
        return Promise.resolve({ ok: true });
      }
      throw new Error(`unexpected fetch: ${url}`);
    });
    const { resolveApiUrl } = await import('./apiUrl');

    await expect(resolveApiUrl()).resolves.toBe('https://current-tunnel.trycloudflare.com');
  });

  it('returns null when both cloud and tunnel are unreachable', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('unreachable'));
    const { resolveApiUrl } = await import('./apiUrl');

    await expect(resolveApiUrl()).resolves.toBeNull();
  });

  it('caches a health-checked resolution instead of re-probing on every call', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true });
    const { resolveApiUrl } = await import('./apiUrl');

    await resolveApiUrl();
    await resolveApiUrl();

    expect(global.fetch).toHaveBeenCalledTimes(1);
  });
});

describe('createDynamicBaseQuery URL joining', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.stubEnv('VITE_API_URL', '');
    localStorage.clear();
    restoreLocalhost();
  });

  afterEach(() => {
    localStorage.clear();
    delete global.fetch;
    vi.unstubAllEnvs();
  });

  it('joins baseUrl, prefix and path with exactly one slash regardless of leading/trailing slashes', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({}),
      text: async () => '{}',
      clone() { return this; },
    });
    const { createDynamicBaseQuery } = await import('./apiUrl');
    const baseQuery = createDynamicBaseQuery('/api/v1');

    await baseQuery('/media/upload', {}, {});

    const lastCall = global.fetch.mock.calls[global.fetch.mock.calls.length - 1][0];
    const requestUrl = typeof lastCall === 'string' ? lastCall : lastCall?.url;
    expect(requestUrl).toBe('http://localhost:8080/api/v1/media/upload');
  });

  it('treats an empty prefix as pass-through, for endpoints whose query already includes the full path', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      headers: new Headers({ 'content-type': 'application/json' }),
      json: async () => ({}),
      text: async () => '{}',
      clone() { return this; },
    });
    const { createDynamicBaseQuery } = await import('./apiUrl');
    const baseQuery = createDynamicBaseQuery('');

    await baseQuery('genre/movie/list?api_key=abc', {}, {});

    const lastCall = global.fetch.mock.calls[global.fetch.mock.calls.length - 1][0];
    const requestUrl = typeof lastCall === 'string' ? lastCall : lastCall?.url;
    expect(requestUrl).toBe('http://localhost:8080/genre/movie/list?api_key=abc');
  });
});
