---
name: work-issue
description: Take one GitHub issue on liviuionesi/lmdb.dev through the full implement → test → review → close loop that CLAUDE.md's autonomous work contract defines, run interactively. Picks (or takes a given) issue, implements code and tests together, runs the module's tests/lint/coverage, gets an independent code review pass and a separate test-quality review pass, fixes what's flagged, resyncs the issue against real evidence, commits, pushes, and posts a session comment. Use when the user runs /work-issue, asks to "work the next issue", "pick up a task from github", "implement issue #N", or wants one backlog item taken start-to-finish in this session.
tools: Bash, Read, Edit, Write, Grep, Glob, Agent, AskUserQuestion, TodoWrite
---

# /work-issue — one issue, start to finish

Runs `CLAUDE.md`'s "work loop, per issue" interactively, for a human sitting
at the session rather than the scheduled `lmdb-autonomous-dev` routine. Same
quality bar, same Definition of Done — just without the cross-run lock/halt
dance, which exists to stop concurrent *scheduled* checkouts from racing each
other, not to gate a live session someone is watching.

Args: `/work-issue` (pick the next one per the rules below) or `/work-issue
<N>` (work issue N directly, skipping the picking step — still subject to
scope-discipline checks: refuse if N is an Epic or an unbroken-down Story,
per the same rule the picker follows).


> Outside Claude Code: invoke this by pointing the agent at
> `.agents/skills/work-issue/SKILL.md` with the issue number. Where the
> steps below say `AskUserQuestion`, ask in chat and wait. See "Repo
> skills, and how they translate between tools" in `AGENTS.md`.

## 0. Orient

