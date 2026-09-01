// Tests dictationLanguage.js's get/set localStorage wrapper (#213): the
// default-to-English behavior for first-time/corrupted storage, and that
// setDictationLanguage rejects anything outside the two supported codes
// rather than persisting garbage.
import {
  getDictationLanguage,
  setDictationLanguage,
  SUPPORTED_DICTATION_LANGUAGES,
} from './dictationLanguage';

const STORAGE_KEY = 'lmdb_dictation_language';

describe('dictationLanguage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('exposes English and German as the two supported codes', () => {
    // ai-service's SpeechToTextService only has Vosk models loaded for these
    // two (#212) - a third entry here would let the UI offer a language the
    // backend has no model for.
    expect(SUPPORTED_DICTATION_LANGUAGES).toEqual(['en', 'de']);
  });

  it('defaults to English when nothing has been stored yet', () => {
    // Given a first-time visitor (no localStorage entry), when the
    // dictation language is read, then it must be English - #213's
    // acceptance criteria requires English as the default, not an
    // unset/undefined value the caller would need to special-case.
    expect(getDictationLanguage()).toBe('en');
  });

  it('returns the persisted language once set', () => {
    // Given a prior visit already chose German, when the language is read
    // on a later mount, then it comes back as German - proves the read
    // path actually consults storage rather than always returning the
    // hardcoded default.
    localStorage.setItem(STORAGE_KEY, 'de');
    expect(getDictationLanguage()).toBe('de');
  });

  it('falls back to English when the stored value is not a supported code', () => {
    // Given corrupted/stale storage (e.g. an old build wrote a different
    // shape), when read back, then it must not surface as a truthy
    // "language" the caller sends straight to the backend.
    localStorage.setItem(STORAGE_KEY, 'fr');
    expect(getDictationLanguage()).toBe('en');
  });

  it('persists a supported language across the get/set round trip', () => {
    // Given a language is set, when it's read back (simulating the next
    // page load), then it's the value that was set, not the default -
    // proves setDictationLanguage actually reaches localStorage rather
    // than just updating some in-memory value get ignores.
    setDictationLanguage('de');
    expect(getDictationLanguage()).toBe('de');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('de');
  });

  it('ignores an unsupported value instead of writing it to storage', () => {
    // Given a valid language is already persisted, when setDictationLanguage
    // is called with a code outside SUPPORTED_DICTATION_LANGUAGES, then
    // storage is left untouched - a caller passing a bad value (e.g. a
    // typo, or a future third language the backend doesn't support yet)
    // must not corrupt the stored selection.
    setDictationLanguage('en');
    setDictationLanguage('fr');
    expect(localStorage.getItem(STORAGE_KEY)).toBe('en');
  });
});
