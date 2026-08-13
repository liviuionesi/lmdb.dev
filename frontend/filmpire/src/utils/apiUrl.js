import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';

const CLOUD_API_URL = 'https://filmpire-api.duckdns.org';
const LOCAL_API_URL = 'http://localhost:8080';
// Published by infrastructure/scripts/start-tunnel.sh or cloud deployment -
// provides instant HTTPS endpoint bypassing any ISP DNS caching.
const TUNNEL_POINTER_URL = 'https://raw.githubusercontent.com/pehlivanu/lmdb.dev/develop/infrastructure/tunnel-url.txt';
const HEALTH_CHECK_TIMEOUT_MS = 2500;
const RESOLUTION_TTL_MS = 30000;

// Module-level cache so repeated calls within the TTL window don't re-probe the network
let resolutionCache = { url: null, expiresAt: 0 };
const backendStatusListeners = new Set();

/**
 * Subscribes a listener to backend status changes.
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
 * @param {string} status - New backend status ('STANDBY' | 'WAKING_UP' | 'ONLINE')
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
 * Resolves the configured backend target provider: 'azure' | 'aws' | 'minikube'.
 *
 * @returns {string} The active backend provider ('azure', 'aws', or 'minikube')
 */
export function getBackendTarget() {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('filmpire_backend_target');
    if (saved && ['azure', 'aws', 'minikube', 'tunnel'].includes(saved)) {
      return saved === 'tunnel' ? 'minikube' : saved;
    }
  }
  const envTarget = import.meta.env?.VITE_BACKEND_TARGET?.toLowerCase();
  if (envTarget && ['azure', 'aws', 'minikube', 'tunnel'].includes(envTarget)) {
    return envTarget === 'tunnel' ? 'minikube' : envTarget;
  }
  return 'azure';
}

/**
 * Saves the selected backend target provider to local storage.
 *
 * @param {string} target - 'azure' | 'aws' | 'minikube'
 */
export function setBackendTarget(target) {
  if (typeof window !== 'undefined') {
    localStorage.setItem('filmpire_backend_target', target);
  }
}

/**
 * Invalidate cached backend resolution to force a fresh network probe.
 */
export function invalidateResolutionCache() {
  resolutionCache = { url: null, expiresAt: 0 };
}

/**
 * Returns manual override URL if explicitly configured.
 *
 * @returns {string|null}
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
  return null;
}

/**
 * Probes `${url}/actuator/health` with a short timeout.
 *
 * @param {string} url - Candidate backend base URL.
 * @returns {Promise<boolean>} True if the backend responded with an ok status.
 */
export async function checkBackendHealth(url) {
  if (!url) return false;
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
 * Fetches the currently-published HTTPS tunnel URL.
 *
 * @returns {Promise<string|null>} The published tunnel URL, or null if unavailable.
 */
export async function fetchPublishedTunnelUrl() {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), HEALTH_CHECK_TIMEOUT_MS);
  try {
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
 * Resolves the backend base URL dynamically with multi-tier fallback:
 * 1. Manual override (localStorage or VITE_API_URL)
 * 2. Localhost:8080 (if running on localhost AND healthy)
 * 3. Default Cloud URL (https://filmpire-api.duckdns.org if healthy)
 * 4. Published HTTPS Tunnel URL (if healthy)
 * 5. Standby fallback
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

  // If on localhost and local backend is alive, use it
  if (typeof window !== 'undefined' && window.location.hostname === 'localhost') {
    if (await checkBackendHealth(LOCAL_API_URL)) {
      resolutionCache = { url: LOCAL_API_URL, expiresAt: now + RESOLUTION_TTL_MS };
      return LOCAL_API_URL;
    }
  }

  // Try cloud backend
  if (await checkBackendHealth(CLOUD_API_URL)) {
    resolutionCache = { url: CLOUD_API_URL, expiresAt: now + RESOLUTION_TTL_MS };
    return CLOUD_API_URL;
  }

  // Fallback to published HTTPS tunnel
  const tunnelUrl = await fetchPublishedTunnelUrl();
  if (tunnelUrl && await checkBackendHealth(tunnelUrl)) {
    resolutionCache = { url: tunnelUrl, expiresAt: now + RESOLUTION_TTL_MS };
    return tunnelUrl;
  }

  // All tiers exhausted — return null so callers know nothing is reachable
  return null;
}

/**
 * Synchronous variant of resolveApiUrl().
 *
 * @returns {string}
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
 * Joins base URL and path.
 *
 * @param {string} base
 * @param {string} path
 * @returns {string}
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
 * Creates dynamic RTK Query baseQuery.
 *
 * @param {string} [pathPrefix]
 * @returns {Function}
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
    const baseUrl = (await resolveApiUrl()) || CLOUD_API_URL;
    const path = typeof args === 'string' ? args : args.url;
    const url = joinUrl(joinUrl(baseUrl, pathPrefix), path);

    const adjustedArgs = typeof args === 'string'
      ? { url }
      : { ...args, url };

    return rawBaseQuery(adjustedArgs, api, extraOptions);
  };
};
