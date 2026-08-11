import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useBackendWakeup } from './useBackendWakeup';
import * as apiUrlModule from '../../utils/apiUrl';

vi.mock('../../utils/apiUrl', () => ({
  resolveApiUrl: vi.fn().mockResolvedValue('https://filmpire-api.duckdns.org'),
  checkBackendHealth: vi.fn(),
  triggerBackendWakeup: vi.fn().mockResolvedValue({ status: 'WAKING_UP' }),
  invalidateResolutionCache: vi.fn(),
  subscribeBackendStatus: vi.fn(() => () => {}),
  getBackendTarget: vi.fn(() => 'azure'),
  setBackendTarget: vi.fn(),
}));

describe('useBackendWakeup hook', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.clearAllMocks();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('sets status to ONLINE if backend is already healthy on mount', async () => {
    apiUrlModule.checkBackendHealth.mockResolvedValue(true);

    const { result } = renderHook(() => useBackendWakeup({ autoWakeup: false }));

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.status).toBe('ONLINE');
  });

  it('triggers auto-wakeup and counts down seconds when backend is down on mount', async () => {
    apiUrlModule.checkBackendHealth.mockResolvedValue(false);

    const { result } = renderHook(() => useBackendWakeup({ autoWakeup: true }));

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.status).toBe('WAKING_UP');
    expect(result.current.secondsRemaining).toBe(90);

    // Fast-forward 5 seconds
    act(() => {
      vi.advanceTimersByTime(5000);
    });

    expect(result.current.secondsRemaining).toBe(85);
  });

  it('transitions to READY when health check succeeds during polling', async () => {
    // Initially down, then becomes healthy on second poll
    apiUrlModule.checkBackendHealth
      .mockResolvedValueOnce(false)
      .mockResolvedValueOnce(true);

    const onReadyMock = vi.fn();
    const { result } = renderHook(() => useBackendWakeup({ autoWakeup: true, onReady: onReadyMock }));

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.status).toBe('WAKING_UP');

    // Advance to trigger poll (4000ms)
    await act(async () => {
      vi.advanceTimersByTime(4000);
    });

    expect(result.current.status).toBe('READY');
    expect(onReadyMock).toHaveBeenCalled();
  });
});
