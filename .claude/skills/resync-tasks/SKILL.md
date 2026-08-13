---
name: resync-tasks
description: Rewrite GitHub issues on this repo to be short, plain, and accurate — sync each issue against real implementation state, rescope items that turned out infeasible instead of piling on caveats, and cut LLM-verbose prose down to plain language. Use when the user runs /resync-tasks or asks to clean up, simplify, or declutter GitHub issues.
tools: Bash, Read, Grep, Glob, Agent, TodoWrite
---

# /resync-tasks — issue clarity sweep

Fixes the failure mode where an issue accumulates paragraph after paragraph
of "Lessons from the first live run," a growing "Still open" list, and dense
run-on sentences — until it's unreadable.

**Rule: bullets, not prose — but bullets that still make sense.** This is
not a "compress everything to the fewest words possible" pass. A bullet can
be a full clause if that's what it takes to be clear; the goal is readable
and scannable, not telegraphic. What actually gets cut: paragraphs,
narrated investigation logs, and repeated caveats — not meaning.

**Rule: max 5 acceptance criteria per task.** Each one is a real,
independently checkable milestone — something you can point at and say
"done" or "not done," not a fuzzy aspiration. If a task's honest scope
needs more than 5 real milestones, that's a signal the task is too big —
see "When a task is too big" below, don't just cram a 6th bullet in.

**Rule: acceptance criteria must be in sync with the task description.**
Every criterion traces back to something the description actually asks
for, and nothing the description promises is left without a matching
criterion. If a criterion doesn't map to a stated requirement, either the
criterion or the description is wrong — fix whichever one is stale.

**Rule: no unexplained abbreviations or jargon.** Spell out anything not
obvious (e.g. "SG" → "security group", "CIDR block" not just "CIDR").

**Principle behind all of this: many small precise tasks beat one big task
that tries to do everything.** A large task's implementation drifts from
its own description as work happens — half gets built one way, half
another — and then the whole description needs rewriting from scratch
just to stay honest. A task scoped to 5 or fewer real milestones stays in
sync with its own code far more easily, and produces better-quality,
narrower changes besides.

[[feedback_keep_github_issues_in_sync]] still applies underneath this:
ground every claim in real code/commit/test state, don't just reinterpret
stale text to look done, never leave a genuinely-done issue unchecked. This
skill adds the clarity/scoping discipline on top of that accuracy
discipline — both matter, accuracy first, clarity always.

## A concrete before/after (from the incident that created this skill)

**Before (real example, #27 — too long, too dense, keeps growing):**
```
## Acceptance Criteria
- [ ] apply/destroy round-trip with zero manual console steps — not yet attempted
- [ ] Stays within 750h t3.micro + 30 GB EBS free tier — can't confirm without a live apply
- [ ] Core service slice runs on the single node — can't confirm without a live apply

## Still open before this can close
Same shape as #26's: infra code is written and self-consistent, but nothing
has actually touched a real AWS account. Needs, in order: (1) bootstrap the
S3/DynamoDB state backend by hand, (2) either `aws configure` locally or
stand up the OIDC role documented in the README, (3) a real `terraform
apply` → `kubectl apply -k overlays/aws` → curl the gateway → `terraform
destroy` round-trip, watching for the memory-sizing risk noted above...
```
(and after a live run, this grew to five numbered paragraphs of findings,
each several sentences long — see the issue's history for how bad it got.)

**After (tight, plain, rescoped where the original plan didn't hold):**
```
## Task
Run k3s on a small EC2 instance as the AWS demo target (EKS's managed
control plane isn't free).

## Acceptance Criteria
- [x] apply -> deploy -> gateway responds -> destroy, verified clean (2026-08-01)
- [x] Runs within AWS free credits, budget-guard tripwire live — t3.micro
      couldn't actually run this reliably, t3.small is the real working
      size (small cost, covered by free credits, not literal $0)
- [x] Core services run on the single node

## Notes
- Resizing/restarting the node in place breaks its network config — destroy
  and recreate instead. No code fix yet.
- Images are pushed to ghcr.io manually right now because the CI pipeline
  that's supposed to publish them (#28) is currently broken for an
  unrelated reason.
```
Use this pair as the calibration reference for every issue in the sweep.

## When a task is too big

If an issue's honest, real scope needs more than 5 independently-checkable
milestones, don't force them all into one list. Instead:
- Pick the 5 that best represent the core deliverable.
- Add a one-line Note naming what's left out and recommending it become
  its own issue(s) — don't silently drop it, and don't auto-create the
  split issues yourself (that changes issue numbering/epic links, a bigger
  structural change than this sweep should make without the user seeing
  it first). Flag it in the final report instead.

## Process

1. **Discover.** `gh issue list --repo liviuionesi/lmdb.dev --state open --limit 200`.
   Report the count before starting.

2. **Dispatch one Agent per issue, in parallel.** Give each agent:
   - The before/after example above, verbatim, as the style calibration.
   - The issue number and repo.
   - Instructions:
     1. `gh issue view <n> --repo liviuionesi/lmdb.dev --json body,comments,state,title`.
     2. Ground the rewrite in real state — check relevant code/commits
        (`git log`, file existence, a quick test run if genuinely cheap and
        the issue's claim is easy to verify) rather than trusting the old
        text. Do not re-run expensive live infrastructure verification
        (cloud applies, full test suites) just for this sweep — if the
        issue already documents a real prior verification, keep that
        evidence, just state it briefly.
     3. Rewrite the body: a short task description in plain bullets or a
        couple of sentences, an acceptance-criteria checklist of at most 5
        real milestones (see "When a task is too big" above if honest scope
        needs more), a brief notes section only if something genuinely
        needs flagging (not a growing caveat list). Every criterion must
        map to something the description actually asks for. If part of the
        original plan turned out infeasible, rescope the item to match
        reality in
        one line instead of leaving it unchecked forever.
     4. If the issue is a stub that was never started (template text,
        untouched), leave it largely as-is but still trim boilerplate and
        check the acceptance criteria are a real, checkable list — don't
        invent progress that doesn't exist.
     5. Apply the same brevity/plain-language rule to any NEW comment this
        agent posts (only post a comment if the rewrite meaningfully
        changed scope/status — a pure formatting tidy-up doesn't need one).
     6. If the issue is now genuinely, verifiably done against its own
        (possibly rescoped) acceptance criteria, close it with a one-line
        comment — per [[feedback_keep_github_issues_in_sync]], never leave
        a done issue open, but never close on a technicality either.
     7. Update the issue via `gh issue edit <n> --body-file <tmpfile>` and,
        if closing, `gh issue close <n> --reason completed`.
   - Tool access: Bash (for `gh`/`git`), Read, Grep, Glob. No Write needed
     — issue bodies go through `gh issue edit`, not the filesystem (agents
     may still need a scratch file for `--body-file`; use the session
     scratchpad directory for that, one file per issue to avoid collisions).

3. **Report.** A short table: issue number, old length (rough line count)
   → new length, whether scope changed, whether it closed, and whether it
   was flagged as too big for 5 milestones (with the recommended split).
   Flag any issue an agent was unsure how to rescope rather than guessing.

## Non-goals

- Not a re-verification pass — don't re-run cloud applies or full test
  suites just to groom text. If real verification is needed to know an
  issue's true status, say so in the report instead of skipping it
  silently or guessing.
- Not for epics' child-issue lists or the roadmap doc — scope is issue
  bodies/comments only unless the user asks for those too.
