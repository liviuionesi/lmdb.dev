// Tests storeAuthTokens/clearAuthTokens against real jsdom localStorage.
import { storeAuthTokens, clearAuthTokens } from '.';

describe('auth token storage helpers', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('storeAuthTokens persists both the access and refresh token', () => {
    storeAuthTokens({ accessToken: 'access-123', refreshToken: 'refresh-456' });
    expect(localStorage.getItem('access_token')).toBe('access-123');
    expect(localStorage.getItem('refresh_token')).toBe('refresh-456');
  });

  it('clearAuthTokens removes both tokens', () => {
    localStorage.setItem('access_token', 'access-123');
    localStorage.setItem('refresh_token', 'refresh-456');

    clearAuthTokens();

    expect(localStorage.getItem('access_token')).toBeNull();
    expect(localStorage.getItem('refresh_token')).toBeNull();
  });
});
