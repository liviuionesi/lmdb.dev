# ADR-007: Distributed Tracing Now (Micrometer Tracing + Zipkin)

**Status:** Accepted
**Date:** 2026-07-21
**Deciders:** Project owner

## Context

Observability had two of three pillars planned (Prometheus metrics, ELK
logs); tracing was deferred. In a distributed request path — React app →
gateway → movie-service → TMDB — tracing is the tool that makes the
architecture *visible*, and "show me a trace" is a routine ask in system
design interviews.

## Decision

Adopt **Micrometer Tracing (Brave bridge) + Zipkin** now, across gateway and
all services: trace/span IDs propagate via W3C headers, appear in the JSON
logs (correlating ELK with traces), and export to a Zipkin container in the
local profiles. Sampling: 100% locally, configurable for cloud.

## Options Considered

**Zipkin (chosen)** — single lightweight container, first-class Spring Boot
support, zero config UI.

**Jaeger/Tempo + Grafana** — richer at scale, heavier to run; unnecessary
for this system's size. The Micrometer abstraction keeps the exporter
swappable if this changes.

**OpenTelemetry Collector pipeline** — most "modern," but adds a collector
hop with no benefit at this scale; Micrometer can export OTLP later without
code changes.

## Consequences

- Easier: cross-service debugging; a demo trace showing cache-hit vs
  TMDB-fallback latency difference tells the whole facade story in one
  screenshot.
- Harder: minor per-service dependency/config additions; trace context must
  be preserved through the gateway's reactive filters.
- Revisit: switch exporter to OTLP/Tempo if the Grafana stack consolidates.
