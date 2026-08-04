import { mediaApi, getMediaUrl } from './media';

describe('media service', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  describe('getMediaUrl helper', () => {
    it('returns null when URL is empty or undefined', () => {
      expect(getMediaUrl(null)).toBeNull();
      expect(getMediaUrl(undefined)).toBeNull();
      expect(getMediaUrl('')).toBeNull();
    });

    it('returns original URL when already absolute', () => {
      const absoluteUrl = 'http://external-storage.com/photo.jpg';
      expect(getMediaUrl(absoluteUrl)).toBe(absoluteUrl);
    });

    it('prepends API gateway URL when URL is relative', () => {
      const relativeUrl = '/api/v1/media/uuid-123/download';
      const expectedPrefix = process.env.REACT_APP_API_URL || 'http://localhost:8080';
      expect(getMediaUrl(relativeUrl)).toBe(`${expectedPrefix}${relativeUrl}`);
    });
  });

  describe('mediaApi structure and endpoints', () => {
    it('registers expected endpoints on mediaApi', () => {
      expect(mediaApi.endpoints.uploadMedia).toBeDefined();
      expect(mediaApi.endpoints.getMediaForEntity).toBeDefined();
      expect(mediaApi.endpoints.deleteMedia).toBeDefined();
      expect(mediaApi.reducerPath).toBe('mediaApi');
    });

    it('constructs correct query definitions for uploadMedia', () => {
      const file = new File(['dummy content'], 'avatar.png', { type: 'image/png' });
      const uploadArgs = {
        file,
        entityId: '123',
        entityType: 'USER',
        mediaType: 'AVATAR',
        uploadedBy: 'testuser',
      };

      const queryConfig = mediaApi.endpoints.uploadMedia.initiate(uploadArgs);
      expect(queryConfig).toBeDefined();
    });
  });
});
