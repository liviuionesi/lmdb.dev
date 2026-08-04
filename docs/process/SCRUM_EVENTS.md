# The Four Scrum Events, Adapted for an AI-Driven Team

The Scrum Guide requires four events every Sprint. An always-on AI
"team" has no calendar to meet on, so each event is adapted to *when it
actually needs to happen*, not skipped.

## Sprint Planning
Happens at the start of each Sprint, before any Task work begins:
1. Review the Product Backlog; pull Stories into the new Sprint Milestone
   up to a reasonable capacity (based on recent Sprints' actual velocity
   once there's history — a guess for Sprint 1).
2. Every pulled Story must meet `DEFINITION_OF_READY.md`.
3. Set a **Sprint Goal** — a single sentence describing what this Sprint
   is actually for, written into the Milestone's description. A Sprint
   with five unrelated Stories and no unifying goal is a scheduling
   accident, not a Sprint.

## Daily Scrum
The human equivalent is a 15-minute daily sync. The AI equivalent: **every
scheduled routine firing that picks up work posts a short status comment**
(already required by CLAUDE.md's "Observability" section) — what got
done, what's next, what's blocking. This is inspectable at any time
without waiting for a fixed daily slot, which is a legitimate, not lesser,
adaptation of the same inspect-and-adapt intent.

## Sprint Review
At the Sprint boundary (with completed Epics already promoted to main upon closure):
1. Actually run the software and confirm the Increment works — not just
   "tests pass," a real check that what shipped this Sprint functions.
2. Post a **Sprint Report**: Story Points committed vs. actually closed,
   which Stories slipped and the real reason, what's now demonstrably
   working that wasn't at Sprint start.

## Sprint Retrospective
Immediately after the Sprint Report, before starting the next Sprint's
Planning: a short, honest note — what went well, what didn't, **one**
concrete process change for next Sprint (not a wishlist). If a
retrospective finding implies a real process change, it gets applied to
`CLAUDE.md` or this `docs/process/` folder directly, not just written down
and forgotten — a retrospective that never changes anything isn't one.
