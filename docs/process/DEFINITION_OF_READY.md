# Definition of Ready

A Story may be pulled into a Sprint only when all of these are true.
Referenced from every `[STORY]` issue's "Definition of Ready" checkbox —
not restated per issue.

- Written as `As a <role>, I want <goal>, so that <benefit>` and passes
  INVEST (Independent, Negotiable, Valuable, Estimable, Small, Testable).
  If it fails Small or Estimable, split it before it's Ready.
- Acceptance criteria are written as Given/When/Then, at most 5, each one
  independently testable.
- Dependencies (other issues, external services, data) are identified and
  either resolved or explicitly noted as blocking.
- Sized in Story Points (Fibonacci: 1, 2, 3, 5, 8, 13, 21).
- Any applicable [Non-Functional Requirements](NON_FUNCTIONAL_REQUIREMENTS.md)
  are known to the person/agent about to pick it up — not necessarily
  restated in the issue, but not a surprise either.

If any of these isn't true yet, the Story stays in the Product Backlog,
unscheduled, until it is.
