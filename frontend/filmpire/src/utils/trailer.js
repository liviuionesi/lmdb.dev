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
  if (/^[a-zA-Z0-9_-]{11}$/.test(trimmed)) {
    return trimmed;
  }

  // Handle standard watch?v=, youtu.be, or embed URLs
  const urlMatch = trimmed.match(/(?:youtube\.com\/(?:[^\n\s]+\/\S+\/|(?:v|e(?:mbed)?)\/|.*[?&]v=)|youtu\.be\/)([a-zA-Z0-9_-]{11})/i);
  if (urlMatch && urlMatch[1]) {
    return urlMatch[1];
  }

  return DEFAULT_TRAILER_ID;
}

/**
 * Picks a random trailer ID from the curated playlist.
 *
 * @returns {string} YouTube video ID
 */
export function getRandomCuratedTrailerId() {
  const randomIndex = Math.floor(Math.random() * CURATED_TRAILERS.length);
  return CURATED_TRAILERS[randomIndex].id;
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
    const saved = localStorage.getItem('filmpire_standby_trailer');
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
    localStorage.setItem('filmpire_standby_trailer', extractYouTubeId(trailerId));
  }
}
