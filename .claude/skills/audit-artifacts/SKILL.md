---
name: audit-artifacts
description: Audit and rewrite one Story and its child Tasks so they match the issue templates, read in plain English, tell the truth about the code, and have every acceptance criterion backed by a test that actually runs. Use when the user runs /audit-artifacts, or asks to review/rewrite GitHub issues, check that acceptance criteria are real, or bring the backlog up to Scrum standard.
tools: Bash, Read, Write, Edit, Grep, Glob, Agent, TodoWrite
---

# /audit-artifacts — one Story, audited to the end

Takes **one Story and every Task under it** through four gates. One Story
per run. Do not spread across unrelated Stories — a half-audited board is
worse than an un-audited one, because it looks trustworthy.

Two modes:

- `/audit-artifacts 85` — **Story mode.** Story #85 and its child Tasks.
- `/audit-artifacts epic 22` — **Epic mode.** Epic #22's own body only.
  See "Epic mode" below; it is a separate run, never folded into a Story
  run.

If no argument is given, ask which Story, or pick the lowest-numbered
Story not yet audited.

Repo: `liviuionesi/lmdb.dev`. Templates: `.github/ISSUE_TEMPLATE/`.
Voice profile: `VOICE.md`, next to this file.

## Why this exists

`/resync-tasks` makes issues short and honest. It stops there. This skill
goes one step further and asks the question that actually matters:

> If someone deleted this issue and kept only the code, would the code
> still prove the criterion is true?

If the answer is no, the tick mark is a promise, not a fact. Dates like
"verified 2025-11-14" inside an acceptance criterion are the clearest
symptom — the box stays ticked forever while the thing it claims quietly
breaks. That is how #109 and #250 happened on green-looking issues.

## The four gates

Run them in this order. Each gate assumes the one before it passed.

### Gate 1 — Shape

The body matches its template in `.github/ISSUE_TEMPLATE/` section for
section. Concretely:

- Every section the template has is present. No extra sections invented.
- Story: `As a / I want / So that`, `Parent Epic`, Given/When/Then criteria,
  DoR box, Story Points, Technical Tasks, DoD box, Notes.
- Task: `Task`, `Parent Story`, criteria, `Hours`, Notes.
- Epic: `Epic`, `Business Value`, `Product Goal alignment`, `Child Stories`,
  Notes.
- Bug: `Bug`, `Severity vs. Priority`, criteria, Notes. Bugs have no parent
  section — that is deliberate, do not add one.
- **At most 5 acceptance criteria.** More than 5 honest criteria means the
  issue is too big — flag it for a split, do not cram.
- An empty section is deleted, not left as a bare heading.
- `## Notes` is decisions, rescopes and follow-up numbers only. If it reads
  like a log of what happened, cut it.

### Gate 2 — Voice

Rewrite the text so it reads like Liviu wrote it, not like an agent wrote
it. **Read `VOICE.md` next to this file before starting.** It holds the
rules and three real before/after pairs taken from this board.

The short version: short sentences, plain words, no em dashes, no closing
zingers, say it once. Spell out jargon the first time ("security group",
not "SG").

**Where the voice comes from.** Liviu does not write the code or the
issues by hand, so nothing already on this board is a sample of his
writing. His messages in conversation are the only clean corpus.
`VOICE.md` is derived from those.

**Never calibrate against existing issues.** They were written by agents.
Copying their tone is how the artificial register survives the audit —
you would be sanding one agent's prose into another agent's prose and
calling it humanised. `VOICE.md` is the only reference.

**Match his complexity, not his typos.** He writes fast and leaves
spelling errors. Copy the sentence length, the rhythm and the word
choice. Do not copy the mistakes into a project artifact.

**Shortening must not cost a fact.** Issue numbers, file paths, class
names, commands and version numbers stay exact. If a sentence cannot get
shorter without going vague, leave it long.

Cut on sight: narrated investigation logs, "Lessons from the first live
run", growing caveat lists, pasted build output, test counts, coverage
percentages, notes about what the environment could not run.

