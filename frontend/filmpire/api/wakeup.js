/**
 * Vercel Serverless Function: /api/wakeup
 *
 * Checks if the Filmpire backend is healthy and reachable. If offline / sleeping,
 * initiates an automated wake-up signal for Azure or AWS compute nodes.
 */

const CLOUD_API_URL = process.env.CLOUD_API_URL || 'https://filmpire-api.duckdns.org';
const GITHUB_REPO = process.env.GITHUB_REPO || 'pehlivanu/filmpire-microservices';
const GITHUB_TOKEN = process.env.GITHUB_TOKEN;

// In-memory cooldown per serverless instance to prevent spam
let lastWakeupTimestamp = 0;
const WAKEUP_COOLDOWN_MS = 60000; // 1 minute

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

  // GET request returns the current health status
  if (req.method === 'GET') {
    return res.status(200).json({
      status: isHealthy ? 'RUNNING' : 'STANDBY',
      backendUrl: CLOUD_API_URL,
      healthy: isHealthy,
    });
  }

  // POST request triggers wake-up if not already running
  if (req.method === 'POST') {
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
        estimatedSeconds: 90,
      });
    }

    const targetCloud = (req.body && req.body.cloud) || process.env.BACKEND_TARGET || 'azure';
    lastWakeupTimestamp = now;

    // If GITHUB_TOKEN is configured and targeting a cloud provider, dispatch the workflow
    if (GITHUB_TOKEN && (targetCloud === 'azure' || targetCloud === 'aws')) {
      try {
        await fetch(`https://api.github.com/repos/${GITHUB_REPO}/actions/workflows/deploy.yml/dispatches`, {
          method: 'POST',
          headers: {
            Authorization: `Bearer ${GITHUB_TOKEN}`,
            Accept: 'application/vnd.github+json',
            'User-Agent': 'Filmpire-Wakeup-Serverless',
          },
          body: JSON.stringify({
            ref: 'develop',
            inputs: { cloud: targetCloud },
          }),
        });
      } catch (err) {
        console.warn('GitHub Actions dispatch notice:', err.message);
      }
    }

    const clusterLabel = targetCloud === 'minikube' || targetCloud === 'tunnel' ? 'LOCAL MINIKUBE TUNNEL' : `${targetCloud.toUpperCase()} CLUSTER`;

    return res.status(200).json({
      status: 'WAKING_UP',
      targetCloud,
      estimatedSeconds: 90,
      message: `Wake-up signal dispatched for ${clusterLabel}. Initializing compute...`,
    });
  }

  return res.status(405).json({ error: 'Method not allowed' });
}
