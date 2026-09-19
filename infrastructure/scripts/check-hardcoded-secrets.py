#!/usr/bin/env python3
"""Regression check for #113/#114: no hardcoded default password fallback
reappears in a tracked source file, config, Dockerfile, doc, or script.

Scans every file `git ls-files` reports (so it never sees an untracked
build artifact) for the exact default strings #114 removed. `minioadmin123`
is the one deliberate exception: #106 decided it is a local-dev-only
default that already matches `docker-compose.yml`'s own default, not a
leaked secret, and kept it in exactly two files. Anywhere else, it fails
the same as the others.

Run: python3 infrastructure/scripts/check-hardcoded-secrets.py
Tested by: test_check_hardcoded_secrets.py
"""
import pathlib
import subprocess
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent.parent

# Removed for good in #114: any reappearance anywhere tracked is a regression.
BANNED_EVERYWHERE = ["admin123", "redis123", "postgres123"]

# Kept deliberately in #106 as a local-dev default matching docker-compose.yml's
# own default; allowed only in these two files.
MINIOADMIN_DEFAULT = "minioadmin123"
MINIOADMIN_ALLOWED_FILES = {
    "infrastructure/docker/docker-compose.yml",
    "backend/media-service/src/main/resources/application.yml",
}

# This script and its test name the banned strings in prose; skip self-matches.
SELF_EXCLUDED_FILES = {
    "infrastructure/scripts/check-hardcoded-secrets.py",
    "infrastructure/scripts/test_check_hardcoded_secrets.py",
}

# Historical issue-body mirrors quote the removed strings when describing
# what #114 did; they are records of the issue text, not source under audit.
EXCLUDED_PREFIXES = (".github/issues/backlog/",)

# `.env.example` is a template a developer copies to `.env` (README.md line
# ~270: `cp infrastructure/docker/.env.example infrastructure/docker/.env`).
# Nothing reads it directly, so documenting example local-dev values there
# is the file's purpose, not a leaked or silently-defaulted secret.
EXCLUDED_SUFFIXES = (".env.example",)


def tracked_files() -> list[str]:
    """Lists every git-tracked file, relative to the repo root.

    Using `git ls-files` instead of walking the filesystem means build
    output, `node_modules`, and anything else `.gitignore` excludes is
    never scanned — only what is actually committed.
    """
    out = subprocess.run(
        ["git", "ls-files"], cwd=REPO_ROOT, capture_output=True, text=True, check=True)
    return [line for line in out.stdout.splitlines() if line]


def find_violations(paths: list[str], root: pathlib.Path = REPO_ROOT) -> list[str]:
    """Checks every path against both rules and returns one message per hit.

    1. Any of BANNED_EVERYWHERE anywhere tracked is always a violation.
    2. MINIOADMIN_DEFAULT is a violation everywhere except the two files
       #106 approved it in.

    `root` defaults to the real repo but is overridable so the test suite
    can point this at a synthetic directory instead of the live tree.
    """
    violations = []
    for rel_path in paths:
        if rel_path in SELF_EXCLUDED_FILES or rel_path.startswith(EXCLUDED_PREFIXES):
            continue
        if rel_path.endswith(EXCLUDED_SUFFIXES):
            continue
        full_path = root / rel_path
        try:
            text = full_path.read_text(errors="ignore")
        except (OSError, IsADirectoryError):
            continue
        # 'minioadmin123' contains 'admin123' as a substring; strip approved
        # minioadmin occurrences first so they are judged only by the
        # MINIOADMIN_DEFAULT rule below, not double-flagged here too.
        scan_text = text.replace(MINIOADMIN_DEFAULT, "")
        for needle in BANNED_EVERYWHERE:
            if needle in scan_text:
                violations.append(f"{rel_path}: contains banned default '{needle}'")
        if MINIOADMIN_DEFAULT in text and rel_path not in MINIOADMIN_ALLOWED_FILES:
            violations.append(
                f"{rel_path}: contains '{MINIOADMIN_DEFAULT}' outside the "
                f"#106-approved files {sorted(MINIOADMIN_ALLOWED_FILES)}")
    return violations


def main() -> int:
    violations = find_violations(tracked_files())
    if violations:
        print(f"{len(violations)} hardcoded-secret violation(s):")
        for v in violations:
            print(f"  {v}")
        return 1
    print("No hardcoded default password fallbacks found.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
