# Deployment Guide

How to run LMDB end-to-end — local only, local backend + the deployed
Vercel frontend, Azure AKS, and AWS EC2/k3s — and how the frontend finds
whichever backend is actually up in each case.

Deploys are triggered **locally via Gradle tasks**, not from the web. The
`/admin` "Launch/Destroy" button and its serverless proxy were removed
(#151) specifically so no public URL can trigger cloud spend — only
someone with a shell on this machine (and cloud credentials) can deploy or
destroy anything.

## 1. How the frontend finds its backend

Every real request from the React app goes through
[`frontend/lmdb/src/utils/apiUrl.js`](../../frontend/lmdb/src/utils/apiUrl.js),
which resolves a backend URL automatically, every time — no manual step in
any of the scenarios below unless noted. The exact tier order (manual
override → localhost → cloud default → DuckDNS fallback → published
tunnel) is documented once, in
[ARCHITECTURE.md §11.6](../architecture/ARCHITECTURE.md#116-dynamic-backend-resolution-one-frontend-deploy-any-live-backend) —
not repeated here to avoid the two drifting apart, which is what happened
before this pass (this section and two other docs each described a
different, and in one case wrong, tier order for the same code).

Resolution is cached 30s per browser tab so it isn't re-probed on every
request. **Consequence:** whichever backend is actually reachable when
resolution runs wins — you don't "point" the frontend at Azure vs AWS vs
local, you just make sure the right one is the one currently answering
health checks.

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

### Complete Gradle Deployment Command Reference

| Task | Command | What It Does |
|---|---|---|
| **`statusInfra`** | `./gradlew statusInfra` | Health check for Local, Tunnel, Azure, and AWS |
| **`deployLocal`** | `./gradlew deployLocal` | Starts local Docker/Podman microservices & databases |
| **`stopLocal`** | `./gradlew stopLocal` | Stops local Compose, Minikube, Vite, and Tunnel |
| **`startTunnel`** | `./gradlew startTunnel` | Starts public Cloudflare HTTPS tunnel for local/Minikube gateway |
| **`stopTunnel`** | `./gradlew stopTunnel` | Stops the Cloudflare tunnel container |
| **`startAzure`** | `./gradlew startAzure` | Starts stopped Azure AKS cluster compute nodes & updates DuckDNS |
| **`stopAzure`** | `./gradlew stopAzure` | Stops AKS cluster compute nodes ($0 compute spend, preserves disks) |
| **`startAws`** | `./gradlew startAws` | Starts stopped AWS EC2 k3s instance & updates DuckDNS |
| **`stopAws`** | `./gradlew stopAws` | Stops AWS EC2 k3s instance ($0 compute spend, preserves EBS) |
| **`stopAllClouds`** | `./gradlew stopAllClouds` | Detects and stops all running clouds (Azure, AWS, Minikube) at once |
| **`autoStopWatchdog`** | `./gradlew autoStopWatchdog` | Shuts down cloud compute if idle for > 1 hour |

---

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

Use this to make `https://lmdb.dev` talk to
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
./gradlew startAzure           # resumes stopped AKS compute nodes (~2 min)
# OR for full initial provisioning:
./gradlew deployAzure          # infrastructure/scripts/deploy-azure.sh
```

This single command: `terraform apply` (provisions AKS on
`Standard_D4ls_v7`, 4 vCPU/8GB) → `az aks get-credentials` → `kubectl
apply -k infrastructure/kubernetes/overlays/azure` (full local-parity
service set — gateway, movie/actor/user/ai-service, MongoDB, Postgres,
Redis, Ollama) → waits for full 9-workload rollout → auto-updates DuckDNS
(`api.lmdb.dev`).

The gateway itself is exposed as a **NodePort** (`:30080`, no load balancer —
see `infrastructure/terraform/README.md`) at that raw IP, over plain HTTP —
same mixed-content problem as §4, a browser on the deployed HTTPS frontend
can't call it directly. Unlike AWS/local, **Azure no longer needs a manual
tunnel for this** (ADR-019): `infrastructure/kubernetes/overlays/azure/
caddy-tls.yaml` deploys a Caddy pod bound to the node's own (static) public
IP as the 10th workload, alongside the other 9, which gets a real Let's
Encrypt certificate for `api.lmdb.dev` automatically and
reverse-proxies to the gateway. `deploy-azure.sh` rolling out that manifest
and `cluster-stop.yml`'s start action re-pointing DuckDNS is the whole
bridge — no tunnel, no hand-published URL, nothing to redo per session.

Visiting `https://lmdb.dev/` with the backend
asleep is itself enough to wake it: `frontend/lmdb/api/wakeup.js`
health-checks on every hit and dispatches `cluster-stop.yml`'s start action
if the backend doesn't answer, while `BackendStandbyModal` plays a trailer
during the ~2-3 min AKS resume. See ADR-019 for the full mechanism and the
tradeoff it knowingly makes with ADR-015.

**CORS note:** the gateway's allow-list
(`backend/api-gateway/.../SecurityConfig.java`) has to include the
frontend's actual origin — `https://lmdb.dev`
plus origin *patterns* for `*.vercel.app`/`*.trycloudflare.com`/
`*.duckdns.org` are already in there. If you ever change the Vercel
domain, that's the file to update.

**Image freshness:** the deployed image is whatever `ghcr.io/liviuionesi/
lmdb-*:latest` was at the last successful `Docker Publish` run — which
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
docker rm -f lmdb-azure-tunnel   # if you started the extra tunnel above
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

Same shape as Azure, self-managed k3s on a single `m7i-flex.large` instead of a
managed control plane (bumped from `t3.small` alongside Azure's resize —
Ollama alone needs up to 4Gi).

```bash
./gradlew startAws             # resumes stopped EC2 k3s instance (~1-2 min)
# OR for full initial provisioning:
./gradlew deployAws            # infrastructure/scripts/deploy-aws.sh
```

One extra prerequisite Azure doesn't have: an SSH keypair. Your
**public** key goes in `infrastructure/terraform/aws/terraform.tfvars`
(`ssh_public_key`) before the first apply; the node is provisioned with
it baked in. There's no AWS equivalent of `az aks get-credentials` — the
script/workflow fetches `kubeconfig` over SSH using that key's private
half.

Like Azure, **AWS runs the zero-touch Caddy TLS proxy** (`infrastructure/kubernetes/overlays/aws/caddy-tls.yaml`)
bound to host ports 80/443 on the EC2 instance's public IP, auto-fetching a Let's Encrypt
certificate for `api.lmdb.dev`. No manual tunnel is needed when switching between
Azure and AWS.

The deployed frontend automatically displays an interactive **telemetry footer** showing:
- 🟢 **Live Cloud Provider Badge** (`Powered by Microsoft Azure`, `Powered by AWS`, or `Powered by Minikube`)
- ⏱️ **Live Uptime**
- 🌙 **Time to Auto-Sleep Countdown** (derived from the 1-hour idle threshold)
- Target selector modal for on-the-fly environment switching.

Teardown:

```bash
./gradlew stopAws              # stops instance ($0 compute, preserves EBS volume)
# OR full permanent destruction:
./gradlew destroyAws
```

### 6.1 Smart Cloud Deploy in GitHub Actions (Self-Healing & Password-Protected)

`.github/workflows/deploy.yml`, `cluster-stop.yml`, and `destroy.yml` are
automated CI-driven paths. They are **password-protected** to prevent
accidental cloud charges:

| Secret / Variable | Kind | Purpose |
|---|---|---|
| `DEPLOY_PASSPHRASE` | **Secret** | **Mandatory authorization passphrase** required to trigger deploy, stop, or destroy |
| `AZURE_CLIENT_ID`/`AZURE_TENANT_ID`/`AZURE_SUBSCRIPTION_ID` | Variable | Azure OIDC login |
| `AWS_ROLE_ARN`, `AWS_REGION` | Variable | AWS OIDC login |
| `TF_STATE_*` | Variable | Terraform remote state backend |
| `DUCKDNS_TOKEN` | Secret | Updates `api.lmdb.dev` with live node IP |
| `AWS_K3S_SSH_PRIVATE_KEY` | Secret | Fetches kubeconfig over SSH from AWS k3s nodes |

#### Smart Self-Healing Behavior in `deploy.yml`:
- **If the cluster is Stopped** (e.g. from 1-hour auto-stop) → Automatically starts it.
- **If the cluster was Destroyed** (no Terraform state) → Automatically provisions it with Terraform.
- **Full Rollout Check**: Verifies all 9 workloads before completing.
- **GitHub Deployment Environment**: Badges and logs live status under the repo's **Deployments** tab.

## 7. Deploying the frontend itself

Push to `main` — Vercel's git integration auto-builds
`frontend/lmdb` and redeploys `lmdb.dev`.
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

- **Movies don't load, `api.lmdb.dev` doesn't resolve:** cloud
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
  `localStorage.lmdb_api_url` override isn't set to something stale in
  your browser (devtools → Application → Local Storage).
