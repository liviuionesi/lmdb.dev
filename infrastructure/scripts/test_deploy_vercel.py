#!/usr/bin/env python3
"""Tests for deploy-vercel.sh's env-var pass-through (#149, audited by #302).

`deploy-vercel.sh` drives a real `npm` build and the real Vercel CLI via
`npx`, neither of which can run in this environment (no Vercel account, no
network egress to Vercel). As in test_tunnel_scripts.py, fake `npm` and
`npx` executables are put on `PATH` that record their arguments instead of
doing real work, so the script's own logic — which build-env flags it
passes through to the Vercel CLI — runs for real and is asserted for real.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "infrastructure/scripts/deploy-vercel.sh"
FRONTEND_DIR = REPO_ROOT / "frontend/lmdb"

# Both fakes append every invocation to $FAKE_CALLS so the test can inspect
# exactly what the script ran, then exit 0 without doing real work.
FAKE_NPM = """#!/usr/bin/env bash
echo "npm $*" >> "$FAKE_CALLS"
exit 0
"""
FAKE_NPX = """#!/usr/bin/env bash
echo "npx $*" >> "$FAKE_CALLS"
exit 0
"""


class DeployVercelBuildEnvTest(unittest.TestCase):
    """Runs the real deploy-vercel.sh against faked npm/npx on PATH."""

    def setUp(self):
        """Installs fake `npm`/`npx` binaries on PATH ahead of the real ones."""
        self._tmp = tempfile.TemporaryDirectory()
        fake_npm = pathlib.Path(self._tmp.name) / "npm"
        fake_npm.write_text(FAKE_NPM)
        fake_npm.chmod(fake_npm.stat().st_mode | stat.S_IEXEC)
        fake_npx = pathlib.Path(self._tmp.name) / "npx"
        fake_npx.write_text(FAKE_NPX)
        fake_npx.chmod(fake_npx.stat().st_mode | stat.S_IEXEC)
        self._calls_file = pathlib.Path(self._tmp.name) / "calls.log"
        self._calls_file.write_text("")
        self._env = dict(os.environ)
        self._env["PATH"] = f"{self._tmp.name}:{self._env['PATH']}"
        self._env["FAKE_CALLS"] = str(self._calls_file)
        # node_modules already exists in this checked-out frontend, so the
        # script's "npm install" branch is skipped either way; no setup
        # needed for that.

    def tearDown(self):
        self._tmp.cleanup()

    def _run(self, extra_env):
        env = dict(self._env)
        env.update(extra_env)
        # VERCEL_TOKEN unset by default so the interactive branch is used;
        # tests set it explicitly where the branch under test needs it.
        env.pop("VERCEL_TOKEN", None)
        env.update(extra_env)
        result = subprocess.run(
            ["bash", str(SCRIPT)], env=env, capture_output=True, text=True,
            cwd=REPO_ROOT,
        )
        return result, self._calls_file.read_text().splitlines()

    def test_passes_both_build_env_vars_through_to_vercel_when_both_are_set(self):
        """Given both Vite vars are set, the script forwards both as --build-env.

        Task #149's AC2 states the script "injects VITE_API_URL and
        VITE_TMDB_KEY automatically". Before this fix, the script never
        referenced either variable, so the deployed build could only get
        them from a one-time manual entry in the Vercel dashboard.
        """
        result, calls = self._run({
            "VITE_API_URL": "https://api.lmdb.dev",
            "VITE_TMDB_KEY": "test-tmdb-key",
        })
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        npx_call = next(c for c in calls if c.startswith("npx "))
        self.assertIn("--build-env VITE_API_URL=https://api.lmdb.dev", npx_call)
        self.assertIn("--build-env VITE_TMDB_KEY=test-tmdb-key", npx_call)

    def test_omits_a_build_env_flag_for_a_variable_that_is_not_set(self):
        """Given only one Vite var is set, the script forwards only that one.

        Confirms the pass-through is conditional per variable rather than
        always emitting both flags (which would pass a literal empty value
        and clobber whatever the Vercel project has configured for the
        other one).
        """
        result, calls = self._run({"VITE_API_URL": "https://api.lmdb.dev"})
        npx_call = next(c for c in calls if c.startswith("npx "))
        self.assertIn("--build-env VITE_API_URL=https://api.lmdb.dev", npx_call)
        # The unset variable's name may appear in the missing-key warning,
        # but never as a --build-env flag on the actual Vercel invocation.
        self.assertNotIn("--build-env VITE_TMDB_KEY", npx_call)

    def test_warns_when_vite_tmdb_key_is_missing(self):
        """Given VITE_TMDB_KEY is unset, the script warns before deploying.

        Matches the existing warning pattern in infrastructure/scripts/env.sh
        for the backend's TMDB_API_KEY, so a demo deployed without the key
        fails loudly in the deploy log instead of silently shipping a
        catalog page that can never load movie data.
        """
        result, _ = self._run({})
        self.assertIn("VITE_TMDB_KEY is not set", result.stdout + result.stderr)


if __name__ == "__main__":
    unittest.main()
