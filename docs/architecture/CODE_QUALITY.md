# Code Quality — SonarQube Setup

**Issue:** #20 (SonarQube Integration and Code Quality Cleanup)
**Status:** Complete — server + Gradle plugin + IDE connected mode; 0 open issues, quality gate OK.

## What runs where

| Layer | What it does | How |
|---|---|---|
| **SonarQube server** | Full analysis, history, quality gate | `docker compose --profile sonar up -d` → http://localhost:9010 |
| **Gradle plugin** | Pushes analysis + JaCoCo coverage | `./gradlew test jacocoTestReport sonar` |
| **SonarQube for IDE** (SonarLint) | Same rules, inline in the editor | connected mode, bound to the local server |

## Running an analysis

```bash
# 1. Start SonarQube (behind the `sonar` profile — it is NOT part of the normal
#    stack, so day-to-day dev and the e2e smoke job never pay for it)
cd infrastructure/docker && docker compose --profile sonar up -d

# 2. Analyse (coverage first — sonar reads each module's JaCoCo XML)
export SONAR_HOST_URL=http://localhost:9010
export SONAR_TOKEN=<a user token from My Account → Security>
./gradlew test jacocoTestReport sonar
```

> **Port 9010, not 9000** — MinIO already binds 9000/9001.
>
> **Never reuse a data volume across SonarQube major versions.** A volume
> written by an older image makes the new web server crash-loop while cleaning
> its work dir. On an upgrade: `docker volume rm docker_sonarqube_data
> docker_sonarqube_extensions docker_sonarqube_logs docker_sonarqube_db_data`.

## Seeing results in the IDE (connected mode)

Connected mode makes the editor use the **server's** quality profile, so what
you see inline is exactly what the pipeline sees — not a bundled default ruleset.

1. Install **SonarQube for IDE** (publisher SonarSource).
   - VS Code → Marketplace.
   - **Antigravity / VSCodium / Cursor** use **Open VSX**, where it is published
     as `SonarSource.sonarlint-vscode`. CLI install:
     ```bash
     ELECTRON_RUN_AS_NODE=1 <ide-binary> <ide>/resources/app/out/cli.js \
       --install-extension SonarSource.sonarlint-vscode
     ```
2. Add the **connection** to your IDE's *user* settings (machine-specific — it
   holds a credential, so it must never be committed):
   ```json
   "sonarlint.connectedMode.connections.sonarqube": [
     { "connectionId": "filmpire-local", "serverUrl": "http://localhost:9010" }
   ]
   ```
3. Run **"SonarQube for IDE: Connect to SonarQube (Server)"** and paste a token
   (stored in the OS keychain). Reload the window.

The **project binding** is committed at [`.sonarlint/connectedMode.json`](../../.sonarlint/connectedMode.json)
(`connectionId: filmpire-local`, `projectKey: filmpire-microservices`), so every
developer binds to the same project. Workspace-level SonarLint prefs live in
`.vscode/settings.json`.

## Quality profile: "Filmpire way"

Java analysis uses a custom profile — a copy of **Sonar way** with two rules
deactivated. Exported for reproducibility at
[`infrastructure/sonarqube/filmpire-way-java-profile.xml`](../../infrastructure/sonarqube/filmpire-way-java-profile.xml)
(restore via *Quality Profiles → Restore*).

| Rule | Why deactivated |
|---|---|
| `java:S8694` | "Use `Month`/`DayOfWeek` enum instead of a numeric literal" — 77 hits, almost all date literals in test fixtures and utils. Pure churn, no defect. |
| `java:S8688` | "`.now()` should take a `ZoneId`/`Clock`" — 27 hits. Timestamp fields legitimately use `now()`; injecting a `Clock` throughout is over-engineering here. |

Both are also dropped by SonarSource's own built-in *Sonar agentic AI* profile —
independent agreement that they are noise for a codebase like this.

### Why not the leaner built-in profile?

| Profile | Rules |
|---|---|
| Sonar way | 555 |
| **Filmpire way** (in use) | **553** |
| Sonar agentic AI | 486 |

*Sonar agentic AI* is leaner but drops 69 rules that matter here: `S6813`
(field injection — **forbidden** by ARCHITECTURE.md Appendix B), the Spring
rules `S6832`/`S6833`/`S6829`/`S6830`/`S6837`, and the timezone rules `S8220`
(BLOCKER) and `S8700`. It reads as a narrow, high-precision profile for
reviewing AI-generated code, not comprehensive project QA. Rule *count* was
never the problem — two bad-fit rules were producing 104 of 143 findings.

## Analysis settings (in `build.gradle`)

- `sonar.coverage.jacoco.xmlReportPaths` — aggregates every module's JaCoCo XML.
- `sonar.exclusions` — generated/build output, MapStruct `*Impl`, descoped `frontend/`.
- `sonar.coverage.exclusions` — `*Application`, `config/**`, `dto/**`, `*Config`:
  still **analysed** for defects, just not counted against coverage, so the
  percentage reflects real logic rather than bootstrap boilerplate.
- Test-only rule relaxations (`S100`, `S3305`, `S1192`) via
  `sonar.issue.ignore.multicriteria` — test methods use descriptive underscored
  names, `@Autowired` fields are standard in Spring tests, and repeated string
  literals aid test readability.

## Accepted findings (won't-fix, with reasons)

Marked resolved on the server rather than silenced by disabling a rule, so the
reasoning is recorded against the specific occurrence:

| Rule | Where | Why |
|---|---|---|
| `java:S2068` ×2 | `ErrorCodes` | Constants *named* `…PASSWORD…` are error-code identifiers, not credentials. False positive. |
| `java:S4502` ×1 | user-service `SecurityConfig` | CSRF disabled deliberately: a stateless JWT API has no session/cookie for CSRF to protect. |
| `java:S2143` ×3 | `DateUtils`, `JwtUtil`, `JwtTokenProvider` | `java.util.Date` is mandated — these are legacy-interop converters and the jjwt API. |
| `java:S112` ×1 | user-service `SecurityConfig` | `throws Exception` is required by Spring Security's `HttpSecurity.build()`. |
| `java:S6809` ×5 | `ActorService` | All `@Transactional` use default `REQUIRED`, so a self-call joins the same transaction — behaviour is identical. Revisit if any adopt `REQUIRES_NEW`. |

## Notable fixes made under #20

- **`PageResponse<T extends Serializable>`** — the class implements
  `Serializable` and is returned from `@Cacheable` methods backed by
  JDK-serialized Redis, but `T` was unbounded. Bounding it turns a runtime
  `NotSerializableException` into a compile error at the call site (this is
  exactly the failure that previously reached production; see ADR-011).
- **`MovieService` self-invocation (S6809 ×7)** — the `*Raw` methods are
  `@Cacheable` *and* called both externally (facade) and internally (native
  API). The internal `this.…` calls bypassed the proxy, so the two API layers
  each re-fetched from TMDB instead of sharing one cached response. Now routed
  through an `ObjectProvider<MovieService>` self-reference, so both share the
  cache — which matters because TMDB rate-limits.
- **`DateUtils.hoursBetween` (the only BUG)** — computed on bare
  `LocalDateTime`, so a span crossing a DST boundary was off by the shift. Now
  resolved against `ZoneId.systemDefault()` before differencing.

## Current state

```
quality gate     OK
bugs             0
vulnerabilities  0
code smells      0
coverage         84.8%
ncloc            4732
```
