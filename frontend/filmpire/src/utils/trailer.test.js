import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import {
  extractYouTubeId,
  getStandbyTrailerId,
  setStandbyTrailerId,
  getRandomCuratedTrailerId,
  CURATED_TRAILERS,
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
      expect(extractYouTubeId('W8yHZXircp8')).toBe('W8yHZXircp8');
    });

    it('extracts ID from standard youtube.com/watch?v= URLs', () => {
      expect(extractYouTubeId('https://www.youtube.com/watch?v=k6U-i4gX7kQ')).toBe('k6U-i4gX7kQ');
      expect(extractYouTubeId('https://www.youtube.com/watch?v=NLUj9lFPU6s&t=10s')).toBe('NLUj9lFPU6s');
    });

    it('extracts ID from short youtu.be URLs', () => {
      expect(extractYouTubeId('https://youtu.be/GuDF9CEtFwE')).toBe('GuDF9CEtFwE');
    });

    it('extracts ID from embed URLs', () => {
      expect(extractYouTubeId('https://www.youtube.com/embed/nGrW-OR2uDk')).toBe('nGrW-OR2uDk');
    });

    it('falls back to DEFAULT_TRAILER_ID for invalid inputs', () => {
      expect(extractYouTubeId('')).toBe(DEFAULT_TRAILER_ID);
      expect(extractYouTubeId(null)).toBe(DEFAULT_TRAILER_ID);
      expect(extractYouTubeId('invalid_random_string')).toBe(DEFAULT_TRAILER_ID);
    });
  });

  describe('getRandomCuratedTrailerId', () => {
    it('returns one of the curated trailer IDs', () => {
      const validIds = CURATED_TRAILERS.map((t) => t.id);
      const randomId = getRandomCuratedTrailerId();
      expect(validIds).toContain(randomId);
    });
  });

  describe('getStandbyTrailerId & setStandbyTrailerId', () => {
    it('returns a valid curated trailer ID when unset', () => {
      const validIds = CURATED_TRAILERS.map((t) => t.id);
      expect(validIds).toContain(getStandbyTrailerId());
    });

    it('reads VITE_STANDBY_TRAILER_ID environment variable', () => {
      vi.stubEnv('VITE_STANDBY_TRAILER_ID', 'k6U-i4gX7kQ');
      expect(getStandbyTrailerId()).toBe('k6U-i4gX7kQ');
    });

    it('prefers localStorage over environment variable unless forceRandom is true', () => {
      vi.stubEnv('VITE_STANDBY_TRAILER_ID', 'W8yHZXircp8');
      setStandbyTrailerId('https://youtu.be/k6U-i4gX7kQ');
      expect(getStandbyTrailerId()).toBe('k6U-i4gX7kQ');

      // forceRandom picks a random curated trailer
      const validIds = CURATED_TRAILERS.map((t) => t.id);
      expect(validIds).toContain(getStandbyTrailerId(true));
    });
  });
});
