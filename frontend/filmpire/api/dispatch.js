/**
 * Vercel serverless function that proxies GitHub Actions `workflow_dispatch`
 * calls on behalf of the Admin Dashboard's Live Infrastructure Orchestrator.
 *
 * This exists so the GitHub token never has to live in the browser: Vite
 * env vars (VITE_*) are inlined into the public JS bundle at build time, so
 * a token read from `import.meta.env` would be readable by anyone who opens
 * devtools on the deployed site. Reading it here instead, from a plain
 * (non-VITE_-prefixed) server env var, keeps it out of the bundle entirely.
 *
 * Access is gated by a shared passphrase (ADMIN_DISPATCH_SECRET) because the
 * `/admin` route itself has no login — without this check, anyone who found
 * the URL could trigger real Azure/AWS spend with no credentials at all.
 */

const REPO_OWNER = 'pehlivanu';
const REPO_NAME = 'filmpire-microservices';
const ALLOWED_WORKFLOWS = new Set(['deploy.yml', 'destroy.yml']);

/**
 * Handles POST /api/dispatch requests, forwarding a validated
 * `workflow_dispatch` event to the GitHub Actions API.
 *
 * @param {import('http').IncomingMessage & { body: any }} req - Vercel request; `req.body` is the parsed JSON payload `{ workflow, inputs }`.
 * @param {import('http').ServerResponse & { status: Function, json: Function }} res - Vercel response helper.
 * @returns {Promise<void>} Resolves once a response has been sent.
 */
module.exports = async (req, res) => {
  if (req.method !== 'POST') {
    res.status(405).json({ message: 'Method not allowed' });
    return;
  }

  // 1. Reject anyone who doesn't know the shared admin passphrase.
  const adminSecret = process.env.ADMIN_DISPATCH_SECRET;
  if (!adminSecret) {
    res.status(500).json({ message: 'Server misconfigured: ADMIN_DISPATCH_SECRET is not set.' });
    return;
  }
  if (req.headers['x-admin-key'] !== adminSecret) {
    res.status(401).json({ message: 'Invalid or missing admin passphrase.' });
    return;
  }

  // 2. Only allow dispatching the two known infra workflows, never an
  // arbitrary workflow file supplied by the client.
  const { workflow, inputs } = req.body || {};
  if (!ALLOWED_WORKFLOWS.has(workflow)) {
    res.status(400).json({ message: `Unknown workflow: ${workflow}` });
    return;
  }

  const githubToken = process.env.GITHUB_TOKEN;
  if (!githubToken) {
    res.status(500).json({ message: 'Server misconfigured: GITHUB_TOKEN is not set.' });
    return;
  }

  try {
    const ghResponse = await fetch(
      `https://api.github.com/repos/${REPO_OWNER}/${REPO_NAME}/actions/workflows/${workflow}/dispatches`,
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${githubToken}`,
          Accept: 'application/vnd.github.v3+json',
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ ref: 'develop', inputs: inputs || {} }),
      },
    );

    if (!ghResponse.ok) {
      const err = await ghResponse.json().catch(() => ({}));
      res.status(ghResponse.status).json({ message: err.message || `GitHub API error: ${ghResponse.status}` });
      return;
    }

    res.status(200).json({ ok: true });
  } catch (err) {
    res.status(502).json({ message: err.message || 'Failed to reach GitHub API' });
  }
};
