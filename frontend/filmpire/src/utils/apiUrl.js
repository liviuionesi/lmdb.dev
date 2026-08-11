import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';

const CLOUD_API_URL = 'https://filmpire-api.duckdns.org';
const LOCAL_API_URL = 'http://localhost:8080';
// Published by infrastructure/scripts/start-tunnel.sh on every restart -
// cloudflared quick tunnels mint a new random hostname each time, so this
// pointer is the only way a deployed frontend can find the *current* one.
const TUNNEL_POINTER_URL = 'https://raw.githubusercontent.com/pehlivanu/filmpire-microservices/develop/infrastructure/tunnel-url.txt';
const HEALTH_CHECK_TIMEOUT_MS = 2500;
const RESOLUTION_TTL_MS = 30000;

// Module-level so repeated calls within the TTL window (e.g. one per RTK
// Query request) don't re-probe the network every time.
let resolutionCache = { url: null, expiresAt: 0 };
const backendStatusListeners = new Set();

/**
 * Subscribes a listener to backend status changes (e.g., 'STANDBY', 'WAKING_UP', 'READY').
 *
 * @param {Function} listener - Callback receiving (status, details)
 * @returns {Function} Unsubscribe function
 */
export function subscribeBackendStatus(listener) {
  backendStatusListeners.add(listener);
  return () => backendStatusListeners.delete(listener);
}

/**
 * Emits a backend status update to all registered subscribers.
 *
 * @param {string} status - New backend status ('STANDBY' | 'WAKING_UP' | 'READY')
 * @param {Object} [details] - Optional payload details
 */
export function notifyBackendStatus(status, details = {}) {
  backendStatusListeners.forEach((listener) => {
    try {
      listener(status, details);
    } catch {
      // Ignore listener errors
    }
  });
}

/**
 * Invalidate cached backend resolution to force a fresh network probe.
 */
export function invalidateResolutionCache() {
  resolutionCache = { url: null, expiresAt: 0 };
}

/**
 * Returns the backend URL fixed by configuration or environment, bypassing
 * any network health checks - a manually-set override, a build-time
 * VITE_API_URL, or this code itself running on localhost.
 *
 * @returns {string|null} The fixed override URL, or null if none applies (health-checked resolution is needed).
 */
function getStaticOverride() {
  if (typeof window === 'undefined') {
    return LOCAL_API_URL;
  }
  const manual = localStorage.getItem('filmpire_api_url');
  if (manual) {
    return manual;
  }
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }
  if (window.location.hostname === 'localhost') {
    return LOCAL_API_URL;
  }
  return null;
}

/**
 * Probes `${url}/actuator/health` with a short timeout, so an unreachable
 * candidate backend can't stall URL resolution.
 *
 * @param {string} [url=CLOUD_API_URL] - Candidate backend base URL.
 * @returns {Promise<boolean>} True if the backend responded with an ok status.
 */
export async function checkBackendHealth(url = CLOUD_API_URL) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEALTH_CHECK_TIMEOUT_MS);
  try {
    const res = await fetch(`${url}/actuator/health`, { method: 'GET', mode: 'cors', signal: controller.signal });
    return res.ok;
  } catch {
    return false;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Dispatches an automated wake-up signal via the serverless /api/wakeup endpoint.
 *
 * @param {string} [cloud='azure'] - Target cloud provider ('azure' or 'aws')
 * @returns {Promise<Object>} The response from the serverless wakeup handler
 */
export async function triggerBackendWakeup(cloud = 'azure') {
  try {
    const res = await fetch('/api/wakeup', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cloud }),
    });
    if (!res.ok) {
      return { status: 'ERROR', message: 'Failed to dispatch wakeup signal.' };
    }
    const data = await res.json();
    notifyBackendStatus(data.status || 'WAKING_UP', data);
    return data;
  } catch {
    return { status: 'ERROR', message: 'Network error contacting wakeup endpoint.' };
  }
}

/**
 * Fetches the local dev tunnel's currently-published public URL.
 *
 * @returns {Promise<string|null>} The published tunnel URL, or null if it's unavailable or malformed.
 */
