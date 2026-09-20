// Tests the /api/wakeup Vercel serverless handler: GET's plain health
// report, POST's happy-path wakeup dispatch, and — the gap this file
// closes — the in-memory cooldown gate that stops repeated POSTs from
// spamming GitHub Actions (see cluster-stop.yml / ADR-019). `lastWakeupTimestamp`
// is module-level state (not exported, on purpose — see wakeup.js), so each
// cooldown test resets modules and re-imports the handler, the same pattern
// apiUrl.test.js uses for its own module-level resolution cache. global.fetch
// stands in for the actuator health probe, matching apiUrl.test.js and
// useServiceStatus.test.jsx's own fetch-mocking convention.
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';

// Minimal mock Vercel req/res: this is the first handler test of its kind in
// api/, so there's no existing sibling pattern to copy — res chains
// .status().json()/.end() the same way the real Vercel runtime does.
const buildReq = (method, body) => ({ method, body });

const buildRes = () => {
  const res = {};
  res.status = vi.fn().mockReturnValue(res);
  res.json = vi.fn().mockReturnValue(res);
  res.end = vi.fn().mockReturnValue(res);
  res.setHeader = vi.fn().mockReturnValue(res);
  return res;
};

describe('GET /api/wakeup', () => {
  beforeEach(() => {
    vi.resetModules();
  });

  afterEach(() => {
    delete global.fetch;
  });

  it('returns 200 with a status field reflecting current backend health', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true });
    const { default: handler } = await import('./wakeup.js');

    const req = buildReq('GET');
    const res = buildRes();
    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({ status: 'RUNNING' }));
  });

  it('reports STANDBY when the health probe fails', async () => {
    global.fetch = vi.fn().mockRejectedValue(new Error('unreachable'));
    const { default: handler } = await import('./wakeup.js');

    const req = buildReq('GET');
    const res = buildRes();
    await handler(req, res);

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({ status: 'STANDBY' }));
  });
});

describe('POST /api/wakeup cooldown gate', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.useFakeTimers();
    // Backend down for every probe in this block. targetCloud 'minikube' hits
    // wakeup.js's local/tunnel branch, which returns WAKING_UP without an
    // outbound GitHub Actions dispatch call — keeping these cooldown tests
    // about the cooldown gate itself, not GitHub credentials/dispatch.
    global.fetch = vi.fn().mockRejectedValue(new Error('unreachable'));
  });

  afterEach(() => {
    vi.useRealTimers();
    delete global.fetch;
  });

  it('triggers wakeup and returns success on the first POST', async () => {
    const { default: handler } = await import('./wakeup.js');

    const res = buildRes();
    await handler(buildReq('POST', { cloud: 'minikube' }), res);

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({ status: 'WAKING_UP' }));
    expect(res.json).not.toHaveBeenCalledWith(expect.objectContaining({ cooldownRemainingSeconds: expect.anything() }));
  });

  it('returns WAKING_UP with cooldownRemainingSeconds instead of re-triggering within the cooldown window', async () => {
    const { default: handler } = await import('./wakeup.js');

    await handler(buildReq('POST', { cloud: 'minikube' }), buildRes());

    // Still well inside the 60s WAKEUP_COOLDOWN_MS window.
    vi.advanceTimersByTime(10000);

    const res = buildRes();
    await handler(buildReq('POST', { cloud: 'minikube' }), res);

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({
      status: 'WAKING_UP',
      cooldownRemainingSeconds: 50,
    }));
  });

  it('re-triggers normally once the cooldown window has fully elapsed', async () => {
    const { default: handler } = await import('./wakeup.js');

    await handler(buildReq('POST', { cloud: 'minikube' }), buildRes());

    // Past the 60s WAKEUP_COOLDOWN_MS window.
    vi.advanceTimersByTime(60001);

    const res = buildRes();
    await handler(buildReq('POST', { cloud: 'minikube' }), res);

    expect(res.status).toHaveBeenCalledWith(200);
    expect(res.json).toHaveBeenCalledWith(expect.objectContaining({ status: 'WAKING_UP' }));
    expect(res.json).not.toHaveBeenCalledWith(expect.objectContaining({ cooldownRemainingSeconds: expect.anything() }));
  });
});
