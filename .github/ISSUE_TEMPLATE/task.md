---
name: Task
about: A technical subtask under a User Story (or standalone technical work with no user-facing slice)
title: '[TASK] '
labels: task
---

## Task
<!-- What needs to be done, and why, in a few sentences or bullets. -->

## Parent Story
Parent: #<N>
<!-- Replace the line above with a one-line reason if there's genuinely no
     parent Story (pure technical/infra work with no user-facing slice).
     An explicit "no parent" decision is fine; a missing section is not.

     Also link it natively: open the Story and use "Add existing issue"
     under Sub-issues. The markdown line above is for readers; the native
     link is what makes the project board show the hierarchy and roll up
     progress. Both, always — they must not disagree. -->

## Scope
<!-- Only if it's not obvious what's excluded. Name the sibling issue that
     owns anything deliberately left out. Delete this section otherwise. -->

## Acceptance Criteria
<!-- At most 5. Each one is a real, independently checkable milestone —
     something you can point at and say "done" or "not done". If honest
     scope needs more than 5, the task is too big: split it instead.

     Do not close this Task with any box below unchecked. Check each one
     against real evidence — the file, the commit, a test run — not against
     what this issue used to say. If something turned out infeasible, or a
     later decision superseded it, rescope the criterion in one line and
     name the ADR or issue that replaced it. -->
- [ ]
- [ ]

## Estimate
**Hours:** <Tasks are hour-estimated, not pointed — points size Stories, hours size the within-Sprint work of implementing one>

## Notes
<!-- Only if something genuinely needs flagging: a decision, a rescope, a
     known gap, a follow-up issue. Keep it short and plain.

     Not a running log. No pasted build or test output, no retelling of
     review rounds, no test counts or coverage percentages, no notes about
     what the environment couldn't run. None of that is read later. -->
