---
name: Bug
about: Something is broken, with a known or suspected reproduction
title: '[BUG] '
labels: bug
---

<!-- Before opening this issue:
     Writing: .claude/skills/audit-artifacts/VOICE.md. Short sentences,
     plain words, facts and numbers. No filler, no corporate vocabulary.
     Labels: one type (set by this template) and one priority, P0-critical
     to P3-low. Assign the issue.
     Project board: set Status, Priority, Size, Estimate, Start date and
     Target date. Size and Estimate come from the points or hours below.
     Check your work: infrastructure/scripts/audit-check.py <number> -->

## Bug
<!-- What is broken and how to see it: reproduction steps, a command, or a
     log excerpt. Say whether it is confirmed live or only suspected. -->

## Root Cause
<!-- Fill in once known. Delete this section while still investigating. -->

## Severity vs. Priority
<!-- Two axes, set independently. A low-severity bug can be P0 when it
     blocks a demo. A Blocker-severity bug in dead code can be P3.

     The priority written here and the P0-critical to P3-low label on the
     issue must agree. audit-check.py fails the issue when they do not. -->
**Severity (technical impact):** Blocker / Critical / Major / Minor
**Priority (business urgency):** P0 / P1 / P2 / P3

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
<!-- Two more rules for a Bug specifically:

     "Fixed and verified live" means you drove the broken path and watched
     it work, not that the diff looks right. Never verify through a
     shortcut that skips the layer the bug is in: a gateway routing fix is
     not proven by calling the service directly.

     The regression test is the criterion most often ticked without the
     test existing. Name it, and break the fix once to watch it fail. -->
- [ ] Fixed, and the fixed path verified through the layer the bug was in
      (`<test or command>`)
- [ ] Regression test added, seen failing before it passed (`<test name>`)

## Notes
<!-- Decisions, rescopes, follow-up issue numbers. Delete if there are
     none. Not a running log.

     Bugs sit outside the Epic to Story to Task hierarchy, so there is no
     parent section here by design. -->
