# ADR-016: Frontend Resolves Its Backend Dynamically, Fronted by a Published Tunnel

**Status:** Accepted
**Date:** 2026-08-10
**Deciders:** Project owner
**Related:** ADR-004 ($0 budget), ADR-015 (local-only deploy trigger), §11.6

## Context

The frontend deploys once to Vercel. What it should talk to changes
constantly and unpredictably: local dev, a live Azure demo, a live AWS
demo, or — the normal steady state under ADR-004's ephemeral-cluster
model — nothing at all. Hardcoding a backend URL at build time means every
change requires a Vercel redeploy just to point at a different target, and
gives every visitor the same (frequently dead) URL regardless of what's
actually reachable at that moment.

A second, structurally separate problem sits underneath it: a cloud
gateway is exposed as a `NodePort` on the node's raw IP over plain HTTP
(ADR-004 rules out a paid load balancer/certificate). The Vercel frontend
is HTTPS. Browsers block "mixed content" — an HTTPS page fetching a plain
`http://` resource — outright, with no user override. Any solution has to
solve both: *which* backend to use, and *how* to reach a plain-HTTP cloud
backend from an HTTPS page at all.

## Decision

**Resolution waterfall, evaluated per request** (see §11.6 for the exact
order): a manual `localStorage` override, then a build-time env var, then
`localhost` if running locally, then the cloud default *only if it passes
a live health check*, then a published tunnel URL *only if it also passes
a health check*, then the cloud default anyway as a last, visible resort.
Implemented in `frontend/filmpire/src/utils/apiUrl.js`, cached 30s per
request so it isn't a network round-trip every time.

**HTTPS fronting via an ephemeral Cloudflare quick tunnel.**
`cloudflared tunnel --url http://<node-ip>:30080` gives a real HTTPS
endpoint with no certificate to provision and no Cloudflare account
required. Its hostname is randomly regenerated on every restart, so it's
published to a small tracked file (`infrastructure/tunnel-url.txt`),
updated and `git push`ed by `start-tunnel.sh` (local) or the equivalent
manual step (cloud) every time the tunnel restarts. The frontend fetches
this file from `raw.githubusercontent.com` — a plain public GET, free, no
auth — making the repository itself the (very lightweight) service
registry for a demo environment that can't justify a real one.

## Options Considered

**Fixed `VITE_API_URL`, redeploy on change.** Rejected: reintroduces the
exact problem this exists to solve, and every visitor would see whatever
was configured at the last build regardless of whether it's currently
alive.

**A real DNS record kept live** (e.g. `filmpire-api.duckdns.org` pointed
at whichever cloud node is currently up). Tried as the *first* fallback
tier and kept for that — but it can't be the *only* mechanism: DuckDNS is
a bare A-record, no TLS termination, so it inherits the exact mixed-content
problem above whenever it points at a plain-HTTP node. It also requires
remembering to update it on every deploy, which a human will eventually
forget; the tunnel-URL pointer is written by the deploy script itself.

**A managed load balancer with a real certificate.** Solves HTTPS cleanly.
Rejected outright by ADR-004: load balancers bill hourly regardless of
traffic, and a cert either costs money or adds a Let's Encrypt/cert-manager
operational surface disproportionate to a demo environment.

**Dynamic waterfall + tunnel-fronting (chosen).** More moving parts than a
fixed URL, but every one of them is free, and the result is the property
that actually matters: the same Vercel deployment, touched once, correctly
finds and uses whichever backend — local machine, Azure, or AWS — happens
to be running at any given moment, with zero per-visit configuration and
zero redeploys.

## Consequences

- CORS became a second, genuinely separate thing to get right: the
  gateway's allow-list has to include the frontend's real origin (plus
  origin *patterns* for the ephemeral tunnel/cloud domains), or every
  request in the waterfall above — including the health checks — gets a
  403 that a plain `curl` test without an `Origin` header won't reveal.
  This was found live as the actual root cause of an outage that initially
  looked like a routing problem: the backend was healthy and reachable,
  every `curl` succeeded, and the browser still failed.
- The `raw.githubusercontent.com` CDN has its own cache, independent of
  git's own consistency — a fresh push can take up to a few minutes to be
  visible to that specific fetch path, worth knowing before assuming a
  publish "didn't work."
- A tunnel process can silently wedge (stay running, stop actually
  forwarding traffic) without any error in its own logs — found live.
  `status-infra.sh` health-checks the currently published URL specifically
  so this is visible without a manual `curl`.
- This mechanism has no authentication of its own — anyone who discovers a
  currently-published tunnel URL can hit the live backend directly,
  bypassing the frontend. Acceptable for a demo environment with no
  persistent secrets behind it (ADR-004's $0 constraint already keeps real
  data off these deploys); would need real API-level auth (already present
  for user-owned endpoints via user-service's JWT, §3.5) if that
  assumption ever changes.
