# ADR-005: Eureka & Config Server vs Kubernetes-Native Alternatives

**Status:** Accepted
**Date:** 2026-07-21
**Deciders:** Project owner

## Context

The stack uses Netflix Eureka for discovery and Spring Cloud Config for
configuration. On Kubernetes, both are largely redundant: K8s Services + DNS
provide discovery, and ConfigMaps/Secrets provide configuration. Running
Eureka *inside* K8s is a well-known anti-pattern when done unknowingly —
double registration, slower failover than kube-proxy, extra pods on
resource-starved free-tier nodes.

## Decision

Keep Eureka + Config Server as first-class components of the
**docker-compose / bare-JVM local profile** (where no orchestrator provides
those capabilities, and where their patterns are the learning objective).
In the **Kubernetes overlays**, services resolve each other via K8s DNS
(`lb://` gateway URIs replaced by `http://movie-service` cluster DNS) and
consume config via ConfigMaps generated from the same native config files —
Eureka and Config Server are simply not deployed there.

## Options Considered

**Eureka everywhere (including K8s)** — rejected: wastes ~512 MB on nodes
that have 1–2 GB total, and demonstrates unawareness rather than skill.

**K8s-native everywhere, delete Eureka/Config** — rejected: they are already
built, tested, and are precisely the Spring Cloud portfolio material the
project exists to demonstrate; the compose profile keeps them meaningful.

## Trade-off Analysis

Maintaining two wiring profiles costs some configuration duplication
(Kustomize overlays patch the discovery/config env vars). In exchange, the
project demonstrates BOTH the classic Spring Cloud stack AND the judgment to
know where it does not belong — which is exactly the distinction a tech lead
is expected to articulate.

## Consequences

- Easier: free-tier nodes fit the core slice; K8s deployments have fewer
  moving parts and faster failover.
- Harder: overlay-specific configuration must be kept in sync with the
  compose profile.
- Revisit: if the K8s profile becomes primary, deprecate the compose-profile
  Eureka path entirely.
