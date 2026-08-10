# Deployment Guide

How to run Filmpire end-to-end — local only, local backend + the deployed
Vercel frontend, Azure AKS, and AWS EC2/k3s — and how the frontend finds
whichever backend is actually up in each case.

Deploys are triggered **locally via Gradle tasks**, not from the web. The
`/admin` "Launch/Destroy" button and its serverless proxy were removed
(#151) specifically so no public URL can trigger cloud spend — only
someone with a shell on this machine (and cloud credentials) can deploy or
destroy anything.

## 1. How the frontend finds its backend

Every real request from the React app goes through
[`frontend/filmpire/src/utils/apiUrl.js`](../../frontend/filmpire/src/utils/apiUrl.js),
which resolves a backend URL in this order, **every time, automatically —
no manual step in any of the scenarios below unless noted**:

1. `localStorage.filmpire_api_url` — a manual pin. Nothing sets this
   automatically anymore; only useful if you open devtools and set it
   yourself to force a specific backend.
2. `VITE_API_URL` — a build-time env var. Unset in Vercel today.
3. `http://localhost:8080` — if the code itself is running on `localhost`
   (i.e. you're running `npm run dev` locally).
4. `https://filmpire-api.duckdns.org` — the cloud default, **only if it
   passes a health check** (`GET /actuator/health`).
5. Whatever URL is published in [`infrastructure/tunnel-url.txt`](../../infrastructure/tunnel-url.txt)
   — **only if that also passes a health check.** This file is
   auto-published by `start-tunnel.sh`/`deploy-azure.sh` on every run (see
   §2 and §3) and read by the frontend from
   `raw.githubusercontent.com/.../develop/infrastructure/tunnel-url.txt`,
   so a fresh push makes it visible within seconds, no redeploy needed.
6. The cloud URL anyway, as a last resort (so the app fails predictably
   instead of silently).

Resolution is cached 30s per browser tab so it isn't re-probed on every
request. **Consequence:** whichever backend is actually reachable when the
above runs wins — you don't "point" the frontend at Azure vs AWS vs local,
you just make sure the right one is the one currently answering health
checks.

## 2. Prerequisites

| Tool | Needed for |
|---|---|
| Docker or Podman (+ compose) | Local backend, both tunnel scripts |
| `terraform` >= 1.9 | Azure, AWS |
| `az` CLI, logged in (`az login`) | Azure |
| `aws` CLI v2, configured | AWS |
| `kubectl` | Azure, AWS |
| An SSH key pair (`ssh-keygen -t ed25519`) | AWS only — see §4 |

One-time cloud bootstrap (remote Terraform state, `terraform.tfvars`,
`backend.hcl`) is **not** repeated here — follow
[`infrastructure/terraform/README.md`](../../infrastructure/terraform/README.md)
first if you've never applied either cloud before. This guide assumes
that's already done.

All `./gradlew` tasks below just shell out to a script in
`infrastructure/scripts/` — run the script directly if you want to skip
Gradle's daemon startup overhead.

## 3. Scenario: local only

For local development against `localhost:3000`/`5173`. No frontend binding
needed — §1 rule 3 handles it automatically.

```bash
./gradlew deployLocal      # infrastructure/scripts/start-infrastructure.sh
# ...or with a public tunnel too, if you also want the deployed Vercel
# site to reach this local stack (see §4):
./gradlew deployLocal --args='--tunnel'   # or: ./infrastructure/scripts/start-infrastructure.sh --tunnel
```

Brings up the full `docker-compose.yml` stack (Postgres, MongoDB, Redis,
MinIO, Elasticsearch/Kibana, Eureka, Config Service, all seven app
services). Verify:

```bash
./gradlew statusInfra
```

Teardown:

```bash
./gradlew stopLocal
```

## 4. Scenario: local backend + the deployed Vercel frontend

Use this to make `https://filmpire-microservices-tan.vercel.app` talk to
your own machine — e.g. when Azure/AWS are torn down (the normal $0-budget
state) but you still want to demo against something real.

```bash
./gradlew deployLocal          # if not already up
./gradlew startTunnel          # infrastructure/scripts/start-tunnel.sh
```

`start-tunnel.sh` launches a Cloudflare quick tunnel (`cloudflared`, no
account needed) pointed at `http://localhost:8080`, then **writes the
resulting HTTPS URL to `infrastructure/tunnel-url.txt` and
commits+pushes it to `develop`** — this is what makes §1 rule 5 work for
*anyone* visiting the deployed site, not just your own browser. Expect a
small `chore: publish local tunnel URL...` commit to appear every time you
run this; that's the mechanism working, not a mistake.

**Why a tunnel and not just your public IP:** the deployed frontend is
HTTPS; browsers block "mixed content" (an HTTPS page fetching a plain
`http://` URL) outright. `cloudflared` gives you a real HTTPS endpoint
without a certificate or port-forwarding.

The tunnel URL is **ephemeral** — a new random hostname every restart.
Don't hardcode it anywhere; the point of the pointer file is that nothing
needs to.

Teardown:

```bash
./gradlew stopTunnel
```

## 5. Scenario: Azure AKS

```bash
./gradlew deployAzure          # infrastructure/scripts/deploy-azure.sh
```

This single command: `terraform apply` (provisions AKS on
`Standard_D4ls_v7`, 4 vCPU/8GB) → `az aks get-credentials` → `kubectl
apply -k infrastructure/kubernetes/overlays/azure` (full local-parity
service set — gateway, movie/actor/user/ai-service, MongoDB, Postgres,
Redis, Ollama; see §6.1 for what's still missing and why) → waits for
rollout → pulls Ollama's models (one-time, same manual step as local dev)
→ prints the node's public IP.

The gateway is exposed as a **NodePort** (`:30080`, no load balancer — see
`infrastructure/terraform/README.md`) at that raw IP, over plain HTTP.
Same mixed-content problem as §4: you can `curl` it directly, but a
browser on the deployed HTTPS frontend can't. **Front it with a tunnel the
same way:**

```bash
docker run -d --name filmpire-azure-tunnel --network host \
  docker.io/cloudflare/cloudflared:latest \
  tunnel --no-autoupdate --url http://<NODE_IP>:30080
docker logs filmpire-azure-tunnel | grep -o 'https://[a-zA-Z0-9-]*\.trycloudflare\.com'
```

Then publish that URL the same way `start-tunnel.sh` does:

```bash
echo "<tunnel-url>" > infrastructure/tunnel-url.txt
git add infrastructure/tunnel-url.txt && git commit -m "chore: publish Azure tunnel URL (#151)" && git push origin develop
```

(This manual step isn't yet folded into `deploy-azure.sh` — worth adding
if Azure becomes the routine target rather than an occasional demo.)

**CORS note:** the gateway's allow-list
(`backend/api-gateway/.../SecurityConfig.java`) has to include the
frontend's actual origin — `https://filmpire-microservices-tan.vercel.app`
plus origin *patterns* for `*.vercel.app`/`*.trycloudflare.com`/
`*.duckdns.org` are already in there. If you ever change the Vercel
domain, that's the file to update.

**Image freshness:** the deployed image is whatever `ghcr.io/pehlivanu/
filmpire-*:latest` was at the last successful `Docker Publish` run — which
only fires after a **green Backend CI on `main`**
(`.github/workflows/docker-publish.yml`). If CI is red, deploys silently
run stale code. Check `gh run list --workflow="Backend CI"` before
assuming a fresh deploy has your latest changes; if you need to force it,
`kubectl rollout restart deployment/api-gateway deployment/movie-service`
after CI/publish goes green.

Verify:

```bash
curl http://<NODE_IP>:30080/actuator/health
curl http://<NODE_IP>:30080/movie/popular
./gradlew statusInfra   # also shows AKS node/pod status if kubectl context is set
```

Teardown (**always do this — Azure's free spending-limit account is the
only thing keeping this at $0, not a literal free SKU**):

```bash
docker rm -f filmpire-azure-tunnel   # if you started the extra tunnel above
./gradlew destroyAzure                # infrastructure/scripts/destroy-azure.sh
```

### 5.1 What's still not in either cloud overlay

`media-service` (no Kubernetes manifests exist for it yet — it'd also need
an object-storage decision, MinIO locally) and the observability-only
services (`discovery-service`/Eureka, `config-service`, Kafka, Zipkin —
ADR-005 keeps these out of every K8s overlay deliberately; K8s Services +
cluster DNS already cover what Eureka/Config Server do locally, and
Kafka/Zipkin are internal analytics/tracing, not a user-facing feature).
Everything else that runs locally now runs identically in the cloud (#151).

## 6. Scenario: AWS EC2/k3s

Same shape as Azure, self-managed k3s on a single `t3.xlarge` instead of a
managed control plane (bumped from `t3.small` alongside Azure's resize —
Ollama alone needs up to 4Gi).

```bash
./gradlew deployAws            # infrastructure/scripts/deploy-aws.sh
```

One extra prerequisite Azure doesn't have: an SSH keypair. Your
**public** key goes in `infrastructure/terraform/aws/terraform.tfvars`
(`ssh_public_key`) before the first apply; the node is provisioned with
it baked in. There's no AWS equivalent of `az aks get-credentials` — the
script/workflow fetches `kubeconfig` over SSH using that key's private
half.

Same NodePort (`:30080`) / mixed-content / tunnel-fronting / CORS /
image-freshness notes as §5 all apply identically.

Teardown:

```bash
./gradlew destroyAws
```

### If you use the GitHub Actions path instead

`.github/workflows/deploy.yml`/`destroy.yml` (`workflow_dispatch`, cloud
picker) are still in the repo as an alternate CI-driven path — useful if
you want deploys to run somewhere other than your own machine. They need
these repo secrets/vars (already configured as of 2026-08-10):

| Name | Kind | Used for |
|---|---|---|
| `AZURE_CLIENT_ID`/`AZURE_TENANT_ID`/`AZURE_SUBSCRIPTION_ID` | vars | Azure OIDC login |
| `AWS_ROLE_ARN`, `AWS_REGION` | vars | AWS OIDC login |
| `TF_STATE_*` (bucket/container/resource group/storage account/table) | vars | Terraform remote state |
| `DUCKDNS_TOKEN` | secret | Points `filmpire-api.duckdns.org` at whichever cloud IP just deployed |
| `AWS_K3S_SSH_PRIVATE_KEY` | secret | AWS-only — fetches kubeconfig over SSH |

The Gradle tasks in §5/§6 are the primary path now; this table is here so
the CI path doesn't bit-rot silently if picked back up later.

## 7. Deploying the frontend itself

Push to `main` — Vercel's git integration auto-builds
`frontend/filmpire` and redeploys `filmpire-microservices-tan.vercel.app`.
That's the actual live mechanism; confirm a deploy happened by checking
the served JS bundle hash changed (`curl -s <url> | grep -o 'assets/index-[a-zA-Z0-9]*\.js'`).

`infrastructure/scripts/deploy-vercel.sh` is a manual alternative (CLI
`vercel --prod` deploy) if you ever need to deploy without going through
`main` — not the routine path.

**Vercel env vars `GITHUB_TOKEN`/`ADMIN_DISPATCH_SECRET`**, set earlier for
the now-deleted `/admin` deploy button, are dead/unused since #151's
refactor. Harmless to leave, safe to delete next time you're in the Vercel
dashboard.

## 8. Troubleshooting

- **Movies don't load, `filmpire-api.duckdns.org` doesn't resolve:** cloud
  is torn down (expected — that's the $0-budget default state). Bring up
  local + tunnel (§4) or a cloud target (§5/§6); the frontend picks either
  up automatically once its health check passes.
- **CORS error in the browser console:** the gateway's `SecurityConfig.java`
  allow-list doesn't cover the frontend's current origin. Check `curl -I
  -X OPTIONS -H "Origin: <frontend-origin>" <backend>/actuator/health` for
  a `access-control-allow-origin` header.
- **A cloud deploy looks successful but old behavior persists:** almost
  always a stale `:latest` image — see the "Image freshness" note in §5.
  Check `gh run list --workflow="Docker Publish"`.
- **Frontend seems to ignore a fresh local tunnel:** `tunnel-url.txt`
  updates propagate through `raw.githubusercontent.com`, which can lag a
  push by up to ~5 minutes on a cold cache. Also check the manual
  `localStorage.filmpire_api_url` override isn't set to something stale in
  your browser (devtools → Application → Local Storage).
