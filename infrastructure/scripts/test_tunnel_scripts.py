#!/usr/bin/env python3
"""Tests for start-tunnel.sh and stop-tunnel.sh (#144).

Both scripts drive a real `docker`/`podman` CLI and a real Cloudflare
Tunnel, neither of which exists in this environment (no daemon, no network
egress to Cloudflare — see #250 and #229 for the same constraint elsewhere
in this repo). Rather than skip the scripts entirely, these tests put a
fake `docker` executable on `PATH` that records what it was called with and
returns canned output, so the scripts' own logic — URL extraction from
container logs, pointer-file publishing, and container teardown — runs for
real and is asserted for real.

`infrastructure/tunnel-url.txt` is a tracked file that `start-tunnel.sh`
overwrites as a side effect. Each test restores its original content, so a
test run leaves no diff in a clean checkout.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
START_SCRIPT = REPO_ROOT / "infrastructure/scripts/start-tunnel.sh"
STOP_SCRIPT = REPO_ROOT / "infrastructure/scripts/stop-tunnel.sh"
POINTER_FILE = REPO_ROOT / "infrastructure/tunnel-url.txt"
CONTAINER_NAME = "lmdb-cloudflare-tunnel"
FAKE_LOG_LINE = (
    "INF |  Your quick Tunnel has been created! Visit it: "
    "https://fake-test-tunnel.trycloudflare.com |"
)

FAKE_DOCKER = """#!/usr/bin/env bash
# Records every invocation, then answers just enough to let
# start-tunnel.sh/stop-tunnel.sh believe a real container came up.
echo "$*" >> "$FAKE_DOCKER_CALLS"
case "$1" in
  logs) echo "{log_line}" ;;
  *) exit 0 ;;
esac
""".format(log_line=FAKE_LOG_LINE)


class TunnelScriptTest(unittest.TestCase):
    """Runs the real scripts against a fake `docker` on PATH."""

    def setUp(self):
        """Installs the fake `docker` on PATH and snapshots the pointer file.

        `tempfile.TemporaryDirectory` and the pointer-file backup are both
        cleaned up in `tearDown`, so a failing assertion still leaves the
        repo as it found it.
        """
        self._original_pointer = POINTER_FILE.read_text()
        self._tmp = tempfile.TemporaryDirectory()
        fake_docker = pathlib.Path(self._tmp.name) / "docker"
        fake_docker.write_text(FAKE_DOCKER)
        fake_docker.chmod(fake_docker.stat().st_mode | stat.S_IEXEC)
        self._calls_file = pathlib.Path(self._tmp.name) / "calls.log"
        self._calls_file.write_text("")
        self._env = dict(os.environ)
        self._env["PATH"] = f"{self._tmp.name}:{self._env['PATH']}"
        self._env["FAKE_DOCKER_CALLS"] = str(self._calls_file)

    def tearDown(self):
        POINTER_FILE.write_text(self._original_pointer)
        self._tmp.cleanup()

    def _calls(self):
        return self._calls_file.read_text().splitlines()

    def test_start_tunnel_publishes_the_extracted_public_url(self):
        """`start-tunnel.sh` extracts the URL and writes the pointer file.

        Given a `cloudflared` container whose logs announce a
        `trycloudflare.com` URL, when `start-tunnel.sh` runs, then it starts
        the container without requesting elevated privileges (no
        `--privileged`, no bind-mounted Docker socket) and writes that exact
        URL to `infrastructure/tunnel-url.txt` (`apiUrl.js`'s
        `fetchPublishedTunnelUrl` reads this file), instead of leaving the
        previous run's stale URL in place.
        """
        result = subprocess.run(
            ["bash", str(START_SCRIPT)], env=self._env,
            capture_output=True, text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        run_call = next(c for c in self._calls() if c.startswith("run "))
        self.assertNotIn("--privileged", run_call)
        self.assertNotIn("/var/run/docker.sock", run_call)
        self.assertEqual(
            POINTER_FILE.read_text().strip(),
            "https://fake-test-tunnel.trycloudflare.com",
        )

    def test_stop_tunnel_stops_then_removes_the_named_container(self):
        """`stop-tunnel.sh` tears down the exact container `start-tunnel.sh` named.

        Given a running tunnel container, when `stop-tunnel.sh` runs, then
        it calls `docker stop` followed by `docker rm -f` on
        `lmdb-cloudflare-tunnel`, so a second `start-tunnel.sh` run does not
        collide with a container of the same name still running.
        """
        result = subprocess.run(
            ["bash", str(STOP_SCRIPT)], env=self._env,
            capture_output=True, text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        calls = self._calls()
        stop_call = next(c for c in calls if c.startswith("stop "))
        rm_call = next(c for c in calls if c.startswith("rm "))
        self.assertIn(CONTAINER_NAME, stop_call)
        self.assertIn(CONTAINER_NAME, rm_call)
        self.assertLess(calls.index(stop_call), calls.index(rm_call))


if __name__ == "__main__":
    unittest.main()
