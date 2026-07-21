# Filmpire Microservices — Autonomous Work Contract

This file governs unattended/scheduled Claude Code runs on this repo (see the
`filmpire-autonomous-dev` scheduled task). A human-driven session can ignore
this file, but should still respect the conventions below.

## Where work happens

- All autonomous work commits directly onto the `develop` branch (create it
  from `main` if it doesn't exist locally yet). Never commit to `main`.
- No per-issue branches — everything lands as sequential commits on
  `develop`, so the user can scan that one branch and see exactly what was
  done, in what order, and why.
- Never run `git push` or `gh pr create`. This repo has a single
  collaborator (the user), who can't approve or merge their own PRs, so
  opening one is pure friction. Commit locally on `develop` and stop there —
  the user reviews and pushes/merges by hand whenever they're ready.
- Do not add a `Co-Authored-By` trailer or any other AI-attribution line to
  commit messages — the user is the sole author of their own work. Keeping
  autonomous work on `develop` (separate from wherever the user's own
  commits live) is what distinguishes it, not a commit trailer.

## Source of truth for remaining work

- GitHub Issues on `pehlivanu/filmpire-microservices` are the backlog.
  `.github/issues/PROJECT_ROADMAP.md` gives the phase-level narrative; the
  issues themselves (`gh issue list`) are the authoritative status.
- Architecture reference: `docs/architecture/ARCHITECTURE.md`.
- Existing completed services (`movie-service`, `discovery-service`,
  `config-service`, `api-gateway`) are the pattern to follow for structure,
  package layout, test style (JUnit + WireMock for external calls), and
  Spring Boot/Gradle conventions. Match them rather than inventing new
  patterns.

## Picking the next task

1. Run `gh issue list --repo pehlivanu/filmpire-microservices --state open`.
2. Run `git log develop --oneline` and check which issue numbers are already
   referenced in commit subjects — that tells you what's done or in
   progress. If the most recent commit is prefixed `WIP:`, finish that issue
   before starting a new one.
3. Pick the highest-priority open issue (`P0-critical` > `P1-high` > ...)
   not yet referenced in `develop`'s history.
4. If an issue is an Epic, work its next unfinished child task issue instead
   of the epic itself.

## Commit conventions

- Commit subject references the issue: `feat: Implement User Service (#17)`.
- Before committing: build and run tests for any module you touched
  (`./gradlew :backend:<module>:test` or the relevant module path) and make
  sure it's green. Don't commit code you haven't run tests against.
- If you must stop mid-issue (context or budget runs out before the issue is
  done), still leave `develop` in a buildable, fully-committed state — no
  uncommitted work, no broken build. Prefix the commit subject with `WIP:`
  and include a short note in the commit body of exactly what's left to do,
  so the next scheduled run (or the user) can pick it up cold.

## Scope discipline

- One issue (or one clearly-bounded chunk of a large issue) per run. Don't
  jump between unrelated issues in the same run.
- Don't touch `main`, other branches, or issues you didn't pick.
- Don't edit CI/CD workflows, branch protection, or repo settings.
- Don't add dependencies or upgrade framework versions unless the issue
  specifically calls for it.