export async function fetchPublishedTunnelUrl() {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEALTH_CHECK_TIMEOUT_MS);
  try {
    // Cache-bust: raw.githubusercontent.com fronts a CDN that would
    // otherwise happily serve a stale URL for several minutes.
    const res = await fetch(`${TUNNEL_POINTER_URL}?cb=${Date.now()}`, { signal: controller.signal });
    if (!res.ok) {
      return null;
    }
    const text = (await res.text()).trim();
    return text.startsWith('https://') ? text : null;
  } catch {
    return null;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Resolves the backend base URL to actually use, from highest to lowest
 * priority:
 * 1. A manual override saved by the Admin dashboard (localStorage).
 * 2. VITE_API_URL, if fixed at build time.
 * 3. `http://localhost:8080`, when this code itself runs on localhost.
 * 4. The default cloud target, if it passes a health check.
 * 5. The local dev tunnel published by start-tunnel.sh, if it passes a health check.
 * 6. The cloud target anyway, as a last-resort default.
 *
 * Health-checked results are cached for RESOLUTION_TTL_MS so this doesn't
 * re-probe the network on every single request.
 *
 * @returns {Promise<string>} The backend base URL to use.
 */
export async function resolveApiUrl() {
  const staticOverride = getStaticOverride();
  if (staticOverride) {
    return staticOverride;
  }

  const now = Date.now();
  if (resolutionCache.url && now < resolutionCache.expiresAt) {
    return resolutionCache.url;
  }

  // 1. Prefer the cloud backend if it's actually up.
  let resolved = CLOUD_API_URL;
  if (!(await checkBackendHealth(CLOUD_API_URL))) {
    // 2. Cloud is down - fall back to whichever local tunnel is currently published, if reachable.
    const tunnelUrl = await fetchPublishedTunnelUrl();
    if (tunnelUrl && await checkBackendHealth(tunnelUrl)) {
      resolved = tunnelUrl;
    } else {
      // Both cloud and tunnel are down; notify subscribers that backend is in standby
      notifyBackendStatus('STANDBY', { cloudUrl: CLOUD_API_URL });
    }
  }

  resolutionCache = { url: resolved, expiresAt: now + RESOLUTION_TTL_MS };
  return resolved;
}

/**
 * Synchronous best-effort variant of resolveApiUrl(), for call sites that
 * can't await (e.g. render-time reads). Returns the last health-checked
 * result if one is cached, otherwise a static override or the cloud
 * default, and kicks off a background resolution so the next call benefits
 * from an up-to-date, health-checked value.
 *
 * @returns {string} The best currently-known backend base URL.
 */
export const getApiUrl = () => {
  const staticOverride = getStaticOverride();
  if (staticOverride) {
    return staticOverride;
  }

  if (resolutionCache.url && Date.now() < resolutionCache.expiresAt) {
    return resolutionCache.url;
  }

  resolveApiUrl().catch(() => {});
  return CLOUD_API_URL;
};

/**
 * Joins a base URL and a path with exactly one `/` between them, regardless
 * of whether either side already has a leading/trailing slash - endpoint
 * `query` builders across services are inconsistent about this.
 *
 * @param {string} base - The left-hand side (a base URL or already-joined prefix).
 * @param {string} path - The right-hand side to append.
 * @returns {string} The joined URL.
 */
function joinUrl(base, path) {
  if (!path) {
    return base;
  }
  const trimmedBase = base.endsWith('/') ? base.slice(0, -1) : base;
  const trimmedPath = path.startsWith('/') ? path.slice(1) : path;
  return `${trimmedBase}/${trimmedPath}`;
}

/**
 * Creates a dynamic RTK Query baseQuery that resolves the backend URL at
 * request time (so it always targets whichever backend is currently up) and
 * automatically attaches an Authorization header when a JWT is present.
 *
 * @param {string} [pathPrefix] - Path segment inserted between the resolved backend URL and each endpoint's own path (e.g. '/api/v1'); pass '' for endpoints that already include the full path, like the TMDB-shaped facade routes.
 * @returns {Function} An RTK Query baseQuery function.
 */
export const createDynamicBaseQuery = (pathPrefix = '/api/v1') => {
  const rawBaseQuery = fetchBaseQuery({
    prepareHeaders: (headers) => {
      const accessToken = typeof window !== 'undefined' ? localStorage.getItem('access_token') : null;
      if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`);
      }
      return headers;
    },
  });

  return async (args, api, extraOptions) => {
    const baseUrl = await resolveApiUrl();
    const path = typeof args === 'string' ? args : args.url;
    const url = joinUrl(joinUrl(baseUrl, pathPrefix), path);

    const adjustedArgs = typeof args === 'string'
      ? { url }
      : { ...args, url };

    return rawBaseQuery(adjustedArgs, api, extraOptions);
  };
};
