# Non-Functional Requirements (standing checklist)

Applied where relevant, not restated per issue — a Story/Task only needs
to justify skipping one of these, not repeat all of them every time.

- **Security**: no secrets in code/commits/logs; inputs validated at
  service boundaries; new endpoints follow the existing auth pattern
  (gateway JWT + `X-User-Roles`).
- **Performance**: no N+1 queries introduced; cache/read-through patterns
  followed where this repo already establishes them (see ADR-010, ADR-011).
- **Observability**: new services/endpoints get actuator health +
  Prometheus metrics + structured JSON logging, matching every existing
  service (ADR referenced in `docs/architecture/adr/`).
- **Documentation**: Javadoc/TSDoc per `CLAUDE.md`'s standard; a new
  architectural decision gets its own ADR, not a comment.
- **Cost**: stays within the $0-budget framing (ADR-004) unless the issue
  explicitly says otherwise.
