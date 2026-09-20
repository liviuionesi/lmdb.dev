#!/usr/bin/env python3
"""Tests for auto-stop-watchdog.sh (#157, audited by #303).

The real script drives the real `curl` against a live gateway and, on
timeout, the real `stop-azure.sh`/`stop-aws.sh` (which themselves need a
real cloud CLI — see test_azure_lifecycle.py/test_aws_lifecycle.py). Since
`SCRIPT_DIR` is resolved from the invoked script's own path, these tests
run a *copy* of the real watchdog script from a temp directory alongside
fake `stop-azure.sh`/`stop-aws.sh` siblings, so `TARGET_CLOUD=azure`/`aws`
call the fake sibling directly with no dependency on cloud CLIs. A fake
`curl` on `PATH` answers the two endpoints the script polls
(`/actuator/health`, `/actuator/activity`) from environment-controlled
values, so both the idle-threshold branch and the below-threshold branch
run for real.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import shutil
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
REAL_SCRIPT = REPO_ROOT / "infrastructure/scripts/auto-stop-watchdog.sh"

# The last argument to every curl call in the real script is the URL, so a
# fake curl can dispatch purely on the URL's suffix regardless of flag
# order.
FAKE_CURL = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_CURL_CALLS"
url="${@: -1}"
case "$url" in
  */actuator/health) printf '%s' "${FAKE_HEALTH_CODE:-200}" ;;
  */actuator/activity) printf '{"idleSeconds":%s}' "${FAKE_IDLE_SECONDS:-0}" ;;
  *) printf '{}' ;;
esac
"""

FAKE_STOP_SCRIPT = """#!/usr/bin/env bash
echo "$(basename "$0") called" >> "$STOP_CALLS_LOG"
"""


class AutoStopWatchdogTest(unittest.TestCase):
    """Runs a copy of auto-stop-watchdog.sh against fake curl and fake stop-*.sh siblings."""

    def setUp(self):
        """Copies the real script into a temp SCRIPT_DIR with fake stop-azure.sh/stop-aws.sh."""
        self._tmp = tempfile.TemporaryDirectory()
        self._script_dir = pathlib.Path(self._tmp.name)
        self._script = self._script_dir / "auto-stop-watchdog.sh"
        shutil.copy(REAL_SCRIPT, self._script)
        self._script.chmod(self._script.stat().st_mode | stat.S_IEXEC)

        self._stop_calls_log = self._script_dir / "stop-calls.log"
        self._stop_calls_log.write_text("")
        for name in ("stop-azure.sh", "stop-aws.sh"):
            fake = self._script_dir / name
            fake.write_text(FAKE_STOP_SCRIPT)
            fake.chmod(fake.stat().st_mode | stat.S_IEXEC)

        self._bin = pathlib.Path(self._tmp.name) / "bin"
        self._bin.mkdir()
        fake_curl = self._bin / "curl"
        fake_curl.write_text(FAKE_CURL)
        fake_curl.chmod(fake_curl.stat().st_mode | stat.S_IEXEC)
        self._curl_calls = self._bin / "curl-calls.log"
        self._curl_calls.write_text("")

        self._env = dict(os.environ)
        self._env["PATH"] = f"{self._bin}:/usr/bin:/bin"
        self._env["FAKE_CURL_CALLS"] = str(self._curl_calls)
        self._env["GATEWAY_URL"] = "https://gateway.test"
        self._env["STOP_CALLS_LOG"] = str(self._stop_calls_log)

    def tearDown(self):
        self._tmp.cleanup()

    def _run(self, **extra_env):
        env = dict(self._env, **extra_env)
        return subprocess.run(
            ["bash", str(self._script)], env=env, capture_output=True, text=True,
        )

    def test_stops_azure_when_idle_at_or_past_the_threshold(self):
        """Given idleSeconds >= 3600 and TARGET_CLOUD=azure, stop-azure.sh runs.

        Task #157's third criterion: the watchdog checks `/actuator/
        activity` and stops compute once idle time reaches the 3600s (1
        hour) threshold.
        """
        result = self._run(FAKE_IDLE_SECONDS="3600", TARGET_CLOUD="azure")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self._stop_calls_log.read_text().strip(), "stop-azure.sh called")

    def test_stops_aws_when_idle_past_the_threshold(self):
        """Given idleSeconds > 3600 and TARGET_CLOUD=aws, stop-aws.sh runs."""
        result = self._run(FAKE_IDLE_SECONDS="4000", TARGET_CLOUD="aws")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self._stop_calls_log.read_text().strip(), "stop-aws.sh called")

    def test_does_not_stop_when_idle_is_under_the_threshold(self):
        """Given idleSeconds below 3600, no stop script runs.

        Confirms the watchdog does not stop compute prematurely.
        """
        result = self._run(FAKE_IDLE_SECONDS="3599", TARGET_CLOUD="azure")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self._stop_calls_log.read_text(), "")
        self.assertIn("remaining until auto-sleep", result.stdout)

    def test_respects_a_custom_idle_threshold(self):
        """A custom IDLE_THRESHOLD_SECONDS overrides the 3600s default."""
        result = self._run(
            FAKE_IDLE_SECONDS="100", TARGET_CLOUD="azure", IDLE_THRESHOLD_SECONDS="60",
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self._stop_calls_log.read_text().strip(), "stop-azure.sh called")

    def test_does_nothing_when_backend_is_already_unreachable(self):
        """Given the health check fails, the watchdog exits without querying activity."""
        result = self._run(FAKE_HEALTH_CODE="000", TARGET_CLOUD="azure")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self._stop_calls_log.read_text(), "")
        self.assertFalse(any("/actuator/activity" in c
                             for c in self._curl_calls.read_text().splitlines()))


if __name__ == "__main__":
    unittest.main()
