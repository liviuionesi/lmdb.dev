# ADR-019: Azure Zero-Touch Auto-Wake / Auto-Sleep

**Status:** Accepted
**Date:** 2026-08-12
**Author:** Liviu Ionesi
**Deciders:** Liviu Ionesi
**Related Issues:** #160 (Story: Professional Cloud Lifecycle Management), this fix
**Supersedes:** N/A — closes gaps ADR-018 left open
**Amends:** ADR-015 (see "Relationship to ADR-015" below) — narrows its
"no public cloud-spend trigger" rule rather than reversing it

---

## Context

ADR-018 decided *stop, don't destroy* for idle cloud compute, and #160 built
the scripts/workflow to do it on command. The remaining promise — visiting
`https://lmdb.dev/` alone should wake a stopped
Azure backend, with no push/commit/manual step, and the backend should sleep
itself again after an hour of inactivity — turned out not to work when
actually tested live against the deployed frontend. Tracing the real request
path (not just re-reading the code) found three separate, independent gaps:

1. **The wake-up dispatch never worked.** `api/wakeup.js` (Vercel serverless)
   called the GitHub Actions API to dispatch `deploy.yml`, but omitted the
   `passphrase` input that workflow requires — the dispatch call always got
   rejected (422), and the failure was swallowed in a `catch` with only a
   `console.warn`. It also targeted the wrong workflow: `deploy.yml` runs a
   full `terraform apply`; the already-built `cluster-stop.yml` start action
   is the right non-destructive tool for "resume what's already provisioned."
