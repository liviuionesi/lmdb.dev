#!/usr/bin/env python3
"""Tests for start-infrastructure.sh's --tunnel/--live flag (#147, audited by #302).

`start-infrastructure.sh` drives real Docker Compose and, when `--tunnel` is
given, the real `start-tunnel.sh` (which itself drives a real `cloudflared`
container). Neither a Compose daemon nor network egress to Cloudflare exists
in this environment (see test_tunnel_scripts.py for the same constraint).

As in test_tunnel_scripts.py, a fake `docker` executable goes on `PATH`
that records every call and answers just enough for the compose-detection
and tunnel-launch code paths to believe real infrastructure came up. `npm`
is excluded from `PATH` entirely (it lives under /opt/node22/bin outside
this test's restricted PATH) so the script's own "npm not found — skipping
frontend startup" branch runs, keeping the test focused on the tunnel flag
instead of a real frontend install.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "infrastructure/scripts/start-infrastructure.sh"
POINTER_FILE = REPO_ROOT / "infrastructure/tunnel-url.txt"
FAKE_LOG_LINE = (
    "INF |  Your quick Tunnel has been created! Visit it: "
    "https://fake-test-tunnel.trycloudflare.com |"
)

# Records every invocation, then answers just enough for both the compose
# detection/orchestration calls and the nested start-tunnel.sh's docker
# calls to succeed without a real daemon.
FAKE_DOCKER = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
case "$1" in
  logs) echo "{log_line}" ;;
  *) exit 0 ;;
esac
""".format(log_line=FAKE_LOG_LINE)


class StartInfrastructureTunnelFlagTest(unittest.TestCase):
    """Runs the real start-infrastructure.sh against a fake `docker`, no `npm`."""

    def setUp(self):
        """Installs the fake `docker`, restricts PATH to exclude npm, snapshots the pointer file."""
        self._original_pointer = POINTER_FILE.read_text()
        self._tmp = tempfile.TemporaryDirectory()
        fake_docker = pathlib.Path(self._tmp.name) / "docker"
        fake_docker.write_text(FAKE_DOCKER)
        fake_docker.chmod(fake_docker.stat().st_mode | stat.S_IEXEC)
        self._calls_file = pathlib.Path(self._tmp.name) / "calls.log"
        self._calls_file.write_text("")
        self._env = dict(os.environ)
        # /usr/bin and /bin carry curl, pgrep, sleep, cd builtins etc.; npm
        # lives outside both, so "command -v npm" genuinely fails here.
        self._env["PATH"] = f"{self._tmp.name}:/usr/bin:/bin"
        self._env["FAKE_DOCKER_CALLS"] = str(self._calls_file)

    def tearDown(self):
        POINTER_FILE.write_text(self._original_pointer)
        self._tmp.cleanup()

    def _calls(self):
        return self._calls_file.read_text().splitlines()

    def test_omitting_the_flag_does_not_launch_a_tunnel(self):
        """Given no --tunnel/--live flag, the tunnel container is never started.

        Task #147's third criterion: existing local dev workflows are
        preserved when the flag is omitted.
        """
        result = subprocess.run(
            ["bash", str(SCRIPT)], env=self._env, capture_output=True, text=True,
            cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any(c.startswith("run ") for c in self._calls()))
        self.assertNotIn("Cloudflare Tunnel is LIVE", result.stdout)

    def test_tunnel_flag_launches_the_tunnel_and_prints_the_public_url(self):
        """Given --tunnel, the tunnel container starts and the public URL prints.

        Task #147's first criterion: `start-infrastructure.sh --tunnel`
        launches the Cloudflare tunnel automatically and prints the public
        HTTPS URL, without a separate manual step.
        """
        result = subprocess.run(
            ["bash", str(SCRIPT), "--tunnel"], env=self._env,
            capture_output=True, text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(any(c.startswith("run ") for c in self._calls()))
        self.assertIn("Public URL: https://fake-test-tunnel.trycloudflare.com", result.stdout)

    def test_live_flag_is_an_alias_for_tunnel(self):
        """Given --live instead of --tunnel, the same tunnel launch runs.

        start-infrastructure.sh accepts either flag name interchangeably;
        this confirms --live is not dead code left over from a rename.
        """
        result = subprocess.run(
            ["bash", str(SCRIPT), "--live"], env=self._env,
            capture_output=True, text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(any(c.startswith("run ") for c in self._calls()))


if __name__ == "__main__":
    unittest.main()
