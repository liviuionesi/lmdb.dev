# Filmpire Microservices — Autonomous Work Contract

This file governs unattended/scheduled Claude Code runs on this repo (see the
`filmpire-autonomous-dev` scheduled routine). A human-driven session can
ignore this file, but should still respect the conventions below.

## Before starting any work

1. If `.claude/.autonomous-halt` exists, stop immediately — do nothing else.
   The user paused the system on purpose.
2. If `.claude/.autonomous-lock` exists and is younger than 40 minutes,
   another run is still in flight on this checkout — exit immediately
   rather than racing it.
3. Otherwise, create `.claude/.autonomous-lock` (plain text, a timestamp is
   enough) before doing anything else, and delete it when this run ends —
   including on a `WIP:` handoff. Never leave it behind.

## Backlog structure (Scrum — see `docs/process/METHODOLOGY.md` for why
Scrum specifically, and how this would change if it didn't)

- Hierarchy: **Epic → Story → Task**. A Story is the real requirements
  unit (`As a <role>, I want <goal>, so that <benefit>`, Given/When/Then
  acceptance criteria, Story Points, a Sprint milestone). A Task is a
  technical subtask under a Story, hour-estimated. Bugs are their own
  type, outside this hierarchy.
- Templates: `.github/ISSUE_TEMPLATE/{epic,user-story,task,bug}.md`. Use
  them for shape, not GitHub's web form (`gh issue create`/`gh issue edit`
  don't apply them automatically — match the shape by hand).
- Standing definitions, referenced not restated per issue:
  `docs/process/DEFINITION_OF_READY.md`, `docs/process/
  DEFINITION_OF_DONE.md`, `docs/process/NON_FUNCTIONAL_REQUIREMENTS.md`,
  `docs/process/PRODUCT_GOAL.md`.
- Sprints are real GitHub Milestones (roughly one week each). A Story
  isn't picked up unless it's in the current open Sprint milestone (or
  pull it in first, if it's high-priority Product Backlog work).

## Source of truth for remaining work

- GitHub Issues on `pehlivanu/filmpire-microservices` are the backlog.
  `.github/issues/PROJECT_ROADMAP.md` gives the phase-level narrative; the
  issues themselves (`gh issue list`) are the authoritative status.
- Architecture reference: `docs/architecture/ARCHITECTURE.md` plus
  `docs/architecture/adr/` (one file per decision — add a new ADR for any
  new architectural decision, don't bury it in an issue comment).
- Existing completed services (`movie-service`, `discovery-service`,
  `config-service`, `api-gateway`) are the pattern to follow for structure,
  package layout, test style (JUnit + WireMock for external calls), and
  Spring Boot/Gradle conventions. Match them rather than inventing new
  patterns.

## Picking the next task

1. Run `gh issue list --repo pehlivanu/filmpire-microservices --state open`.
2. Run `git log --oneline` on `develop` and check which issue numbers are
   already referenced in recent commit subjects. If the most recent commit
   is prefixed `WIP:`, finish that issue before starting a new one.
3. If an issue has 2 or more `WIP:` commits already in its history without
   closing, don't auto-retry it again — leave it alone and post a comment
   flagging it as stuck instead. A third silent attempt is budget wasted
   on a task that needs a human look, not more retries.
4. Otherwise pick the highest-priority open issue (`P0-critical` >
   `P1-high` > ...) in the current Sprint milestone, not yet referenced in
   the history.
5. If it's an Epic, work its next unfinished child Story instead of the
   epic itself. If it's a Story with un-broken-down Technical Tasks, break
   it into Task issues first (or work it directly if it's small enough not
   to need that).

## The work loop, per issue

1. Implement the code and its tests together.
2. Run the tests for any module touched (`./gradlew :backend:<module>:test`
   or the relevant module path) — fix immediately if red, don't proceed on
   red tests.
3. Run static analysis (SonarQube), linting (Checkstyle/Spotless or
   ESLint/Prettier), coverage, and dependency/security scanning where
   configured for the module. Fix anything these flag before moving on —
   cheaper to catch here than after a review round.
4. Code review: a second, independent pass (an `Agent` subagent, or the
   appropriate devkit review skill for the stack). For large diffs, or
   anything touching security/auth/shared-library code, or if step 3
   already flagged something, run a second independent review pass too.
5. Test review — a distinct question from "do the tests pass": would they
   actually catch it if the implementation were subtly wrong? A second
   subagent checks this specifically.
6. If anything in steps 2-5 is wrong, fix it and re-run the relevant
   step(s) — up to 3 rounds in this session. If still not resolved after 3
   rounds, stop, commit what's genuinely working as `WIP:` with a precise
   note of what's blocking, and let the next scheduled run pick it up (see
   the cross-session retry cap above).
7. Resync the issue to match real implementation state and close it if
   genuinely done — see "Issue hygiene" below; this already fuses verify
   and close, don't treat them as two separate steps.
8. Commit (see "Commit conventions") and push to `develop`.
9. Move to the next issue. Repeat until the Sprint's issues are done.

## Merging to `main` — Sprint Review + Retrospective

- `develop` is where all task work happens and gets pushed.
- `main` only ever receives fully-verified work: merge `develop` → `main`
  at the end of a Sprint, once every Story closed that Sprint is genuinely
  done (Definition of Done met for each). This is the Sprint Review +
  Sprint Retrospective events (see `docs/process/SCRUM_EVENTS.md`), not
  optional ceremony:
  1. **Sprint Review**: actually run the software, confirm the Increment
     genuinely works. Post a Sprint Report (points committed vs. closed,
     what slipped and why) on the Sprint milestone.
  2. **Sprint Retrospective**: one honest note — what went well, what
     didn't, one concrete process change for next Sprint. If it implies a
     real change, apply it to this file or `docs/process/` directly, not
     just write it down.
  3. Only then merge `develop` → `main`.
- Opening a Pull Request for this merge is optional, not required — one
  collaborator means there's no second approver needed, and GitHub allows
  a repo owner to merge their own PR unless branch protection says
  otherwise. A PR is still fine to open purely as a readable changelog.
- Never merge a Sprint with unfinished/un-verified Stories still in it —
  leave them in the next Sprint instead of forcing the merge.
- Next Sprint's **Sprint Planning** (pull Stories into the new Milestone,
  set its Sprint Goal) happens right after, before new Task work starts.

