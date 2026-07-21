# ADR-004: $0 Cloud Budget — Local-First, Ephemeral Free-Tier Clusters

**Status:** Accepted
**Date:** 2026-07-21
**Deciders:** Project owner

## Context

The project must demonstrate cloud deployment (Terraform, Kubernetes, AWS and
Azure) but the budget is strictly $0 — no monthly cost is acceptable, however
small. Free tiers have hidden traps: hourly public-IPv4 billing (AWS),
hourly Standard Load Balancer billing (Azure), ACR Basic ~$5/month, EKS
control plane ~$73/month, and both providers' free windows expire.

## Decision

1. **Local-first**: the developer laptop (podman-based Kubernetes) is the
   primary environment; the FULL system runs and is verified there.
2. **Non-billable account types only**: Azure free account with spending
   limit ON; AWS post-2025 free-account credits plan. Neither can invoice.
3. **Ephemeral clusters**: `terraform apply` → demo → `terraform destroy`.
   Nothing runs unattended in the cloud.
4. **Cost-shaped choices**: AKS (free control plane) on Azure; single-node
   k3s on EC2 instead of EKS on AWS; ghcr.io instead of ACR/ECR; NodePort
   on node public IP instead of cloud load balancers; zero-spend budget
   alarms as the first Terraform resources.

## Options Considered

**Always-on free hosting (Render/Railway/Fly)** — earlier draft; rejected:
teaches PaaS clicks, not Terraform/Kubernetes, and free tiers there throttle
or expire too.

**Paid minimal cloud (~$20/month)** — rejected: budget is $0, hard.

## Consequences

- Easier: no billing anxiety; unlimited local iteration; demos are
  reproducible from scratch (apply→destroy proves the IaC actually works —
  itself a strong portfolio point).
- Harder: cloud runs only a core service slice (1-2 GB nodes); Kafka and
  full ELK stay local-only; demos require a ~15-minute spin-up.
- Revisit: if employed/funded, the same Terraform promotes to real node
  pools with only variable changes.