2. **Azure has no HTTPS anywhere.** The gateway is a plain-HTTP NodePort (no
   Standard LB — ADR-004's $0-budget stance), but the frontend is HTTPS and
   browsers block that mixed content. `docs/guides/DEPLOYMENT_GUIDE.md` §5
   candidly documented the only working bridge as a **manual** `cloudflared`
   tunnel plus hand-committing the resulting URL to a text file — workable
   for an occasional demo, incompatible with "no manual step, ever."
3. **The 1-hour idle auto-stop never ran.** `auto-stop-watchdog.sh` (ADR-018's
   own stated mitigation for "developer forgets to stop the cluster") existed
   but nothing scheduled it — no cron, no GitHub Actions `schedule:` trigger.
   It only ever did anything if someone happened to run it by hand.

A fourth thing found while verifying live: ADR-018 stated AKS node IPs are
ephemeral on every start. Checked directly against the live VMSS
(`az vmss list-instance-public-ips`) — the node's public IP is actually
**Static**, so it survives `az aks stop`/`start`. That statement in ADR-018
was wrong (or the config changed since); this ADR corrects it, since it's
what makes option A below viable at all.

---

## Decision

### 1. TLS bridge: a Caddy pod on the node's own static IP

Rather than a Standard Load Balancer + ingress-nginx + cert-manager (real
Azure cost, conflicts with ADR-004), `infrastructure/kubernetes/overlays/
azure/caddy-tls.yaml` runs a single Caddy pod with `hostNetwork: true` on the
existing node, binding its already-static public IP. Caddy auto-obtains a
real Let's Encrypt certificate for `api.lmdb.dev` and
reverse-proxies to the gateway's ClusterIP Service. The issued cert is kept
on a small PVC so it survives pod restarts and stop/start cycles instead of
re-issuing (and risking Let's Encrypt's rate limit) every time. NSG rules for
80 (ACME HTTP-01 challenge) and 443 (HTTPS) were added alongside the existing
NodePort 30080 rule in `infrastructure/terraform/modules/network`.

### 2. Wake-up dispatch: fixed target, passphrase mirrored server-side

`api/wakeup.js` now dispatches `cluster-stop.yml` with `action: start` and
includes the passphrase — read from a `DEPLOY_PASSPHRASE` Vercel environment
variable that mirrors the GitHub repo secret. This is server-side only (the
file runs as a Vercel serverless function, never shipped to the browser
bundle), so the passphrase gate still means something for a human dispatching
from the GitHub UI directly, while Vercel's own backend — which only the
account owner controls — can pass it automatically. A failed dispatch now
returns a real `ERROR` status with the reason instead of silently pretending
`WAKING_UP`.

### 3. Idle auto-stop: an actual scheduled workflow

`.github/workflows/cluster-idle-stop.yml` runs on a `schedule: */10 * * * *`
cron. It first does a cheap unauthenticated curl of the gateway's
`/actuator/activity` endpoint; only when `idleSeconds >= 3600` does it OIDC-
login and stop whichever cloud is actually running (`az aks stop` /
`aws ec2 stop-instances` — same non-destructive calls as `cluster-stop.yml`).
Cron triggers can't be invoked by an external actor, so this doesn't need the
`DEPLOY_PASSPHRASE` gate the human-dispatch workflows use. This supersedes
`auto-stop-watchdog.sh` as the actual mechanism; the script stays in the repo
as the pattern a developer can also run by hand from their own machine.

---

## Relationship to ADR-015

ADR-015 (2026-08-11) removed every public web-triggered path to cloud spend
outright, reasoning that a passphrase behind an unauthenticated page "raises
the bar for finding it, not what it can do once found." `/api/wakeup`
reopens exactly the category of surface that decision closed: an endpoint
reachable by anyone who loads the site, no discovery required at all — a
strictly larger exposure than the `/admin` button ADR-015 killed.

This was a deliberate, conscious call, not an oversight: the feature
requested — "visiting the page alone wakes the backend, no manual step" —
is unsatisfiable without some publicly-reachable trigger; there's no version
of "zero manual step" that doesn't require the page load itself to be the
trigger. Confirmed explicitly with the project owner before implementing
(rather than building it quietly and burying the tradeoff in a code
comment), on these grounds distinguishing it from what ADR-015 removed:

- **Narrower action.** ADR-015's button could dispatch `deploy.yml`/
  `destroy.yml` — full `terraform apply`/`destroy`, capable of creating or
  tearing down real infrastructure. `/api/wakeup` can only dispatch
  `cluster-stop.yml`'s `start` action — resume compute that's already
  provisioned, and a no-op if it's already running. It cannot provision,
  destroy, or modify anything.
- **Bounded worst case.** `cluster-idle-stop.yml` (this ADR, part 3) caps
  how long an abused trigger can keep compute running: idle for over an
  hour and it stops itself regardless of how it got started. Worst case is
  "runs at ~$0.21/hr for as long as it keeps receiving real traffic," not
  unbounded spend.
- **Same passphrase mechanism, acknowledged as a deterrent only** — per
  ADR-015's own finding, this does not raise the security bar, only the
  discovery bar for anyone bypassing the frontend and calling the GitHub
  API directly. The actual containment is the two points above, not the
  passphrase.

If idle cost ever becomes meaningfully non-zero, or this endpoint is ever
observed being hit by something other than real visitors, revisit this
exception the same way ADR-015 revisited its own iteration 2.

---

## Consequences

### Positive
- Visiting the Vercel URL is genuinely the only action required — no push,
  no manual tunnel, no hand-edited DNS record — for both waking and
  sleeping the Azure backend.
- The 2–3 min movie-trailer standby window (`BackendStandbyModal`) now
  actually corresponds to real wake-up time instead of a wait that never
  ends because nothing was dispatched.
- No new billable Azure resource — Caddy runs on compute that's already
  paid for while the cluster is up, and costs nothing while stopped.

### Negative
- One more component (Caddy) in the Azure workload count (10 instead of 9)
  and one more secret to keep in sync between GitHub and Vercel
  (`DEPLOY_PASSPHRASE`).
- `cluster-idle-stop.yml`'s 10-minute cron means the actual stop can lag the
  1-hour mark by up to 10 minutes — acceptable for a $0.25/day idle cost,
  not appropriate if idle cost ever became meaningfully non-zero.
- Caddy's `hostNetwork` binding assumes a single node; if the node pool ever
  scales beyond one node, the Deployment would need a `nodeSelector`/anti-
  affinity to stay pinned, or the whole approach revisited in favor of a
  proper LoadBalancer.

### Neutral
- AWS keeps its own existing wake/sleep path unchanged by this ADR — only
  Azure lacked the HTTPS bridge; `cluster-idle-stop.yml`'s AWS job reuses
  the same idle signal since both clouds are fronted by the same DuckDNS
  hostname (only one cloud is "live" at a time in practice).
