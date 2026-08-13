# ADR-013: Frontend Merged Into This Repo As a Monorepo

**Status:** Accepted
**Date:** 2026-07-30
**Deciders:** Project owner
**Related:** ADR-010 (mapped/persisted facade), §1.2/§7.2/Appendix A

## Context

Since the TMDB-facade pivot (ADR-010), the product's actual frontend has
been the pre-existing LMDB React app — originally a standalone project
at `~/Desktop/lmdb` (github.com/pehlivanu/lmdb), unrelated in
origin to this backend and predating it. The two repos evolved in lockstep
in practice (issue #34 required a frontend code change — reading
`REACT_APP_API_URL` instead of hardcoding TMDB's base URL — to consume this
backend) but lived in separate git histories, separate clones, separate
`git status`, with the pairing only documented, never enforced.

That split cost real friction: the runbook
(`docs/guides/RUN_WITH_LMDB_APP.md`) had to describe two working
directories and two "make sure this is committed" steps; a change that
spans both (like #34's) produced two disconnected commits with no shared
history to diff or bisect across; and anyone cloning this repo to look at
"the project" got only half of it.

## Decision

Merge `~/Desktop/lmdb`'s full git history into this repo at
`frontend/lmdb/`, preserving every commit and its original authorship
— not a fresh import, not a squash. Mechanically:

1. Clone the frontend repo to a scratch location.
2. `git filter-repo --path .env --invert-paths` to strip a `.env` file that
   had been committed (and later gitignored, but never removed from
   history) containing a TMDB API key.
3. `git filter-repo --replace-text` to redact the same key's literal string
   value, which had *also* been hardcoded directly in `src/services/TMDB.js`
   across several early commits before the app switched to reading it from
   an env var — deleting the `.env` file alone would not have removed that.
4. `git filter-repo --to-subdirectory-filter frontend/lmdb` to rewrite
   every remaining commit so its paths land under that prefix.
5. `git fetch` the rewritten history into this repo and
   `git merge --allow-unrelated-histories`.

This is the manual equivalent of `git subtree add --prefix=frontend/lmdb
<remote> master`; the `git-subtree` contrib tool itself isn't installed on
this machine and installing it needs `sudo`, which wasn't available in the
session that did this merge. The filter-repo route produces the same
result — a merge commit joining two previously-unrelated histories, with
every frontend file already living at the target prefix — without that
dependency.

The original `~/Desktop/lmdb` repo (and its GitHub remote) is left
untouched; this is not a migration that deletes the source, just an
additional copy of its history folded into a new home.

## Options Considered

**Full-history monorepo merge (chosen)** — one repo, one clone, one
`git log` that actually shows cross-stack changes together. Cost: every
frontend commit (44, after two are pruned empty by the secret-scrub step)
now lives permanently in this repo's history, and the merge itself is a
one-way door — undoing it cleanly later would need another history rewrite,
not just a revert.

**`git submodule`** — keeps the frontend as a separate repo/history,
referenced by commit pointer. Rejected: submodules are exactly the
"separate `git status`, easy to forget to update the pointer" friction this
decision exists to remove, not a fix for it.

**Squash-import (single commit, no history)** — simplest, smallest diff.
Rejected: throws away 44 commits of real authorship and incremental
history for a project the same person has been iterating on for over a
year; a monorepo merge whose whole point is "one history" that discards
the frontend's history isn't actually that.

**Leave it split, just document the pairing better** — lowest effort.
Rejected: the friction described in Context is exactly what documentation
alone had already failed to prevent (issue #34 needed two commits in two
repos for one logical change).

## Consequences

- Easier: `frontend/lmdb/` is now a normal part of this repo — clone
  once, `git log -- frontend/lmdb` shows its real history, a change
  spanning gateway config and frontend base-URL wiring can be one commit
  instead of two across two clones.
- Cost: repo size/clone time grow by the frontend's history (~44 commits,
  no large binaries — `node_modules` was always gitignored on the frontend
  side too, so nothing bulky came along).
- Secrets: the merged history is clean — verified by grepping `git log
  --all -p` across the merged range for both the `.env` path and the raw
  key string, zero hits. The key itself was judged low-stakes (personal
  TMDB API key, no billing/production exposure) and was not rotated; that
  call was made explicitly by the project owner, not inferred.
- `frontend/web-nextjs` and `frontend/mobile-react-native` — mentioned in
  earlier drafts of §1.2/§7.2/Appendix A as "descoped placeholder
  directories" — never actually existed in this repo's git history at any
  point (confirmed via `git ls-tree` before this merge). Those doc sections
  were already inaccurate before this ADR; corrected here as part of
  bringing the frontend's real location up to date, not a separate cleanup.
- Deployment/CI: no CI/CD workflow changes were made as part of this ADR
  (out of scope per the standing "don't touch CI/CD without asking" rule);
  `.github/workflows/terraform-plan.yml`'s path trigger
  (`infrastructure/terraform/`) is unaffected by files added under
  `frontend/`.
