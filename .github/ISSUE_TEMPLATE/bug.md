---
name: Bug
about: Something is broken, with a known (or suspected) repro
title: '[BUG] '
labels: bug
---

## Bug
<!-- What's broken, and how to see it — repro steps, a command, or a log
     excerpt. Confirmed live or just suspected? Say which. -->

## Root Cause
<!-- Fill in once known. Delete this section while still investigating. -->

## Severity vs. Priority
<!-- Two different axes, set independently — a low-severity bug can still
     be P0 (e.g. it's blocking a demo), and a Blocker-severity bug in dead
     code can be P3. -->
**Severity (technical impact):** Blocker / Critical / Major / Minor
**Priority (business urgency):** P0 / P1 / P2 / P3
<!-- The priority written here and the P0-critical..P3-low label on the
     issue must agree. audit-check.py fails the issue when they do not. -->

## Acceptance Criteria
<!-- At most 5. Verified against the real running system, not just a unit
     test or a shortcut path that skips the layer the bug is actually in.

     Do not close this Bug with any box below unchecked. "Fixed and
     verified live" means you actually drove the broken path and watched it
     work — not that the diff looks right. If the fix turned out to be
     removal rather than repair, rescope the criteria in one line and name
     the ADR or issue that decided it. -->
- [ ] Fixed and verified live
- [ ] Regression test added

## Notes
<!-- Only if something genuinely needs flagging: the real root cause, a
     rescope, a follow-up issue. Keep it short and plain.

     Not a running log. No pasted build or test output, no retelling of
     review rounds, no test counts or coverage percentages. Bugs sit
     outside the Epic → Story → Task hierarchy, so there's no parent
     section here by design. -->
