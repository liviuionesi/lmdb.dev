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

## Acceptance Criteria
<!-- At most 5. Verified against the real running system, not just a unit
     test or a shortcut path that skips the layer the bug is actually in. -->
- [ ] Fixed and verified live
- [ ] Regression test added

## Notes
<!-- Only if something genuinely needs flagging. Not a running log. -->
