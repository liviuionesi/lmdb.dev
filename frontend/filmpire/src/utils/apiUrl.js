import { fetchBaseQuery } from '@reduxjs/toolkit/query/react';

/**
 * Resolves the backend API Gateway URL dynamically at runtime.
 * Prioritizes:
 * 1. localStorage.getItem('filmpire_api_url') (set dynamically by Admin Deploy / Tunnel)
 * 2. import.meta.env.VITE_API_URL (configured at build/deployment time)
 * 3. Production fallback: 'https://filmpire-api.duckdns.org'
 * 4. Local fallback: 'http://localhost:8080'
 */
export const getApiUrl = () => {
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('filmpire_api_url');
    if (saved) return saved;
  }
  if (import.meta.env.VITE_API_URL) {
    return import.meta.env.VITE_API_URL;
  }
  if (typeof window !== 'undefined' && window.location && window.location.hostname !== 'localhost') {
    return 'https://filmpire-api.duckdns.org';
  }
  return 'http://localhost:8080';
};

/**
 * Creates a dynamic RTK Query baseQuery that resolves the backend URL at request time.
 * Automatically attaches Authorization header if JWT token is present.
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
    const baseUrl = getApiUrl();
    const url = typeof args === 'string'
      ? `${baseUrl}${pathPrefix}${args}`
      : `${baseUrl}${pathPrefix}${args.url}`;

    const adjustedArgs = typeof args === 'string'
      ? { url }
      : { ...args, url };

    return rawBaseQuery(adjustedArgs, api, extraOptions);
  };
};
