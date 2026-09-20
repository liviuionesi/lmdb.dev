#!/usr/bin/env python3
"""Tests for start-live-demo.sh's health-check exit code (#148, audited by #302).

`start-live-demo.sh` drives a real Docker/Podman stack and real service
health endpoints, neither of which exists in this environment (no daemon,
no running services — see test_tunnel_scripts.py for the same constraint on
the tunnel scripts). This test uses the same technique: fake `curl` and
`start-infrastructure.sh` on `PATH`/in place so the script's own health-check
and exit-code logic runs for real and is asserted for real, instead of
skipping the scenario entirely.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "infrastructure/scripts/start-live-demo.sh"
INFRA_SCRIPT = REPO_ROOT / "infrastructure/scripts/start-infrastructure.sh"

FAKE_INFRA = "#!/usr/bin/env bash\nexit 0\n"

# curl is invoked as: curl -s -o /dev/null -w "%{http_code}" -m 5 "$URL"
# The fake reads $FAKE_CURL_UNHEALTHY_MARKER and answers 500 for the one
# URL containing that marker (a port number), 200 for every other URL.
FAKE_CURL = """#!/usr/bin/env bash
url="${@: -1}"
if [ -n "${FAKE_CURL_UNHEALTHY_MARKER:-}" ] && [[ "$url" == *"$FAKE_CURL_UNHEALTHY_MARKER"* ]]; then
  echo -n "500"
else
  echo -n "200"
fi
"""


class StartLiveDemoHealthCheckTest(unittest.TestCase):
    """Runs the real start-live-demo.sh against faked infrastructure and curl."""

    def setUp(self):
        """Installs a fake `curl` on PATH and swaps in a no-op start-infrastructure.sh.

        Both originals are restored in `tearDown`, so a failing assertion
        still leaves the repo as it found it.
        """
        self._original_infra_script = INFRA_SCRIPT.read_text()
        self._original_infra_mode = INFRA_SCRIPT.stat().st_mode
        INFRA_SCRIPT.write_text(FAKE_INFRA)
        INFRA_SCRIPT.chmod(self._original_infra_mode)

        import tempfile
        self._tmp = tempfile.TemporaryDirectory()
        fake_curl = pathlib.Path(self._tmp.name) / "curl"
        fake_curl.write_text(FAKE_CURL)
        fake_curl.chmod(fake_curl.stat().st_mode | stat.S_IEXEC)
        self._env = dict(os.environ)
        self._env["PATH"] = f"{self._tmp.name}:{self._env['PATH']}"

    def tearDown(self):
        INFRA_SCRIPT.write_text(self._original_infra_script)
        INFRA_SCRIPT.chmod(self._original_infra_mode)
        self._tmp.cleanup()

    def test_exits_zero_when_every_service_is_healthy(self):
        """Given every health endpoint returns 200, the script exits 0.

        Confirms the happy path still succeeds after adding the failure
        exit path, so the fix does not turn a working demo run red.
        """
        env = dict(self._env)
        result = subprocess.run(
            ["bash", str(SCRIPT)], env=env, capture_output=True, text=True,
            cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("Live Demo Stack is Ready", result.stdout)

    def test_exits_nonzero_when_a_core_service_is_unhealthy(self):
        """Given one health endpoint fails, the script exits non-zero.

        Task #148's acceptance criterion states the script "returns
        non-zero exit code if any core service fails health checks". Before
        this fix, `ALL_HEALTHY` was set to `false` but never checked, so the
        script always exited 0 regardless of health-check results.
        """
        env = dict(self._env)
        env["FAKE_CURL_UNHEALTHY_MARKER"] = "8084"  # AI Service
        result = subprocess.run(
            ["bash", str(SCRIPT)], env=env, capture_output=True, text=True,
            cwd=REPO_ROOT,
        )
        self.assertNotEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("NOT fully healthy", result.stdout)


if __name__ == "__main__":
    unittest.main()
