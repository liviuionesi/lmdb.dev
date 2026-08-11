export const DEFAULT_TRAILER_ID = 'h2QJMfXJZaY';

export const CURATED_TRAILERS = [
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
 * Picks a random trailer ID from the curated playlist of user favorite trailers.
 *
 * @returns {string} YouTube video ID
 */
export function getRandomCuratedTrailerId() {
  const randomIndex = Math.floor(Math.random() * CURATED_TRAILERS.length);
  return CURATED_TRAILERS[randomIndex].id;
}

/**
 * Resolves the configured standby trailer ID.
 * If VITE_STANDBY_TRAILER_ID is explicitly provided (or stored in localStorage), uses it.
 * Otherwise, randomly selects one of the favorite trailers (Dune 2, Blade Runner 2049, Gladiator II).
 *
 * @param {boolean} [forceRandom=false] - If true, ignores stored preference and selects a new random trailer
 * @returns {string} YouTube video ID
 */
export function getStandbyTrailerId(forceRandom = false) {
  if (!forceRandom && typeof window !== 'undefined') {
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
