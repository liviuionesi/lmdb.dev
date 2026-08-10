# Filmpire — Microservices Platform & Multi-Cloud Deployment Engineering

**A TMDB v3 API clone with its own persisted, growing dataset, built as a real
8-service Spring Boot system and deployed — live, verified, torn down and
rebuilt more than once — on both Azure and AWS free tiers, for $0.**

This isn't a tutorial project. Nothing in it was built once and left alone —
every architectural claim below was hit against real infrastructure, broke
in a real way at least once, and got fixed. That's the part worth reading
closely if you're evaluating this as evidence of tech-lead or platform
engineering capability rather than as a movie app.

**Live:** [filmpire-microservices-tan.vercel.app](https://filmpire-microservices-tan.vercel.app/)
— the frontend is always up; the backend it talks to is whichever of
local/Azure/AWS happens to be running at the time (see
[why that's not a broken link](#one-frontend-deploy-any-live-backend) below).

---

## What this actually is

The existing [Filmpire](https://github.com/pehlivanu/filmpire) React app
(RTK Query, MUI, Vosk voice control) was originally a thin client for the
real `api.themoviedb.org`. This backend replicates that API's exact v3
surface — same paths, same query parameters, same JSON shapes — closely
enough that the frontend runs against it by changing **only its base URL**.

What sits behind that surface is not a cache of TMDB's bytes. Every
response is mapped into Filmpire's own typed, persisted catalog
(MongoDB/PostgreSQL), fetched from TMDB once per resource and served
locally after that — the dataset genuinely grows from real traffic, and a
detail page for a movie no one's looked at yet still costs one upstream
call, not zero. That distinction — a real data platform behind a
TMDB-shaped facade, not a reverse proxy — was itself a mid-project pivot
(ADR-010), one of seventeen recorded architecture decisions, each with the
options that were rejected and why.

## What this demonstrates

If you're evaluating this for a **tech lead or modernization engagement**,
here's what's actually on display, not just claimed:

- **Real multi-cloud operations, not "wrote some Terraform."** Azure AKS
  and AWS EC2/k3s were both stood up, torn down, and rebuilt live —
  repeatedly. Along the way: AKS's undocumented 2vCPU/4GB minimum, an
  entire Azure VM-size family blocked at the subscription level, AWS
  rejecting an instance type outright with a Free-Tier-eligibility error
  that only surfaces at `apply` time, a live node resize that needed
  `terraform plan` actually read before applying (a naive in-place resize
  would have force-replaced the whole cluster), and a MongoDB deployment
  that OOM-killed itself *during its own first-boot user creation*,
  leaving auth enabled with no valid user — recoverable only by wiping the
  volume, not by restarting the pod. Every one of these is documented with
  what broke, why, and the fix — see [ADR-017](docs/architecture/adr/017-full-cloud-service-parity.md)
  and [§11.5 of the architecture doc](docs/architecture/ARCHITECTURE.md#115-whats-actually-deployed-and-what-it-costs).
- **Cost engineering backed by real numbers, not assumptions.** Before
  resizing a live node, actual Azure Retail Pricing API rates were pulled
  ($0.234/hr for the size in question) and reasoned about explicitly: even
  left running continuously for a month, that's ~$171 — comfortably inside
  a free credit for realistic demo usage, but not "free" in the naive
  sense, and the distinction matters when you're the one accountable for
  the bill.
- **A real security decision, not a checkbox.** The admin panel originally
  had a "deploy to cloud" button, first secured with a client-side token
  (wrong — Vite ships `VITE_`-prefixed env vars into the public bundle),
  then with a server-side proxy behind a passphrase (better, but the page
  itself had no login). Rather than adding a third layer of mitigation,
  the button was removed outright — deploys now require a shell on the
  operator's own machine. [ADR-015](docs/architecture/adr/015-local-only-deploy-trigger.md)
  records the reasoning, including the two earlier attempts that weren't
  good enough.
- **An engineering problem solved properly, not worked around.** The
  frontend deploys once; the backend it should reach changes constantly —
  local, Azure, AWS, or (the normal steady state) nothing at all, since
  clusters are ephemeral by design. Rather than a fixed URL and manual
  redeploys, the frontend health-checks a priority list of candidates per
  request and automatically uses whichever one is actually alive, fronted
  by an ephemeral HTTPS tunnel (solving the real mixed-content problem a
  free-tier NodePort creates) whose current address is published through
  the repo itself. One Vercel deployment, touched once, correctly finds
  whichever backend is running, with zero redeploys and zero per-visit
  configuration. Full writeup: [ADR-016](docs/architecture/adr/016-dynamic-backend-resolution.md).
- **Scrum process artifacts that are actually load-bearing, not
  decorative.** Every non-trivial decision is a numbered ADR with
  rejected alternatives, not just the chosen path (17 so far). Every issue
  follows Epic→Story→Task with Given/When/Then acceptance criteria against
  standing Definition-of-Ready/Done docs. This repo's own `CLAUDE.md`
  encodes the actual working contract (lock/halt files for unattended
  runs, commit conventions, issue-hygiene rules) — the kind of process
  discipline a client is trusting a tech lead to bring, made inspectable
  rather than asserted.
- **Fluency with AI-assisted engineering, with real discipline layered on
  top.** Large parts of this codebase — including the infrastructure work
  described above — were built working with Claude Code, directed and
  reviewed at every step: tests run and read, not assumed green; every
  cloud change verified live against the actual running system, not
  trusted from a plan; documentation reconciled against real code
  afterward rather than left to drift (this README and the architecture
  doc it links were both audited against source for exactly that during
  this project). Knowing how to get real leverage from these tools without
  losing engineering rigor is itself part of what a modernization
  engagement is paying for today.

## Architecture, in one view

```
React (Vite) ──HTTPS, resolved per-request──▶ API Gateway (Spring Cloud, :8080)
                                                      │  JWT auth · rate limiting
                                                      │  circuit breakers · CORS
                    ┌─────────────┬──────────────┬────┴────┬─────────────┐
                    ▼             ▼              ▼         ▼             ▼
              movie-service  user-service  actor-service  ai-service  media-service
               (:8081)         (:8082)       (:8083)      (:8084/9084)  (:8085)
                    │             │              │             │            │
                MongoDB      PostgreSQL     PostgreSQL   PostgreSQL    MongoDB
                                                          +pgvector    +MinIO
                                                               │
                                                          Ollama (local LLM)
                                                          + Vosk (offline STT)
```

`discovery-service` (Eureka) and `config-service` back the local profile;
Kubernetes' own Service DNS + ConfigMaps replace both natively in every
cloud overlay ([ADR-005](docs/architecture/adr/005-eureka-config-vs-kubernetes-native.md)) —
one less thing running on a free-tier node. Kafka carries a fire-and-forget
analytics event off the request path locally
([ADR-006](docs/architecture/adr/006-kafka-event-bus.md)).

**Live-verified right now, on both Azure and AWS, when a cluster is up**
(they're ephemeral by design — see [Deployment](#deployment) below): movie
browsing, actor pages, registration/login with real signed JWTs, and voice
control's speech-to-text with an offline model baked into the image. Not
curled against a bare pod — through the real gateway, real CORS, from the
real deployed frontend's origin.

## Technology stack

| Layer | Choice |
|---|---|
| Backend | Java 25, Spring Boot 4.1.0, Spring Cloud 2025.1.2, Gradle (Groovy DSL) |
| AI | Spring AI 2.0.0, Ollama (local, $0 — no OpenAI key anywhere), Vosk (offline speech-to-text) |
| Data | PostgreSQL 17 + pgvector, MongoDB 8.0, Redis 7.4, MinIO (S3-compatible) |
| Messaging | Apache Kafka (local profile — analytics event bus) |
| Frontend | React (Vite), Redux Toolkit Query, MUI |
| Infra | Terraform (Azure AKS + AWS k3s-on-EC2), Kubernetes (Kustomize, no Helm for own services) |
| CI/CD | GitHub Actions — build/test, Docker publish, Terraform plan; deploy triggered locally |
| Testing | JUnit 5 + Mockito, Testcontainers, Spring Cloud Contract, Playwright, Postman/Newman, Gatling, Vitest — 7 distinct types, see [§10](docs/architecture/ARCHITECTURE.md#10-testing-strategy) |
| Observability | Prometheus/Grafana + Alertmanager, ELK, Micrometer Tracing + Zipkin — built, local-profile by default (§12) |

## Deployment

Both clouds are **ephemeral by design** — provisioned for a demo,
`terraform destroy`d after, nothing running unattended. That's what keeps
this at genuine $0: not a specific "free" SKU (both accounts have real,
sometimes surprising constraints — see [What this demonstrates](#what-this-demonstrates)
above), but the habit of not leaving anything on.

```bash
./gradlew deployLocal      # full stack, Docker/Podman Compose — the daily dev loop
./gradlew deployAzure      # AKS, full service parity, live-verified
./gradlew deployAws        # k3s-on-EC2, full service parity, live-verified
./gradlew destroyAzure     # destroyAws — always run when done demoing
```

Full runbook, including how the frontend finds whichever backend is up:
[`docs/guides/DEPLOYMENT_GUIDE.md`](docs/guides/DEPLOYMENT_GUIDE.md).

### One frontend deploy, any live backend

The link at the top of this page is not pointed at a specific cloud. The
frontend resolves its backend per request — local → cloud (health-checked)
→ a published tunnel URL (health-checked) — and automatically uses
whichever one actually answers. If neither cloud is up when you visit (the
normal state, per the paragraph above), it falls back cleanly rather than
hanging. Full mechanism: [ADR-016](docs/architecture/adr/016-dynamic-backend-resolution.md).

## Quick start (local)

```bash
git clone https://github.com/pehlivanu/filmpire-microservices.git
cd filmpire-microservices
cp infrastructure/docker/.env.example infrastructure/docker/.env
# edit infrastructure/docker/.env — set TMDB_API_KEY (free: themoviedb.org/settings/api)

./gradlew deployLocal
# or directly: infrastructure/scripts/start-infrastructure.sh

curl http://localhost:8080/genre/movie/list   # smoke test

cd frontend/filmpire && npm install && npm run dev
```

Voice control and AI chat/recommendations need Ollama's models pulled once
(`docker exec filmpire-ollama ollama pull llama3.2` +
`nomic-embed-text`) — see the deployment guide for the full checklist.

## Project structure

```
filmpire-microservices/
├── backend/            # 8 Spring Boot services (see architecture diagram above)
├── frontend/filmpire/   # React (Vite) app — full original history preserved (ADR-013)
├── infrastructure/
│   ├── terraform/       # Azure AKS + AWS k3s, free-tier provisioning
│   ├── kubernetes/       # Kustomize: base/ + overlays/{local,azure,aws} + monitoring/
│   └── scripts/          # deployAzure/deployAws/deployLocal wrappers, tunnel mgmt
├── e2e/                 # Playwright, browser-level acceptance tests
└── docs/
    ├── architecture/     # ARCHITECTURE.md (2700+ lines, kept in sync with source) + 17 ADRs
    ├── process/          # Scrum artifacts — DoR/DoD/NFRs, product goal
    ├── api/              # Postman collection
    └── guides/           # Deployment guide, frontend-binding runbook
```

## Documentation

- [Architecture Document](docs/architecture/ARCHITECTURE.md) — the complete, current system design; every claim in it is checked against real source, not just the original plan
- [Architecture Decision Records](docs/architecture/adr/) — 17 decisions, each with what was rejected and why
- [Deployment Guide](docs/guides/DEPLOYMENT_GUIDE.md) — local, Azure, AWS, and how the frontend binds to whichever is live
- [Port Mapping](docs/architecture/PORT_MAPPING.md) · [Postman Collection](docs/api/) · [Frontend Runbook](docs/guides/RUN_WITH_FILMPIRE_APP.md)

## Testing

```bash
./gradlew test                       # all backend services
cd backend/movie-service && ../../gradlew test   # one service
cd frontend/filmpire && npm test     # Vitest — 180+ tests
```

Seven distinct test types run across this codebase — unit, integration
(Testcontainers, real databases, no H2), contract (Spring Cloud Contract),
browser E2E (Playwright), API smoke (Postman/Newman against the live
composed stack, nightly), load (Gatling), and frontend component tests
(Vitest + Testing Library) — see [§10](docs/architecture/ARCHITECTURE.md#10-testing-strategy)
for what each one actually covers and where it lives.

## About

Built solo by **Liviu Ionesi** as a demonstration of what one engineer,
working deliberately at the intersection of AI-assisted development and
real engineering discipline, can ship: a production-shaped system, not a
toy — real multi-cloud infrastructure, real cost accountability, real
process rigor, and documentation that's actually kept honest against the
code rather than written once and abandoned.

Available for **tech lead and freelance engagements** — modernization,
platform/cloud engineering, and systems built to this standard. Open
issues and the commit history are the actual work log, not a curated
highlight reel: [github.com/pehlivanu/filmpire-microservices](https://github.com/pehlivanu/filmpire-microservices).

---

**Status:** actively developed — see [open issues](https://github.com/pehlivanu/filmpire-microservices/issues) for what's next.