## Issue hygiene (mandatory)

Issues drift from real implementation state unless something forces a sync
back to reality — this section is that force.

- After finishing implementation work on an issue, before moving on:
  re-read the issue, verify every acceptance-criteria box against real
  evidence (code, a test run, a live check against the running stack) — not
  the issue's existing text — and rewrite the body to match the templates
  in `.github/ISSUE_TEMPLATE/`.
- Never close an issue with an unchecked acceptance-criteria box. Never
  verify "done" through a shortcut that skips the exact layer the issue is
  about (e.g. don't confirm a gateway-routing fix by calling the downstream
  service directly instead of through the gateway).
- Ground every rewrite in current code/commit/test state, not a
  reinterpretation of old text. If part of the original plan turned out
  infeasible, rescope the acceptance criteria to match reality in one line
  — don't leave it unchecked forever, and don't pile on caveats instead of
  rescoping.
- Run `/resync-tasks` (repo-local skill, `.claude/skills/resync-tasks/`)
  periodically as a backstop sweep across every open issue, not just the
  one just touched — catches drift a prior session missed.

## Code documentation standard (mandatory)

- Every class and interface gets a Javadoc block: what it is, its role in the
  architecture, and collaborators worth knowing about.
- Every method (including private ones) gets a Javadoc: purpose, params,
  return, thrown exceptions. Trivial getters/setters generated by Lombok are
  exempt.
- Inside method bodies, add inline comments when the code does something
  unusual, non-obvious, or proceeds in distinct steps — number the steps
  (e.g. `// 1. Check Redis cache`). Do NOT comment the self-evident.
- Comments explain WHY and the contract, not restate the code.
- TESTS ARE NOT EXEMPT: every test class gets a Javadoc (what's under test,
  which tools — WireMock, Testcontainers, mocks), every @Test method gets a
  Javadoc describing the scenario and why the asserted behavior is the
  correct one (complementing @DisplayName, never restating it), and test
  bodies use Given/When/Then or numbered-step comments when they have more
  than one logical step. Helper/setup methods too. The shared-library test
  suite is the reference style.

## Commit conventions

- Commit subject references the issue: `feat: Implement User Service (#17)`.
  A commit-msg hook (`.githooks/commit-msg`, wired via `git config
  core.hooksPath .githooks`) rejects any commit whose subject doesn't
  reference a real issue number.
- Keep the subject short (~72 characters) and the body short — one line of
  extra context at most, never a narrative retelling of the issue. If it
  needs more explanation than that, that explanation belongs in the issue,
  not the commit.
- Do not add a `Co-Authored-By` trailer or any other AI-attribution line —
  the user is the sole author of their own work.
- A pre-commit hook (`.githooks/pre-commit`) blocks commits that don't
  compile — don't rely on your own judgment alone for this.
- Never commit secrets or credentials, even ones covered by `.gitignore` —
  `git add -f` bypasses that, so this is a separate, explicit rule.
- If you must stop mid-issue (context or budget runs out before the issue is
  done), leave `develop` in a buildable, fully-committed state — no
  uncommitted work, no broken build. Prefix the commit subject with `WIP:`
  and include a short note in the commit body of exactly what's left to do,
  so the next scheduled run (or the user) can pick it up cold.

## Observability (also this system's Daily Scrum)

- Post a short comment on the issue after each work session touching it —
  what was done, what was verified, what's left. Cheap audit trail,
  no transcript archaeology needed later — and functionally this run's
  Daily Scrum (see `docs/process/SCRUM_EVENTS.md`), inspectable anytime
  rather than on a fixed daily slot.
- A real spending cap on the account is the user's responsibility to set —
  retry limits in this file reduce runaway cost, but the actual backstop
  is at the billing level, not in these instructions.

## Scope discipline

- One issue (or one clearly-bounded chunk of a large issue) per run. Don't
  jump between unrelated issues in the same run.
- Don't touch issues you didn't pick.
- Don't edit CI/CD workflows, branch protection, or repo settings.
- Don't add dependencies or upgrade framework versions unless the issue
  specifically calls for it.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

Rules:
- For codebase questions, first run `graphify query "<question>"` when graphify-out/graph.json exists. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