- `git fetch origin develop` and confirm the working tree is on `develop`
  (or ask before branching/switching — don't silently move the user).
- Skip the `.autonomous-lock`/`.autonomous-halt` dance (interactive session,
  not the scheduled routine) — but if `git log --oneline -1` shows a `WIP:`
  commit, finish *that* issue first regardless of what was asked, same as
  the autonomous contract requires.
- `graphify query`/`graphify explain` before grepping raw files, if
  `graphify-out/graph.json` exists — this repo requires it (see root
  `CLAUDE.md`).

## 1. Pick the issue (skip if the user gave a number)

1. `gh issue list --repo liviuionesi/lmdb.dev --state open --json
   number,title,labels,milestone`.
2. `git log --oneline` — drop any issue number already referenced in a
   commit subject. If an issue already carries 2+ `WIP:` commits without
   closing, don't pick it — flag it as stuck in the final report instead
   (per CLAUDE.md's cross-session retry cap; this session isn't a fourth
   silent attempt).
3. Filter to the current open Sprint milestone (or the highest-priority
   unmilestoned backlog item if none is open).
4. **Read the parent Story's "Technical Tasks" checklist before picking
   among its child Tasks.** A Story's task list is usually written in real
   dependency order (e.g. #198 lists #202 before #217/#218 before #203,
   because #203 needs the other two's data first) — priority labels alone
   don't capture that, and Task issues themselves typically carry no
   priority label at all. Follow the Story's list order over any other
   tiebreak among its own children. This is the one correction this skill
   makes over a literal reading of CLAUDE.md's picking algorithm — see
   "Suggested process improvements" below for why it's worth upstreaming.
5. Otherwise: highest-priority Story-level label (P0 > P1 > P2 > P3) in the
   current Sprint, not yet referenced in history.
6. Epic → work its next unfinished child Story instead. Story with
   un-broken-down Tasks → break it down first (or work it directly if
   small enough not to need that) — ask the user before creating new Task
   issues, since that changes the backlog structure.

Report which issue was picked and why before starting implementation.

## 2. Understand before writing code

- `gh issue view <N> --repo liviuionesi/lmdb.dev --json body,labels,milestone`
  — read every acceptance criterion, not just the title.
- Read the parent Story/Epic and any ADR it references. An ADR that lists
  "Prerequisite gaps" or a contract shape (like ADR-020's structured filter)
  is binding — implement against it, don't reinvent the shape.
- Find the closest existing sibling feature in the same service (same kind
  of endpoint/client/service class) and match its patterns — DTO shape,
  exception handling, logging, RestClient/ChatClient wiring — rather than
  inventing a new one. Check whether an existing test file already covers
  this kind of flow (LLM-backed services in this repo are tested through
  one shared integration-test class per service, not per-class unit tests
  — follow that precedent unless the user says otherwise).

## 3. Implement code and tests together

Not sequentially — write the test for a behavior alongside the code that
satisfies it, per Definition of Done's "not after, as an afterthought."
Apply the mandatory documentation standard from `CLAUDE.md` as you go
(Javadoc on every class/interface/method including private ones and test
methods; numbered inline comments only where the code does something
non-obvious) — retrofitting docs at the end is slower and easier to skimp
on than writing them with the code.

## 4. Run the module's checks, fix on red

In order, for every module actually touched:
1. `./gradlew :backend:<module>:compileJava :backend:<module>:compileTestJava`
2. `./gradlew :backend:<module>:test` — fix immediately on red, don't
   proceed with failing tests.
3. `./gradlew :backend:<module>:spotlessCheck` (`spotlessApply` to
   auto-fix, then re-check).
4. `./gradlew :backend:<module>:jacocoTestCoverageVerification`.
5. SonarQube (`./gradlew test jacocoTestReport sonar`) **only if** a local
   Sonar server is actually reachable (`docker ps | grep sonar`, or
   `SONAR_HOST_URL` is set) — don't silently skip it without saying so, but
   don't block the loop on infra that isn't running either. Say explicitly
   in the final report whether this step ran or was skipped and why.
6. OWASP `dependencyCheckAggregate` **only if this issue added or changed a
   dependency** — the full NVD-backed scan takes minutes and flags nothing
   new for a zero-dependency change. State this explicitly rather than
   running it by rote or skipping it silently.

## 5. Two independent review passes, in parallel

Run both independently (they don't depend on each other) — background
`Agent` calls in Claude Code, separate agent runs in Antigravity, or one
after the other in this session —
then continue other prep work until both report back — don't block
synchronously if there's other useful prep to do (issue-hygiene reading,
drafting the resync).

1. **Code review** — the matching `developer-kit-*` review agent for the
   stack (e.g. `developer-kit-java:spring-boot-code-review-expert` for a
   Spring Boot module), or a general code-reviewer subagent if none fits.
   Give it: the issue's acceptance criteria, the relevant ADR, the actual
   diff (`git diff`/`git status` — tell it to read the real diff, not
   assume), and the sibling files it should match conventions against.
   Ask for a blocker/nice-to-have split and an explicit VERDICT line.
2. **Test review** — a distinct subagent, told explicitly this is *not*
   "do the tests pass" but "would they catch it if the implementation were
   subtly wrong": reason about specific mutations per test (wrong branch
   taken, wrong field mapped, exception swallowed at the wrong type) and
   say concretely which mutation each test does or doesn't catch. Ask
   whether the AC's required scenarios are all actually exercised, and for
   an explicit VERDICT line.

Cap: 3 fix-and-re-review rounds in this session (matches the autonomous
contract's cap). If still unresolved after 3, stop and hand off as `WIP:`
per step 8 below rather than continuing to iterate unboundedly.

## 6. Issue hygiene — resync against real evidence, mandatory

Never close on a technicality, never leave a done issue open, never verify
a claim through a shortcut that skips the layer the issue is actually
about (e.g. don't confirm a gateway-routed fix by calling the downstream
service directly).

1. Re-read the issue. Check every acceptance-criteria box against real
   evidence you just produced (a passing test, a real run) — not against
   the issue's existing prose.
2. Rewrite the body to match `.github/ISSUE_TEMPLATE/{task,bug}.md`'s
   shape. If part of the original scope turned out infeasible, rescope the
   criterion to match reality in one line — don't leave it unchecked
   forever and don't pile on caveats instead ([[resync-tasks]]'s
   before/after example is the calibration for how tight this should be).
3. **Cascading closure, mandatory:** closing a Task → check its parent
   Story for other open Tasks. If any remain, the next unit of work in
   *this same session* (if budget allows) or the next `/work-issue` call is
   one of those remaining Tasks, not an unrelated pick — the point is
   finishing the Story, not just clearing one Task. If none remain, mark
   the Story's DoD box, close it, check it off in its parent Epic, and
   merge `develop` → `main` (see step 8). Closing a Story with no Epic
   Stories left open → close the Epic too.
4. Post a short comment on the issue: what was done, what was verified,
   what's left (this is the system's Daily Scrum — see CLAUDE.md's
   Observability section). Keep it as tight as the resync-tasks
   before/after calibration, not a transcript retelling.

## 7. Commit and push

- Subject references the issue: `feat: <what> (#N)`. The commit-msg hook
  rejects a subject with no real issue number — don't fight it, fix the
  subject.
- Short subject (~72 chars), at most one line of body context. No
  `Co-Authored-By` or AI-attribution trailer — the user is sole author.
- The pre-commit hook blocks a non-compiling commit; don't rely on your own
  judgment instead of just letting it run.
- Push to `develop`. If push is rejected because someone else pushed first
  (check `ListAgents` for other active sessions on this repo before
  assuming it's a fluke), `git fetch` + `git pull --rebase` and retry once
  — don't force-push over someone else's work.

## 8. Story closure → main, if this issue finished one

Only when a **Story** (not a bare Task) just closed and every DoD gate is
genuinely true: merge `develop` → `main` immediately (`git checkout main &&
git merge develop && git push origin main`, or open a PR as an optional
readable changelog — not required with one collaborator). Never merge an
unverified Story.

## 9. If blocked before done

Stop, commit what's genuinely working as `WIP:` with a precise note of
what's left and why, leave `develop` buildable, and say so plainly in the
final report rather than declaring victory on a partial result.

## Final report to the user

- Issue worked, and why it was picked (or that the user specified it).
- What was implemented, where (file list).
- Test/lint/coverage results, and whether Sonar/OWASP ran or were skipped
  and why.
- Both review verdicts, and what was fixed in response.
- Issue hygiene: what got resynced, whether it closed, any cascading
  Story/Epic closure, whether `main` got the merge.
- Commit(s) pushed.
- Anything flagged as stuck, blocked, or left `WIP:`.

## Suggested process improvements (surfaced from running this)

Don't apply these silently — they change the standing contract in
`CLAUDE.md`/`.claude/CLAUDE.md`, which is the user's process document, not
this skill's to rewrite unilaterally. Name them in the final report and let
the user decide.

1. **Task-picking doesn't yet say to follow a Story's own dependency
   order.** CLAUDE.md's "Picking the next task" step sorts by priority
   label across all open issues in the Sprint; Task issues carry no
   priority label of their own, and nothing currently tells the picker
   that a Story's "Technical Tasks" checklist is itself an ordered
   dependency chain (ADR-020 / #198 is a real example: #203 silently
   depends on #217 and #218 landing first, encoded only in the Story body's
   list order and the ADR's prose, not in any field the picking algorithm
   reads). Step 4 of this skill patches around that by reading the parent
   Story first; worth folding into CLAUDE.md itself so the scheduled
   autonomous routine gets the same fix, not just interactive runs.
2. **`CLAUDE.md` is duplicated byte-for-byte at the repo root and under
   `.claude/`.** Confirmed identical today; two copies of the same
   governance doc will drift the moment one gets edited and the other
   doesn't. Worth collapsing to one real file plus a one-line pointer from
   the other location, rather than trusting future edits to stay in sync
   by hand.
3. **The Sonar step is silently unrunnable in most interactive sessions**
   (`./gradlew sonar` needs a local server most checkouts don't have
   running) but CLAUDE.md phrases it as an unconditional "run static
   analysis... fix anything these flag." Worth an explicit "if reachable,
   else state that it was skipped" carve-out in the contract itself, so a
   session doesn't either silently skip it or block on infra nobody asked
   it to stand up.
4. **`AiServiceIntegrationTest.java` (and likely its siblings in other
   services) is past 1000 lines** as one shared integration-test class per
   service. That's the established, correct pattern for this repo's
   LLM-backed services (no bare unit tests exist for ChatClient-based
   service classes at all) — but as more endpoints land in the same
   service, one growing file gets harder to review per-PR. Worth
   considering a split by feature area (e.g. a shared base class for the
   Testcontainers/WireMock/model-mock setup, one test class per endpoint
   group) before it gets much bigger, not a change to make mid-issue.
