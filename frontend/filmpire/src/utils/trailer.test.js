import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  extractYouTubeId,
  getStandbyTrailerId,
  setStandbyTrailerId,
  DEFAULT_TRAILER_ID,
} from './trailer';

describe('trailer utilities', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
  });

  afterEach(() => {
    localStorage.clear();
    vi.unstubAllEnvs();
  });

  describe('extractYouTubeId', () => {
    it('returns raw 11-char ID unchanged', () => {
      expect(extractYouTubeId('h2QJMfXJZaY')).toBe('h2QJMfXJZaY');
    });

    it('extracts ID from standard youtube.com/watch?v= URLs', () => {
      expect(extractYouTubeId('https://www.youtube.com/watch?v=h2QJMfXJZaY')).toBe('h2QJMfXJZaY');
      expect(extractYouTubeId('https://www.youtube.com/watch?v=zSWdZVtXT7E&t=10s')).toBe('zSWdZVtXT7E');
    });

    it('extracts ID from short youtu.be URLs', () => {
      expect(extractYouTubeId('https://youtu.be/uYPbbksJxIg')).toBe('uYPbbksJxIg');
    });

    it('extracts ID from embed URLs', () => {
      expect(extractYouTubeId('https://www.youtube.com/embed/YoHD9XEInc0')).toBe('YoHD9XEInc0');
    });

    it('falls back to DEFAULT_TRAILER_ID for invalid inputs', () => {
      expect(extractYouTubeId('')).toBe(DEFAULT_TRAILER_ID);
      expect(extractYouTubeId(null)).toBe(DEFAULT_TRAILER_ID);
      expect(extractYouTubeId('invalid_random_string')).toBe(DEFAULT_TRAILER_ID);
    });
  });

  describe('getStandbyTrailerId & setStandbyTrailerId', () => {
    it('defaults to DEFAULT_TRAILER_ID when unset', () => {
      expect(getStandbyTrailerId()).toBe(DEFAULT_TRAILER_ID);
    });

    it('reads VITE_STANDBY_TRAILER_ID environment variable', () => {
      vi.stubEnv('VITE_STANDBY_TRAILER_ID', 'zSWdZVtXT7E');
      expect(getStandbyTrailerId()).toBe('zSWdZVtXT7E');
    });

    it('prefers localStorage over environment variable', () => {
      vi.stubEnv('VITE_STANDBY_TRAILER_ID', 'zSWdZVtXT7E');
      setStandbyTrailerId('https://youtu.be/EXeTwQWrcwY');
      expect(getStandbyTrailerId()).toBe('EXeTwQWrcwY');
    });
  });
});
