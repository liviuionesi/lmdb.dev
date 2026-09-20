#!/usr/bin/env python3
"""Test for stop-infrastructure.sh's tunnel teardown (#147, audited by #302).

`stop-infrastructure.sh` drives real Docker Compose and the real
`stop-tunnel.sh`. As in test_tunnel_scripts.py and
test_start_infrastructure_tunnel_flag.py, a fake `docker` executable
records every call and answers success, so the real script logic — that
stopping the stack always tears down the tunnel container alongside it —
runs for real without a Compose daemon.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "infrastructure/scripts/stop-infrastructure.sh"
CONTAINER_NAME = "lmdb-cloudflare-tunnel"

FAKE_DOCKER = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_DOCKER_CALLS"
exit 0
"""


class StopInfrastructureTunnelTeardownTest(unittest.TestCase):
    """Runs the real stop-infrastructure.sh against a fake `docker`."""

    def setUp(self):
        """Installs the fake `docker` on PATH ahead of the real one."""
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
        self._tmp.cleanup()

    def test_stopping_the_stack_also_tears_down_the_tunnel_container(self):
        """Given the stack is stopped, the tunnel container is stopped and removed too.

        Task #147's second criterion: `stop-infrastructure.sh` detects and
        terminates a running tunnel container automatically, with no
        separate `stop-tunnel.sh` invocation required.
        """
        result = subprocess.run(
            ["bash", str(SCRIPT)], env=self._env, capture_output=True, text=True,
            cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        calls = self._calls_file.read_text().splitlines()
        stop_call = next((c for c in calls if c.startswith("stop ")), None)
        rm_call = next((c for c in calls if c.startswith("rm ")), None)
        self.assertIsNotNone(stop_call, "stop-infrastructure.sh never called `docker stop`")
        self.assertIsNotNone(rm_call, "stop-infrastructure.sh never called `docker rm`")
        self.assertIn(CONTAINER_NAME, stop_call)
        self.assertIn(CONTAINER_NAME, rm_call)


if __name__ == "__main__":
    unittest.main()
