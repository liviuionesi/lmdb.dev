# Methodology

This project runs **Scrum** today. That's a choice, not a hard-coded
assumption — the automation is split so the methodology can change without
touching the part that actually does the work:

- **The Method** (`CLAUDE.md`'s work loop: implement → quality gates →
  code review → test review → fix-forward → resync/close → commit → push)
  is methodology-agnostic. It doesn't know or care whether the backlog is
  organized into Sprints, a Kanban board, or Waterfall phase gates.
- **The backlog shape and ceremonies** (this `docs/process/` folder, the
  Epic→Story→Task hierarchy, Sprint Milestones, the four Scrum Events
  below) are the swappable layer. A future project that wants Kanban
  swaps this folder for WIP-limit rules and a continuous-flow board, no
  Sprints; Waterfall swaps it for phase-gate documents (Requirements →
  Design → Implementation → Verification) and drops Sprints/Stories
  entirely. Same Method underneath, different backlog discipline on top.

Changing methodology is a deliberate, human-approved decision (same as
changing architecture) — never something an autonomous run switches on its
own.

## Scrum, done properly (not just the artifacts)

Referencing the terms from the official Scrum Guide (2020) so this is
correctness-checkable against it, not an approximation:

- **Product Goal** — `PRODUCT_GOAL.md`.
- **Product Backlog** — GitHub Issues.
- **Sprint Backlog** — the Stories/Tasks assigned to the current Sprint
  Milestone.
- **Increment** — the actual working, deployed software, cumulative.
- **Definition of Done** — `DEFINITION_OF_DONE.md`.
- **The four Scrum Events** — see `SCRUM_EVENTS.md` for how each maps to
  an AI-driven, continuously-running team instead of a human one meeting
  on a calendar. Skipping any of these isn't "leaner Scrum," it's not
  Scrum — the adaptation is in *how* each event happens, not whether it
  happens.
