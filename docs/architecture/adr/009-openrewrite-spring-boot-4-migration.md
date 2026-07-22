# ADR-009: OpenRewrite-Driven Spring Boot 3.5 → 4.0 Migration

**Status:** Accepted
**Date:** 2026-07-22
**Deciders:** Project owner

## Context

Spring Boot 3.5.x reached the end of its free (OSS) support window on
2026-06-30; further 3.5.x maintenance now requires a commercial Tanzu Spring
Runtime subscription. Spring Boot 4.0 (GA 2025-11-20, on Spring Framework 7 /
Java 25 baseline) is the supported path forward. The upgrade is not a version
bump in isolation — it drags in Spring Framework 7, Spring Security 7, Spring
Cloud 2025.1, Jackson 3, and Jakarta EE 11, each with breaking API and
configuration changes across all nine modules.

Doing this by hand across the whole repo would be slow and error-prone. We want
a **repeatable, reviewable** upgrade mechanism we can also use for the next
version bump, rather than a one-off manual slog.

## Decision

Adopt **OpenRewrite** (the `org.openrewrite.rewrite` Gradle plugin plus the
`rewrite-spring` recipe module) as the standing framework-migration tool, and
use its `org.openrewrite.java.spring.boot4.UpgradeSpringBoot_4_0` recipe to
drive the 3.5 → 4.0 migration.

- Plugin and recipe-BOM versions are centralized in `gradle.properties`
  (`openRewriteVersion`, `rewriteRecipeBomVersion`); wiring lives in the root
  `build.gradle`. Recipe artifacts resolve from the settings-level repositories
  (`settings.gradle` enforces `FAIL_ON_PROJECT_REPOS`).
- Workflow: `./gradlew rewriteDryRun` to preview a patch, `./gradlew rewriteRun`
  to apply, then **human review** of the diff before anything is committed. The
  recipe is a first pass, not the final word (see Consequences).
- The recipe upgraded, in one pass: Spring Boot → 4.0.7, Spring Cloud →
  2025.1.2, springdoc → 3.0.3, plus the Framework 7 / Security 7 / Jackson 3 /
  Jakarta EE 11 source and configuration rewrites.

### What the recipe handled correctly

- **Jackson 2 → 3**: `com.fasterxml.jackson.*` → `tools.jackson.*`, checked
  `JsonProcessingException` → unchecked `JacksonException`, `new ObjectMapper()`
  → `new JsonMapper()`, and `JsonNode.asText()` → `asString()`.
- **Modular starters**: `spring-boot-starter-web` → `-webmvc`, `flyway-core` →
  `spring-boot-starter-flyway`, `spring-security-test` →
  `spring-boot-starter-security-test`; sliced test artifacts split out
  (`spring-boot-starter-webmvc-test`, `spring-boot-resttestclient`).
- **Nullability**: `org.springframework.lang.NonNull` → JSpecify
  `org.jspecify.annotations.NonNull`.
- **Config properties**: `spring.data.mongodb.*` → `spring.mongodb.*`,
  `spring.cloud.gateway.*` → `spring.cloud.gateway.server.webflux.*`,
  `management.health.mongo.*` → `management.health.mongodb.*`.
- **Testcontainers 1.x → 2.x** module repackaging
  (`org.testcontainers.containers.*` → per-database modules).

### What required manual correction after the recipe

The recipe is a strong ~80% starting point; the following were fixed by hand and
verified by the test suite:

1. **Corrupted Java toolchain** — the recipe injected broken "downgrade to Java
   8/11/17" placeholder comments into the root `build.gradle` toolchain block;
   reverted to keep Java 25.
2. **Hard-pinned versions fighting the BOM** — the recipe pinned explicit `4.0.7`
   / `2.0.5` versions on BOM-managed artifacts; reverted to BOM-managed
   (unversioned) so a single BOM bump governs future upgrades.
3. **Mangled YAML** — property renames were correct but rendered as dotted keys
   with misplaced comments and broken indentation; re-nested by hand.
4. **`@Autowired` stripped from JUnit test constructors** — a recipe overreach
   (that rule is for single-constructor beans, not `@TestConstructor` classes);
   restored where the parameter type is not a well-known one.
5. **Boot-4 package moves the recipe missed** —
   `ErrorWebExceptionHandler` (→ `boot.webflux.error`), `@DataMongoTest`
   (→ `boot.data.mongodb.test.autoconfigure`), and Spring 7's `HttpHeaders`
   dropping `MultiValueMap` (`containsKey` → `containsHeader`).
6. **New module deps** — `spring-boot-webtestclient` (gateway),
   `spring-boot-data-mongodb-test` (movie), and `spring-boot-restclient` for the
   server apps whose `TestRestTemplate` needs `RestTemplateBuilder`.
7. **`@EnableCaching` in web slices** — Boot 4's `@WebMvcTest`/`@DataMongoTest`
   slices no longer import cache auto-configuration, so a small `TestCacheConfig`
   supplies an in-memory `CacheManager`.

## Options Considered

**OpenRewrite `UpgradeSpringBoot_4_0` (chosen)** — automates the bulk of a
multi-project, multi-framework migration; the same tooling serves future bumps.

**Fully manual migration** — full control but slow and inconsistent across nine
modules; rejected as the primary mechanism (we still hand-review the diff).

**Stay on Spring Boot 3.5 with commercial support** — rejected: a paid Tanzu
subscription violates the strict $0 budget (ADR-004), and 4.0 is the free
supported line.

**Renovate / Dependabot** — complementary, not a substitute: they bump version
numbers and open PRs, but do not perform the source-level API rewrites Boot 4
requires. Also a poor fit for this repo's no-PR, single-collaborator workflow.

## Consequences

- Easier: on the supported Boot 4 / Framework 7 / Java 25 line; the next
  framework upgrade is a recipe swap plus a review pass, not a manual rewrite.
- Constraint noted: **Testcontainers moved to 2.0.5**. Postgres and Redis
  containers pass cleanly under podman; MongoDB containers showed transient
  `broken pipe` flakiness under concurrent startup that cleared once context-load
  churn was removed. Watch this if mongo-container test flakiness reappears.
- Cost: OpenRewrite output must always be reviewed — several defects above would
  have broken the build or silently changed behavior if applied blind. The
  green test suite (334 tests) is the acceptance gate for every recipe run.
- Revisit: Spring AI remains commented out in `ai-service`; when it is enabled,
  target Spring AI 2.0.x (the Boot-4-compatible line), not the old
  `1.0.0-SNAPSHOT` pin.
