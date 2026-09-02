---
name: User Story
about: A vertical slice of user-facing value, the real requirements unit
title: '[STORY] As a <role>, I want <goal> so that <benefit>'
labels: user-story
---

<!-- Before opening this issue:
     Writing: .claude/skills/audit-artifacts/VOICE.md. Short sentences,
     plain words, facts and numbers. No filler, no corporate vocabulary.
     Labels: one type (set by this template) and one priority, P0-critical
     to P3-low. Assign the issue.
     Project board: set Status, Priority, Size, Estimate, Start date and
     Target date. Size and Estimate come from the points or hours below.
     Check your work: infrastructure/scripts/audit-check.py <number> -->

## User Story
**As a** <role>
**I want** <goal>
**So that** <benefit>

<!-- INVEST before this enters the backlog: Independent, Negotiable,
     Valuable, Estimable, Small, Testable. If it fails Small or Estimable,
     split it rather than forcing a sixth criterion. -->

## No parent
<!-- Delete this whole section when the Story has an Epic. The native
     sub-issue link is the parent record; a markdown copy is a second thing
     to keep in sync.

     Keep it only for a deliberate "no parent" decision, replacing this
     comment with the one-line reason. That is the one thing the native
     link cannot express: no parent on purpose, versus somebody forgot.

     Set the parent from the Epic: Sub-issues, Add existing issue. -->

## Acceptance Criteria (Given/When/Then)
<!-- At most 5, each independently checkable.

     Every criterion names the test or command that proves it, in brackets
     at the end:

       - [ ] Given a service starts, when it registers with Eureka, then it
             appears in the registry (`EurekaServerIntegrationTest`)

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
- [ ] Given <context>, when <action>, then <outcome> (`<test or command>`)
- [ ] Given <context>, when <action>, then <outcome> (`<test or command>`)

## Definition of Ready
- [ ] Meets [Definition of Ready](https://github.com/liviuionesi/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_READY.md)

## Story Points
**Estimate:** <Fibonacci: 1 / 2 / 3 / 5 / 8 / 13 / 21>
<!-- The sprint is the GitHub Milestone. There is no Sprint section here:
     the milestone field already holds it and two copies drift apart. -->

## Closing this Story
<!-- Technical Tasks are the native sub-issue links and nothing else, for
     the same reason as the Epic template. Do not close this Story while a
     child Task is open. -->

## Definition of Done
- [ ] Meets [Definition of Done](https://github.com/liviuionesi/lmdb.dev/blob/develop/docs/process/DEFINITION_OF_DONE.md)

## Notes
<!-- Decisions, rescopes, follow-up issue numbers. Delete this section if
     there are none. Not a running log: no pasted build or test output, no
     test counts, no coverage percentages, no retelling of review rounds. -->