### Gate 3 — Truth

Check every claim against the **current** repo, not against what the issue
says about itself.

- Does the file/directory/class the criterion names still exist under that
  name? (#2 claimed `frontend/filmpire`; it has been `frontend/lmdb` since
  ADR-013.)
- Does the config value still hold? Read the file, do not trust the text.
- Did a later issue or ADR supersede this work? If so, rescope the
  criterion in one line and name the ADR or issue that replaced it. Do not
  add a caveat under a criterion that is now wrong — rewrite the criterion.
- Never tick a box you have not personally verified this run.
- Never verify through a shortcut that skips the layer the issue is about.
  A gateway routing fix is not proven by calling the service directly.

Also fix the metadata, which drifts silently:

- Every issue has exactly one type label (`epic` / `user-story` / `task` /
  `bug`) and one priority (`P0-critical` … `P3-low`).
- **`sprint-N` labels are dead.** Sprints are GitHub Milestones. Strip any
  `sprint-0`…`sprint-5` label and make sure the Milestone is right instead.
- Linkage runs **both ways and twice over**: the markdown `Parent: #N` line
  on the child, the child listed back under `## Technical Tasks` /
  `## Child Stories` on the parent with a short label, **and** the native
  GitHub sub-issue link. `gh issue edit` cannot create the native link —
  use the GraphQL `addSubIssue` mutation:

```bash
# resolve node ids, then link child under parent
gh api graphql -f query='
mutation($parent:ID!,$child:ID!){
  addSubIssue(input:{issueId:$parent, subIssueId:$child}){ clientMutationId }
}' -f parent="$PARENT_NODE_ID" -f child="$CHILD_NODE_ID"
```

Check what is already linked before adding:

```bash
gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){
  repository(owner:$o,name:$r){ issue(number:$n){
    parent{number} subIssues(first:50){ totalCount nodes{number state} } } } }' \
  -f o=liviuionesi -f r=lmdb.dev -F n=<N>
```

### Gate 4 — Proof

This is the gate that makes the audit worth running.

For each acceptance criterion, name the thing that proves it. Write the
proof into the criterion itself, in parentheses, so the issue and the code
cannot drift apart silently:

```
- [x] All 9 backend modules are wired into one root Gradle build
      (`SettingsGradleTest.allNineModulesAreIncluded`)
```

Rules for the proof:

- **Prefer a test that CI already runs.** A JUnit test, a Vitest test, a
  Playwright spec, a workflow step. Something that goes red on its own.
- A repeatable **command** is acceptable when a test genuinely cannot cover
  it (`./gradlew build`, `kubectl apply -k …`). Write the command, not a
  date.
- **A date is never a proof.** `verified 2025-11-14` describes the past.
  Move that evidence to an issue comment and replace the criterion with
  something re-checkable today.
- **Manual-only is a last resort** and must say so: `(manual: <exact
  steps>)`. Cap it at one criterion per issue. More than one means the work
  is not really testable and needs a follow-up Task.

**If a criterion has no proof, write one.** That is part of this skill, not
a separate job:

1. Find the module that owns the behaviour.
2. Write the test next to the existing tests, in their style — JUnit 5 +
   WireMock for external calls, Testcontainers for real infrastructure.
   Match the neighbours; do not invent a new pattern.
3. Full Javadoc on the test class and every `@Test`, per the repo's
   documentation standard in `AGENTS.md`. Tests are not exempt.
4. Run it. `./gradlew :backend:<module>:test --tests '<Class>'`.
5. **Watch it fail first.** Break the thing it asserts, confirm the test
   goes red, put it back. A test that passes no matter what proves nothing.
6. Only then write its name into the criterion.

If a criterion turns out to be untestable in principle (a documentation
statement, a design decision), say so in one line in Notes and mark it
`(no test — <reason>)`. Being explicit is fine. Pretending is not.

## Order of work

