# ADR-022: Config Server Access Control

**Status:** Accepted
**Date:** 2026-09-19
**Deciders:** Project owner

## Context

config-service's config endpoints (`/{application}/{profile}`) have always
answered any request with no authentication. That is the right default for
local development (native profile, loopback-only), but the README's
Production Considerations list has carried "Configure Security: Add
authentication for config endpoints" as an open item since the service was
first built (#244). Separately, an unrecognised `{application}` — a typo, or
a service that was never onboarded — silently returns HTTP 200 with an
empty-but-valid-looking configuration document, which looks identical to a
real answer.

A prior attempt at #244 was closed on the strength of classes and tests
(`ConfigServerSecurityConfig`, `KnownApplicationEnvironmentRepository`,
three security test classes) that were never actually written — the #251
backlog audit caught the gap and reopened both #244 and its parent Story
#239. This ADR records the actual implementation.

## Decision

Add `spring-boot-starter-security` to config-service and gate every endpoint
except `/actuator/**` behind HTTP Basic auth, controlled by one property:

- `config.security.enabled` (env `CONFIG_SECURITY_ENABLED`), default
  `false`. Two `@ConditionalOnProperty`-guarded `SecurityFilterChain` beans
  in `ConfigServerSecurityConfig` — one permissive, one enforcing — so
  exactly one is ever active.
- `config.security.username` / `config.security.password` (env
  `CONFIG_SECURITY_USERNAME` / `CONFIG_SECURITY_PASSWORD`) back a single
  in-memory user, read directly via `@Value` inside the conditional bean so
  a missing password only fails startup when access control is actually
  turned on.

HTTP Basic over JWT: config-service is fetched by other services, not by an
end user with a session, and the repo has no existing service-to-service JWT
issuance — Basic auth is Spring Cloud Config's standard pattern for exactly
this case and needs nothing beyond a single shared credential.

Separately, `UnknownApplicationFilter` runs at `Ordered.HIGHEST_PRECEDENCE`
(ahead of the security chain) and 404s any `/{application}/{profile}`-shaped
request whose first segment isn't in `config.server.known-applications`.
This applies regardless of the access-control setting above — a typo'd
application name should 404 whether or not auth is even in the picture.

The same filter also checks `/actuator/*` against
`management.endpoints.web.exposure.include` rather than exempting the whole
`/actuator/**` shape. An actuator sub-path Boot doesn't expose (`env`,
`beans`, ...) has no actuator mapping, so it would otherwise fall through to
Spring Cloud Config's own `/{application}/{profile}` controller and be
served as `application=actuator` — an unauthenticated route to the common
configuration documents that bypasses `config.security.enabled` entirely.
An independent review caught this during #244; reading the exposure list
from the same property Boot's own actuator autoconfiguration uses keeps the
two from drifting apart.

## Options Considered

**JWT, reusing user-service/api-gateway's existing filter** — rejected: pulls
a whole JWT stack (issuer, secret, filter) into a service whose only clients
are other backend services fetching config at startup, for no benefit over a
single shared Basic credential.

**Reject unknown applications inside a custom `EnvironmentRepository`** —
rejected: would need to reach into Spring Cloud Config's repository
resolution internals. A filter ahead of everything is simpler, independently
testable, and doesn't depend on Spring Cloud Config's internal API staying
stable across upgrades.

## Consequences

- Easier: local development and the current Docker/Kubernetes profiles are
  unaffected — `CONFIG_SECURITY_ENABLED` is unset, so both keep behaving
  exactly as before this change.
- Harder: turning this on for a real deployment needs one more secret
  (`CONFIG_SECURITY_PASSWORD`) provisioned, and any client that fetches
  config over HTTP directly (rather than through the Spring Cloud Config
  client library, which already supports `spring.cloud.config.username` /
  `password`) would need updating too.
- Revisit: if config-service ever serves more than the current six backend
  clients, or gains per-client credentials, this single shared user stops
  being enough.
