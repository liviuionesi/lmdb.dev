/**
 * Persisted dictation-language selection for voice control (#213, part of
 * #200's bilingual voice control Story). Mirrors the get/set-plus-localStorage
 * shape of {@code getBackendTarget}/{@code setBackendTarget} in {@code apiUrl.js}
 * rather than introducing a new pattern.
 *
 * The two codes here (`en`/`de`) match ai-service's `/speech-to-text`
 * `language` request param (#212) exactly, case-sensitive lowercase — no
 * translation layer needed between frontend and backend.
 */

const DICTATION_LANGUAGE_STORAGE_KEY = 'lmdb_dictation_language';

/** Languages ai-service's SpeechToTextService has a loaded Vosk model for (#212). */
export const SUPPORTED_DICTATION_LANGUAGES = ['en', 'de'];

/** Language used when nothing is stored yet or the stored value is invalid. */
const DEFAULT_DICTATION_LANGUAGE = 'en';

/**
 * Reads the persisted dictation language.
 *
 * @returns {string} `'en'` or `'de'`; defaults to `'en'` for first-time users,
 *   an unrecognized/corrupted stored value, or when `localStorage` isn't
 *   available (server-side render / test environment without a `window`).
 */
export function getDictationLanguage() {
  if (typeof window === 'undefined') {
    return DEFAULT_DICTATION_LANGUAGE;
  }
  const saved = localStorage.getItem(DICTATION_LANGUAGE_STORAGE_KEY);
  return SUPPORTED_DICTATION_LANGUAGES.includes(saved) ? saved : DEFAULT_DICTATION_LANGUAGE;
}

/**
 * Persists the dictation language so it survives a page reload.
 *
 * @param {string} language - `'en'` or `'de'`; anything else is ignored so a
 *   caller can't corrupt storage with a stray value.
 */
export function setDictationLanguage(language) {
  if (typeof window === 'undefined' || !SUPPORTED_DICTATION_LANGUAGES.includes(language)) {
    return;
  }
  localStorage.setItem(DICTATION_LANGUAGE_STORAGE_KEY, language);
}
