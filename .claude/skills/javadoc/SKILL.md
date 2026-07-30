---
name: javadoc
description: Audit and fix Javadoc/comments across this repo's Java classes (main and test) so they document what the code does, not the architectural decision or backstory behind it. Use when the user runs /javadoc, asks to clean up comments/Javadoc, or asks to check comment verbosity across the backend.
tools: Read, Glob, Grep, Bash, Edit, Agent, TodoWrite
---

# /javadoc — comment-quality sweep

Enforces one standard across every Java class in this repo (main and test):
**Javadoc and comments document what the code does — signature, contract,
params, return, and the one non-obvious mechanism if there is one. They do
NOT narrate the decision behind the code: no issue numbers, no ADR
references, no "found while verifying #34" bug-discovery stories, no
architecture-history essays.** That content belongs in commit messages, PR
descriptions, or the ADR itself — not inline in the code. See
[[feedback_comment_verbosity]] in memory for the incident that motivated
this (2026-07-30).

CLAUDE.md's documentation standard for this repo (mandatory Javadoc on
every class/interface/method including private ones and tests, inline
comments for non-obvious steps) still applies — this skill does not remove
required documentation, it tightens what's there. Test methods keep their
one-line scenario/rationale per CLAUDE.md's test standard, but that
rationale must stay about the test's own correctness logic, not a project
history lesson.

## A concrete before/after (from the incident that created this skill)

**Before (too verbose — narrates the decision, not the code):**
```java
/**
 * Backfills detail-only fields on a movie that was only ever upserted
 * from a list endpoint, so the caller-visible invariant of {@link
 * #getOrFetchMovieEntity} — "the returned movie has detail data" — always
 * holds, not just for movies fetched directly by ID.
 *
 * <p>Found while verifying #34 against a live gateway+frontend run:
 * {@code upsertFromListItem} deliberately leaves detail-only fields
 * (runtime, budget, spoken languages, etc.) unset (see its Javadoc), and
 * the ARCHITECTURE.md-documented "progressive enrichment" promise — hit
 * the detail endpoint once and it fills in the rest — was only actually
 * implemented for the {@code append_to_response} sub-resources (videos,
 * credits) in {@link #getMovieForFacade}, not for these base fields. ...
 */
```

**After (states the contract and the one mechanism that matters):**
```java
/**
 * Ensures a persisted movie has its detail-only fields populated,
 * fetching and merging them from TMDB if not. {@code runtime} is the
 * completeness signal — it is set only by a detail fetch, never by a
 * list upsert.
 *
 * @param movie a persisted movie, possibly list-item-only
 * @return {@code movie} unchanged if already detail-complete, otherwise
 *         the same document with detail fields merged in and re-saved
 */
```

Use this pair as the calibration reference when judging every other
comment in the sweep.

## Scope

- Default (no argument): every `*.java` file under `backend/**/src/{main,test}`
  in this repo, excluding `build/` output.
- With an argument (a module name like `movie-service`, or a path): scope
  to just that module/path instead of the whole backend.

## Process

1. **Discover.** `find backend -name "*.java" -not -path "*/build/*"`,
   grouped by module (the path segment right after `backend/`). Report the
   file count per module before starting — this sets expectations for how
   long the sweep takes.

2. **Dispatch one Agent per module, in parallel**, each scoped ONLY to that
   module's files (main + test). Give each agent:
   - The standard and the before/after example above, verbatim.
   - The exact list of files in its module.
   - Explicit instructions: read every class/interface/method Javadoc and
     every inline comment; rewrite any that narrate decisions/history
     instead of describing the code; do not delete Javadoc that's
     required by CLAUDE.md, only tighten it; do not touch code logic,
     only comments; do not touch comments that are already appropriately
     concise (this is a targeted fix, not a rewrite-everything pass);
     report which files it changed and a one-line reason per file.
   - Tool access: Read, Edit, Grep (no Bash/Write needed — comment-only
     edits to existing files).

   Nine real modules as of this skill's creation: `api-gateway`,
   `discovery-service`, `config-service`, `movie-service`, `user-service`,
   `actor-service`, `ai-service`, `media-service`, `shared-library` — but
   always re-discover via step 1 rather than trusting this list, since
   modules get added.

3. **Verify.** After all agents finish, compile every touched module
   (`./gradlew :backend:<module>:compileJava :backend:<module>:compileTestJava`
   for each one touched, or `./gradlew compileJava compileTestJava` if most
   modules were touched). Comment-only changes should never break a build;
   a compile failure here means an agent accidentally touched code, not
   just comments — inspect the diff and fix or revert that file.

4. **Report.** A per-module summary: files changed, files already
   compliant (no change needed), and the build verification result. Do not
   run tests as part of this skill (comment changes don't affect runtime
   behavior) — compiling is sufficient verification.

## Non-goals

- Not a general code-quality or simplification pass — leave logic, naming,
  and structure untouched. If something looks like a real bug while
  reviewing comments, report it in the summary rather than fixing it
  inline (that's a different task).
- Not a one-time cleanup — this is meant to be re-run periodically or after
  a batch of feature work, so keep the module-by-module approach reusable
  rather than hand-tuning it for whatever's stale right now.