1. **Read the Story and every child Task first, before editing anything.**
   Get both lists: markdown children and native sub-issues. If they
   disagree, that is finding number one.
2. **Tasks bottom-up, Story last.** A Story's criteria are only honest once
   its Tasks are. Doing the Story first means rewriting it twice.
3. Per Task: Gates 1 → 4, write any missing test, run it, edit the body
   via `gh issue edit <n> --body-file <file>` using a scratchpad file per
   issue.
4. Then the Story: Gates 1 → 4, and make its `## Technical Tasks` list
   match the real open/closed state of its children.
5. **Cascade.** If every Task under the Story is closed and the Story now
   genuinely meets the Definition of Done, close the Story and merge
   `develop` → `main`. Then reach up into the parent Epic and do exactly
   two things: tick this Story's box in `## Child Stories`, and close the
   Epic if every one of its Stories is now closed. Never leave an
   exhausted Story or Epic open.

   That upward reach is the **only** edit a Story run may make to an Epic.
   Do not rewrite the Epic's prose, criteria or Notes here — several
   Stories share one Epic (#22 parents #87, #88 and #89), so two Story
   runs rewriting one Epic body will overwrite each other. Full Epic
   rewrites belong to Epic mode, which one Task owns.
6. Commit code and test changes referencing the audit Task number. One
   commit per Task is fine; do not batch unrelated issues into one commit.
7. Post one short comment on the Story saying what changed and what is
   still open.

## Epic mode

`/audit-artifacts epic 22` audits one Epic body. Run it **after** every
Story under that Epic has been audited — an Epic's Child Stories list is
only honest once its Stories are.

Gates 1, 2 and 3 apply unchanged. Gate 4 does not: the Epic template has
no acceptance-criteria section, so there is nothing to attach a test to.
Its Child Stories list is the proof surface instead. Check all four:

- Every child Story is listed, with a short label after the number so the
  list reads without opening each one.
- Every box matches that Story's real open/closed state.
- Every listed Story is also linked natively as a sub-issue, and every
  native sub-issue appears in the list. The two must not disagree.
- No Task hangs directly off the Epic. Tasks belong to Stories. If one
  does, re-parent it under a Story or open the Story it needs.

Then the Epic's own text:

- The title names an outcome, not a phase. "[EPIC] Project Setup Phase"
  (#1) describes a slot in a schedule; the Epic template asks for the
  value delivered.
- `## Product Goal alignment` is filled in. "Indirectly serves it by ..."
  is a valid answer for tooling and maintenance Epics — write that rather
  than dropping the section.
- `## Notes` holds decisions and rescopes, not retrofit archaeology.
- A Story that was dropped rather than delivered gets a one-line reason in
  the list, not a box left unchecked forever.
- Close the Epic if every Story under it is closed.

One Task owns each Epic. Never audit an Epic from inside a Story run.

## Parallelism

Child Tasks that touch different modules can be audited in parallel with
one Agent each — but **only** if the user asked for subagents. Otherwise do
them in sequence in this session. Never parallelise the Story itself; it
depends on all its Tasks being finished.

## Report

A short table when the run ends:

| Issue | Shape | Language | Truth | Proof | Action |
|---|---|---|---|---|---|
| #85 | fixed | fixed | 2 stale AC rescoped | 3/4 tested, 1 manual | closed |

Then, in plain sentences: what tests were added, what could not be proven
and why, anything flagged as too big to fit 5 criteria, and anything you
were unsure how to rescope. Flag it — do not guess.

## Non-goals

- Not a cloud re-verification pass. Do not run a real `terraform apply` to
  groom text. If a criterion truly needs live infrastructure, keep the
  prior evidence, state it briefly, and mark the proof `(manual: …)`.
- Do not touch issues outside this Story's subtree, with the single
  exception in step 5: ticking this Story's box in its parent Epic, and
  closing that Epic when it is exhausted.
- Do not renumber, delete, or re-parent issues outside the subtree — that
  is a structural change the user should see first.
