# ADR-015: Deploy/Destroy Triggered Locally Only, Not From the Web

**Status:** Accepted
**Date:** 2026-08-10
**Deciders:** Project owner
**Related:** ADR-004 ($0 budget), §11.4

## Context

The Admin Dashboard (`/admin`) originally had a "Launch/Destroy" button
that dispatched `deploy.yml`/`destroy.yml` (GitHub Actions) directly from
the browser. Getting there took two iterations:

1. First cut: a GitHub Personal Access Token read from
   `import.meta.env.VITE_GITHUB_TOKEN` or `localStorage`. Vite inlines
   `VITE_`-prefixed env vars into the public JS bundle at build time, so
   this shipped a repo-write-scoped credential to anyone who opened
   devtools on the deployed site.
2. Second cut: moved the token server-side into a Vercel serverless
   function (`api/dispatch.js`), gated by a shared admin passphrase
   (`ADMIN_DISPATCH_SECRET`) cached in `localStorage` after first entry.
   This fixed the credential-exposure problem.

Iteration 2 was still wrong in a way that mattered more: **`/admin` itself
has no login of its own.** It's a plain route, reachable by anyone who
finds the URL. A passphrase-gated button behind an unauthenticated page is
better than a bare credential, but the page is still a public trigger for
real cloud spend — the passphrase only raises the bar for *finding* it,
it doesn't change what the button can do once found.

## Decision

Remove the web-triggered deploy path entirely rather than harden it
further. `DeployControl.jsx` and `api/dispatch.js` are deleted. Deploy and
destroy now run **only** from a shell on the operator's own machine, via
`./gradlew deployAzure` / `deployAws` / `deployLocal` (+ `destroy*`
variants), each wrapping a script in `infrastructure/scripts/`. `/admin` is
now read-only telemetry (`StatusCard`) — nothing on it can spend money.

The GitHub Actions `deploy.yml`/`destroy.yml` workflows (`workflow_dispatch`,
human-triggered from the GitHub UI, requiring repo write access) are left
in place as a secondary, still-authenticated path — that trigger already
required being a repo collaborator, which the removed web button did not.

## Options Considered

**Add real authentication to `/admin` (e.g. wire it to user-service's own
JWT login), keep the web trigger.** Rejected as disproportionate: it would
mean building and maintaining a real auth-gated admin panel for a feature
(clicking a button to run a script the operator can already run directly)
that doesn't need a web UI to exist at all. More surface area for the same
outcome.

**Keep the passphrase-gated version, accept the residual risk.** Rejected
once actually looked at hard: the passphrase is a deterrent, not a
security boundary — it's a single shared static string, cached
client-side, with no rate limiting or rotation. "Deters casual discovery"
is a materially weaker guarantee than "cannot be triggered from the web at
all," for a feature (cloud provisioning) where the failure mode is real
money.

**Local-only trigger (chosen).** Removes the vulnerable surface outright
instead of shrinking it. Cost: deploying now requires a shell session on a
machine with cloud credentials configured — acceptable, since that was
already true for `terraform apply`/`destroy` themselves, which the button
was only ever a thin wrapper around.

## Consequences

- No URL, reachable by anyone, can trigger cloud spend anymore — the
  strongest version of this guarantee, not a mitigated version of the
  previous one.
- Losing the web UI's convenience is real but small: the Gradle tasks are
  one command each and already print the same status/next-steps output the
  button's UI used to show (§11.4).
- The now-unused Vercel env vars from iteration 2 (`GITHUB_TOKEN`,
  `ADMIN_DISPATCH_SECRET`) are dead weight, harmless to leave, safe to
  delete next time the Vercel dashboard is open.
- This is the kind of finding that's easy to miss without actually testing
  the deployed page as an anonymous visitor would see it — the first two
  iterations both looked reasonable in isolation and were only caught by
  checking `/admin`'s actual reachability, not by reasoning about the
  token-handling code alone.
