---
name: Task
about: A technical subtask under a Story, or standalone technical work
title: '[TASK] '
labels: task
---

<!-- Before opening this issue:
     Writing: .claude/skills/audit-artifacts/VOICE.md. Short sentences,
     plain words, facts and numbers. No filler, no corporate vocabulary.
     Labels: one type (set by this template) and one priority, P0-critical
     to P3-low. Assign the issue.
     Project board: set Status, Priority, Size, Estimate, Start date and
     Target date. Size and Estimate come from the points or hours below.
     Check your work: infrastructure/scripts/audit-check.py <number> -->

## Task
<!-- What needs doing, and why, in a few sentences or bullets. -->

## No parent
<!-- Delete this whole section when the Task has a Story. The native
     sub-issue link is the parent record; a markdown copy is a second thing
     to keep in sync.

     Keep it only for a deliberate "no parent" decision, replacing this
     comment with the one-line reason. That is the one thing the native
     link cannot express: no parent on purpose, versus somebody forgot.

     Set the parent from the Story: Sub-issues, Add existing issue. -->

## Scope
<!-- Only when it is not obvious what is excluded. Name the sibling issue
     that owns anything deliberately left out. Delete otherwise. -->

## Acceptance Criteria
<!-- At most 5, each independently checkable.

     Every criterion names the test or command that proves it, in brackets
     at the end:

       - [ ] All 9 backend modules are in one root Gradle build
             (`ProjectStructureTest.settingsGradleIncludesExactlyTheNineBackendModules`)

     A date is not a proof. "verified 2026-01-01" describes the past and
     cannot go red when the behaviour breaks. Put that evidence in a
     comment and write something re-checkable here.

     Prefer a test CI already runs. A repeatable command is acceptable when
     a test genuinely cannot cover it. If nothing can prove it, say so:
     (manual: exact steps) or (no test: reason). One manual criterion per
     issue at most; more than that means the work is not testable and needs
     a follow-up issue.

     Do not close with a box unchecked. Check each one against the code,
     not against what this issue used to say. If a later decision
     superseded one, rescope it in a line and name the ADR or issue that
     replaced it. -->
- [ ] <criterion> (`<test or command>`)
- [ ] <criterion> (`<test or command>`)

## Estimate
**Hours:** <hours, not points. Points size a Story; hours size the work of
implementing one Task inside a sprint.>

## Notes
<!-- Decisions, rescopes, follow-up issue numbers. Delete this section if
     there are none. Not a running log: no pasted build or test output, no
     test counts, no coverage percentages, no retelling of review rounds. -->
