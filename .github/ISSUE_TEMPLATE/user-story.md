---
name: User Story
about: A vertical slice of user-facing value (the real requirements-writing unit)
title: '[STORY] As a <role>, I want <goal> so that <benefit>'
labels: user-story
---

## User Story
**As a** <role>
**I want** <goal>
**So that** <benefit>

<!-- INVEST check before this Story enters the backlog: Independent,
     Negotiable, Valuable, Estimable, Small, Testable. If it fails Small or
     Estimable, split it — don't force-fit a 6th acceptance criterion. -->

## Parent Epic
Parent: #<N>
<!-- Every Story belongs to an Epic. If it genuinely doesn't, replace the
     line above with a one-line reason — an explicit "no parent" decision
     is fine, a missing section is not.

     Also link it natively: open the Epic and use "Add existing issue"
     under Sub-issues. The markdown line above is for readers; the native
     link is what makes the project board show the hierarchy and roll up
     progress. Both, always — they must not disagree. -->

## Acceptance Criteria (Given/When/Then)
<!-- At most 5. Each one is real and independently testable — the
     "Confirmation" that makes this Story done, not a fuzzy aspiration.

     Do not close this Story with any box below unchecked. Check each one
     against real evidence — code, a test run, a live check — not against
     what this issue used to say. If something turned out infeasible,
     rescope the criterion in one line and say why; don't leave it
     unchecked forever. -->
- [ ] Given <context>, when <action>, then <outcome>
- [ ] Given <context>, when <action>, then <outcome>

## Definition of Ready
- [ ] Meets [Definition of Ready](https://github.com/liviuionesi/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_READY.md)

## Story Points
**Estimate:** <Fibonacci: 1 / 2 / 3 / 5 / 8 / 13 / 21>

## Sprint
**Milestone:** <link>

## Technical Tasks
<!-- Broken down as child Task issues, not inline. Give every entry a short
     label after the number, so this list can be read without opening each
     one. Link each Task natively as a sub-issue too, and keep the boxes
     matching the Tasks' real open/closed state. -->
- [ ] #<N> — <short label>

## Definition of Done
- [ ] Meets [Definition of Done](https://github.com/liviuionesi/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_DONE.md)

## Notes
<!-- Only if something genuinely needs flagging: a decision, a rescope, a
     known gap, a follow-up issue. Keep it short and plain.

     Not a running log. No pasted build or test output, no retelling of
     review rounds, no test counts or coverage percentages, no notes about
     what the environment couldn't run. None of that is read later. -->
