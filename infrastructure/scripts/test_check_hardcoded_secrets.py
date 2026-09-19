#!/usr/bin/env python3
"""Tests for check-hardcoded-secrets.py, the #113/#114 regression check.

Each test writes a synthetic file tree under a temp directory and calls
`find_violations` against it directly, so the suite proves the rules
themselves rather than the current state of the real repo — it does not go
red just because someone edits an unrelated file.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import importlib.util
import pathlib
import tempfile
import unittest

# check-hardcoded-secrets.py is not an importable module name (the hyphens
# are not a legal identifier), so it is loaded by path rather than `import`.
_SPEC = importlib.util.spec_from_file_location(
    "check_hardcoded_secrets",
    pathlib.Path(__file__).with_name("check-hardcoded-secrets.py"))
chs = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(chs)


def write(root: pathlib.Path, rel_path: str, content: str) -> None:
    """Writes `content` to `root/rel_path`, creating parent directories."""
    full = root / rel_path
    full.parent.mkdir(parents=True, exist_ok=True)
    full.write_text(content)


class BannedEverywhereTest(unittest.TestCase):
    """#114: admin123/redis123/postgres123 must never reappear anywhere
    tracked."""

    def test_admin123_in_a_config_file_is_reported(self):
        """A hardcoded 'admin123' fallback reappearing is exactly the
        regression #114 fixed; the check must flag it."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "backend/foo/application.yml", "password: admin123")
            violations = chs.find_violations(["backend/foo/application.yml"], root=root)
        self.assertEqual(len(violations), 1)
        self.assertIn("admin123", violations[0])

    def test_redis123_and_postgres123_are_each_reported(self):
        """All three named defaults are independently banned, not just
        admin123."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "a.yml", "x: redis123")
            write(root, "b.yml", "y: postgres123")
            violations = chs.find_violations(["a.yml", "b.yml"], root=root)
        self.assertEqual(len(violations), 2)

    def test_a_clean_file_reports_nothing(self):
        """A file using an env-var placeholder instead of a literal default
        must not be flagged."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "application.yml", "password: ${POSTGRES_PASSWORD}")
            violations = chs.find_violations(["application.yml"], root=root)
        self.assertEqual(violations, [])


class MinioadminExceptionTest(unittest.TestCase):
    """#106: minioadmin123 is a deliberate local-dev default, kept in
    exactly two files."""

    def test_minioadmin123_in_an_approved_file_is_not_reported(self):
        """The two files #106 approved must stay clean even though they
        contain the string."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "infrastructure/docker/docker-compose.yml",
                  "MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD:-minioadmin123}")
            violations = chs.find_violations(
                ["infrastructure/docker/docker-compose.yml"], root=root)
        self.assertEqual(violations, [])

    def test_minioadmin123_outside_the_approved_files_is_reported(self):
        """The same string in a third file is a new disclosure, not the
        one #106 reviewed and accepted."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "docs/some-doc.md", "default: minioadmin123")
            violations = chs.find_violations(["docs/some-doc.md"], root=root)
        self.assertEqual(len(violations), 1)
        self.assertIn("minioadmin123", violations[0])

    def test_minioadmin123_does_not_also_trigger_the_admin123_rule(self):
        """'minioadmin123' contains 'admin123' as a substring. An approved
        file must not be double-flagged by the unrelated admin123 rule."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "backend/media-service/src/main/resources/application.yml",
                  "secret-key: ${MINIO_SECRET_KEY:${MINIO_ROOT_PASSWORD:minioadmin123}}")
            violations = chs.find_violations(
                ["backend/media-service/src/main/resources/application.yml"], root=root)
        self.assertEqual(violations, [])


class ExclusionTest(unittest.TestCase):
    """Files that are templates or historical records, not live config,
    are out of scope."""

    def test_env_example_is_not_scanned(self):
        """.env.example is a template copied to .env (README.md); it is
        expected to show example local-dev values, not a leaked secret."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "infrastructure/docker/.env.example", "POSTGRES_PASSWORD=admin123")
            violations = chs.find_violations(
                ["infrastructure/docker/.env.example"], root=root)
        self.assertEqual(violations, [])

    def test_backlog_mirror_is_not_scanned(self):
        """.github/issues/backlog/*.md quotes the removed strings while
        describing what #114 did; it is a record of the issue text."""
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, ".github/issues/backlog/114.md", "Removed admin123/redis123.")
            violations = chs.find_violations(
                [".github/issues/backlog/114.md"], root=root)
        self.assertEqual(violations, [])


if __name__ == "__main__":
    unittest.main()
