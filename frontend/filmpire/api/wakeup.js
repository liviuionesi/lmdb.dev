/**
 * Vercel Serverless Function: /api/wakeup
 *
 * Checks if the LMDB backend is healthy and reachable. If offline / sleeping,
 * initiates an automated wake-up signal for Azure or AWS compute nodes.
 */

const CLOUD_API_URL = process.env.CLOUD_API_URL || 'https://api.lmdb.dev';
const GITHUB_REPO = process.env.GITHUB_REPO || 'pehlivanu/lmdb.dev';
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
// Server-side only (this file runs as a Vercel serverless function, never
// shipped to the browser bundle) mirror of the GitHub repo secret
// DEPLOY_PASSPHRASE. cluster-stop.yml requires it on every dispatch,
// including this automated one — see ADR-019.
const DEPLOY_PASSPHRASE = process.env.DEPLOY_PASSPHRASE;
const WAKEUP_ESTIMATED_SECONDS = 90;

// In-memory cooldown per serverless instance to prevent spam
let lastWakeupTimestamp = 0;
const WAKEUP_COOLDOWN_MS = 60000; // 1 minute

/**
 * Probes the gateway's actuator health endpoint with a short timeout.
 *
 * @param {string} url - Backend base URL to probe.
 * @returns {Promise<boolean>} True if the backend responded with an ok status.
 */
async function isBackendHealthy(url) {
  try {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 3000);
    const res = await fetch(`${url}/actuator/health`, {
      method: 'GET',
      signal: controller.signal,
    });
    clearTimeout(timer);
    return res.ok;
  } catch {
    return false;
  }
}

/**
 * Dispatches the non-destructive cluster-stop.yml workflow's "start" action
 * for the given cloud target via the GitHub Actions REST API.
 *
 * @param {string} targetCloud - 'azure' or 'aws'.
 * @returns {Promise<{dispatched: boolean, reason?: string}>} Whether the
 *   dispatch call was accepted by GitHub, and why not if it wasn't.
 */
async function dispatchWakeupWorkflow(targetCloud) {
  if (!GITHUB_TOKEN || !DEPLOY_PASSPHRASE) {
    return { dispatched: false, reason: 'GITHUB_TOKEN or DEPLOY_PASSPHRASE not configured on the server.' };
  }

  try {
    const ghRes = await fetch(`https://api.github.com/repos/${GITHUB_REPO}/actions/workflows/cluster-stop.yml/dispatches`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${GITHUB_TOKEN}`,
        Accept: 'application/vnd.github+json',
        'User-Agent': 'LMDB-Wakeup-Serverless',
      },
      body: JSON.stringify({
        ref: 'develop',
        inputs: { cloud: targetCloud, action: 'start', passphrase: DEPLOY_PASSPHRASE },
      }),
    });
    if (!ghRes.ok) {
      return { dispatched: false, reason: `GitHub API responded ${ghRes.status}.` };
    }
    return { dispatched: true };
  } catch (err) {
    return { dispatched: false, reason: err.message };
  }
}

/**
 * Handles GET /api/wakeup — reports current backend health without
 * triggering anything.
 *
 * @param {Object} res - Vercel response object.
 * @param {boolean} isHealthy - Result of the health probe.
 */
function handleHealthCheck(res, isHealthy) {
  return res.status(200).json({
    status: isHealthy ? 'RUNNING' : 'STANDBY',
    backendUrl: CLOUD_API_URL,
    healthy: isHealthy,
  });
}

/**
 * Handles POST /api/wakeup — dispatches a wake-up for the requested cloud
 * target, subject to a per-instance cooldown so repeated page visits don't
 * spam GitHub Actions.
 *
 * @param {Object} req - Vercel request object.
 * @param {Object} res - Vercel response object.
 * @param {boolean} isHealthy - Result of the health probe.
 */
async function handleWakeupRequest(req, res, isHealthy) {
  if (isHealthy) {
    return res.status(200).json({
      status: 'ALREADY_RUNNING',
      message: 'Backend is already online and healthy.',
      backendUrl: CLOUD_API_URL,
    });
  }

  const now = Date.now();
  if (now - lastWakeupTimestamp < WAKEUP_COOLDOWN_MS) {
    return res.status(200).json({
      status: 'WAKING_UP',
      message: 'Wake-up already in progress. Please wait for nodes to boot.',
      cooldownRemainingSeconds: Math.ceil((WAKEUP_COOLDOWN_MS - (now - lastWakeupTimestamp)) / 1000),
      estimatedSeconds: WAKEUP_ESTIMATED_SECONDS,
    });
  }

  const targetCloud = req.body?.cloud || process.env.BACKEND_TARGET || 'azure';
  lastWakeupTimestamp = now;

  if (targetCloud !== 'azure' && targetCloud !== 'aws') {
    // minikube/tunnel targets have no remote compute to dispatch — the
    // frontend's own local/tunnel resolution tiers handle those.
    return res.status(200).json({
      status: 'WAKING_UP',
      targetCloud,
      estimatedSeconds: WAKEUP_ESTIMATED_SECONDS,
      message: 'No remote compute to wake for this target — checking local/tunnel tiers.',
    });
  }

  const { dispatched, reason } = await dispatchWakeupWorkflow(targetCloud);
  if (!dispatched) {
    // Surface the real reason instead of silently pretending a wake-up is
    // underway — that swallowing is exactly what made this undebuggable
    // before (see ADR-019).
    return res.status(200).json({
      status: 'ERROR',
      targetCloud,
      message: `Failed to dispatch wake-up: ${reason}`,
    });
  }

  return res.status(200).json({
    status: 'WAKING_UP',
    targetCloud,
    estimatedSeconds: WAKEUP_ESTIMATED_SECONDS,
    message: `Wake-up signal dispatched for ${targetCloud.toUpperCase()} CLUSTER. Initializing compute...`,
  });
}

export default async function handler(req, res) {
  // Enable CORS
  res.setHeader('Access-Control-Allow-Credentials', true);
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,OPTIONS,POST');
  res.setHeader(
    'Access-Control-Allow-Headers',
    'X-CSRF-Token, X-Requested-With, Accept, Accept-Version, Content-Length, Content-MD5, Content-Type, Date, X-Api-Version'
  );

  if (req.method === 'OPTIONS') {
    return res.status(200).end();
  }

  const isHealthy = await isBackendHealthy(CLOUD_API_URL);

  if (req.method === 'GET') {
    return handleHealthCheck(res, isHealthy);
  }

  if (req.method === 'POST') {
    return handleWakeupRequest(req, res, isHealthy);
  }

  return res.status(405).json({ error: 'Method not allowed' });
}
