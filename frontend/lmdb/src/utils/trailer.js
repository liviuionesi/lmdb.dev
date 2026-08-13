export const DEFAULT_TRAILER_ID = 'W8yHZXircp8'; // Machete

export const CURATED_TRAILERS = [
  { id: 'W8yHZXircp8', title: 'Machete', tag: 'Action • 2010' },
  { id: 'k6U-i4gX7kQ', title: 'The Terminator', tag: 'Sci-Fi • 1984' },
  { id: 'NLUj9lFPU6s', title: 'Sin City', tag: 'Crime • 2005' },
  { id: 'h2QJMfXJZaY', title: 'Dune: Part Two', tag: 'Sci-Fi • 2024' },
  { id: 'GuDF9CEtFwE', title: 'Blade Runner 2049', tag: 'Sci-Fi • 2017' },
  { id: 'nGrW-OR2uDk', title: 'Gladiator II', tag: 'Action • 2024' },
];

/**
 * Extracts a valid 11-character YouTube video ID from a URL, embed link, or raw ID.
 *
 * @param {string} input - YouTube URL or ID
 * @returns {string} YouTube video ID or DEFAULT_TRAILER_ID if invalid
 */
export function extractYouTubeId(input) {
  if (!input || typeof input !== 'string') {
    return DEFAULT_TRAILER_ID;
  }
  const trimmed = input.trim();

  // If already an 11-character alphanumeric ID
  if (/^[\w-]{11}$/.test(trimmed)) {
    return trimmed;
  }

  try {
    const url = new URL(trimmed.startsWith('http') ? trimmed : `https://${trimmed}`);
    if (url.hostname.includes('youtu.be')) {
      const id = url.pathname.slice(1);
      if (/^[\w-]{11}$/.test(id)) {
        return id;
      }
    }
    if (url.hostname.includes('youtube.com')) {
      const v = url.searchParams.get('v');
      if (v && /^[\w-]{11}$/.test(v)) {
        return v;
      }
      const parts = url.pathname.split('/').filter(Boolean);
      const lastPart = parts.at(-1);
      if (lastPart && /^[\w-]{11}$/.test(lastPart)) {
        return lastPart;
      }
    }
  } catch {
    // If not a parseable URL, fallback
  }

  return DEFAULT_TRAILER_ID;
}

/**
 * Picks a random trailer ID from the curated playlist.
 *
 * @returns {string} YouTube video ID
 */
export function getRandomCuratedTrailerId() {
  const cryptoObj = typeof window !== 'undefined' && window.crypto ? window.crypto : null;
  if (cryptoObj && typeof cryptoObj.getRandomValues === 'function') {
    const randomBuffer = new Uint32Array(1);
    cryptoObj.getRandomValues(randomBuffer);
    const randomIndex = randomBuffer[0] % CURATED_TRAILERS.length;
    return CURATED_TRAILERS[randomIndex].id;
  }
  const timestampIndex = Date.now() % CURATED_TRAILERS.length;
  return CURATED_TRAILERS[timestampIndex].id;
}

/**
 * Resolves the configured standby trailer ID.
 * If forceRandom is true, selects a new random trailer from the playlist.
 * Otherwise priority is: localStorage -> import.meta.env.VITE_STANDBY_TRAILER_ID -> random curated trailer.
 *
 * @param {boolean} [forceRandom=false] - If true, ignores stored preference and selects a new random trailer
 * @returns {string} YouTube video ID
 */
export function getStandbyTrailerId(forceRandom = false) {
  if (forceRandom) {
    return getRandomCuratedTrailerId();
  }
  if (typeof window !== 'undefined') {
    const saved = localStorage.getItem('lmdb_standby_trailer') || localStorage.getItem('lmdb_standby_trailer');
    if (saved) {
      return extractYouTubeId(saved);
    }
  }
  const envId = import.meta.env?.VITE_STANDBY_TRAILER_ID;
  if (envId) {
    return extractYouTubeId(envId);
  }
  return getRandomCuratedTrailerId();
}

/**
 * Persists the selected standby trailer ID to localStorage.
 *
 * @param {string} trailerId - YouTube video ID or URL
 */
export function setStandbyTrailerId(trailerId) {
  if (typeof window !== 'undefined') {
    localStorage.setItem('lmdb_standby_trailer', extractYouTubeId(trailerId));
  }
}
