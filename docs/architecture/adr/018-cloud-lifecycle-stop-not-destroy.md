# ADR-018: Cloud Lifecycle — Stop-Not-Destroy Between Demo Sessions

**Status:** Accepted
**Date:** 2026-08-11
**Author:** Liviu Ionesi
**Deciders:** Liviu Ionesi
**Related Issues:** #160 (Story: Professional Cloud Lifecycle Management)
**Supersedes:** N/A — new decision

---

## Context

Filmpire runs on ephemeral free-tier cloud compute (Azure AKS
`Standard_D4ls_v7`, AWS EC2 k3s node). The system is a portfolio demo, not a
production service, so it does not need to run 24/7. The question is: when a
demo session ends, should we **destroy** the infrastructure (`terraform destroy`)
or **stop** the compute (VM de-allocation / EC2 stop)?

### Options considered

| Option | Hourly cost while idle | Boot time on next visit | Data survival | Effort |
|--------|------------------------|-------------------------|---------------|--------|
| **A: Always running** | ~$0.21/hr (~$5.06/day) | Instant | ✅ | None |
| **B: Stop compute** (`az aks stop` / `ec2 stop`) | ~$0.01/hr (~$0.25/day, disks only) | ~2–3 min | ✅ PVCs intact | Single command |
| **C: Destroy infra** (`terraform destroy`) | $0.00 | ~8–12 min + Terraform apply | ❌ PVCs deleted | Two commands + TF state |

---

## Decision

**Option B — stop compute between demo sessions, destroy only on long breaks.**

`az aks stop` (Azure) and `aws ec2 stop-instances` (AWS) de-allocate VM cores,
eliminating the dominant ~$0.20/hr compute charge. Azure Disk PVCs and AWS EBS
volumes remain, preserving all database state (Postgres, MongoDB, Redis, Ollama
model weights). This costs ~$0.25/day for Azure (~$7.50/month if idle all month)
— acceptable for a free-tier subscription that has $100–$200 in credits.

`terraform destroy` is reserved for:
- End-of-semester / long breaks (> 1 month of inactivity).
- When the entire Terraform state needs to be rebuilt (e.g. region migration).
- Triggered manually via the `destroy.yml` GitHub Actions workflow.

---

## Implementation

| Script / Gradle Task / Workflow | Purpose |
|---|---|
| `./gradlew startAzure` (`start-azure.sh`) | Starts AKS, waits all 9 workloads Ready, auto-updates DuckDNS (~2m) |
| `./gradlew stopAzure` (`stop-azure.sh`) | Stops AKS compute, waits for full de-allocation, prints cost summary ($0 compute) |
| `./gradlew startAws` (`start-aws.sh`) | Starts AWS k3s EC2 instance, updates DuckDNS (~1m) |
| `./gradlew stopAws` (`stop-aws.sh`) | Stops AWS k3s EC2 instance ($0 compute, EBS preserved) |
| `./gradlew stopAllClouds` (`stop-all-clouds.sh`) | Detects and stops all running clouds (Azure, AWS, Minikube) in one command |
| `./gradlew statusInfra` (`status-infra.sh`) | Health check for Local, Tunnel, Azure, and AWS endpoints |
| `.github/workflows/deploy.yml` | **Smart Deploy**: Auto-wakes stopped clusters or auto-provisions if destroyed, password-gated |
| `.github/workflows/cluster-stop.yml` | Remote start/stop from GitHub Actions UI (protected by `DEPLOY_PASSPHRASE`) |
| `.github/workflows/destroy.yml` | Full `terraform destroy` — password-gated, requires confirmation `DESTROY` |

### Lifecycle for a typical demo day

```bash
# Morning: Resume cloud compute (~1-2 min, database data intact)
./gradlew startAzure
# OR:
./gradlew startAws

# Demo:
# https://filmpire-microservices-tan.vercel.app/

# Evening: Stop cloud compute to bring compute spend to $0
./gradlew stopAzure
# OR stop everything:
./gradlew stopAllClouds
```

---

## Consequences

### Positive
- Credits last ~10–20× longer than always-on.
- All database state (users, ratings, Ollama model weights) survives between sessions.
- 2–3 min restart is acceptable for a portfolio demo (standby modal plays while cluster wakes).
- `stop-all-clouds.sh --dry-run` is safe to add to end-of-day reminders.

### Negative
- ~$0.25/day idle cost even when stopped (Azure Disk PVCs + public IP).
- If the developer forgets to stop, the cluster runs indefinitely at full cost.
  Mitigation: `auto-stop-watchdog.sh` checks inactivity and can stop automatically.

### Neutral
- DuckDNS must be re-updated on each `az aks start` because AKS node IPs are
  ephemeral (new IP on each node pool start). `start-azure.sh` handles this
  automatically when `DUCKDNS_TOKEN` is exported.
- `resolveApiUrl()` in the frontend falls back to the Cloudflare tunnel URL and
  then returns `null` (standby modal) when all tiers fail — the 2–3 min startup
  time is covered by the cinematic trailer standby experience.
