// Tests the auth redux slice: initial state, both reducers, and userSelector.
import reducer, { setUser, clearUser, userSelector } from './auth';

describe('auth slice', () => {
  it('returns the initial state', () => {
    expect(reducer(undefined, { type: '@@INIT' })).toEqual({
      user: {},
      isAuthenticated: false,
    });
  });

  it('setUser stores the user and marks the session authenticated', () => {
    const user = { id: 1, username: 'liviu' };
    const next = reducer(undefined, setUser(user));
    expect(next.user).toEqual(user);
    expect(next.isAuthenticated).toBe(true);
  });

  it('clearUser resets back to an empty, unauthenticated state', () => {
    const authenticated = { user: { id: 1 }, isAuthenticated: true };
    const next = reducer(authenticated, clearUser());
    expect(next.user).toEqual({});
    expect(next.isAuthenticated).toBe(false);
  });

  it('userSelector reads the user slice off the root state', () => {
    const rootState = { user: { user: { id: 1 }, isAuthenticated: true } };
    expect(userSelector(rootState)).toEqual({ user: { id: 1 }, isAuthenticated: true });
  });
});
