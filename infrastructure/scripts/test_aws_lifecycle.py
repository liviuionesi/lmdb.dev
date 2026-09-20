#!/usr/bin/env python3
"""Tests for start-aws.sh and stop-aws.sh (#157, audited by #303).

Both scripts drive the real `aws` CLI and `terraform`, neither installed in
this environment (same constraint as test_azure_lifecycle.py). A fake
`aws` executable goes on `PATH` that records every call and answers from a
small state file, so the real resume/pause branching runs for real:
resuming a stopped instance must call `aws ec2 start-instances`, pausing a
running one must call `aws ec2 stop-instances`, and neither script may call
`terraform destroy` (Task #157's "without destroying state" claim).
`AWS_INSTANCE_ID` is set so neither script needs `terraform output` to
resolve the instance, and `DUCKDNS_TOKEN` is left unset so the optional
DNS/SSH follow-up (needs a real reachable host) is skipped via the
scripts' own guard.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
START_SCRIPT = REPO_ROOT / "infrastructure/scripts/start-aws.sh"
STOP_SCRIPT = REPO_ROOT / "infrastructure/scripts/stop-aws.sh"

# Both scripts only ever call `describe-instances` (state/IP lookups),
# `start-instances`, `stop-instances`, and `wait`; state is tracked in a
# file so `describe-instances` reflects the transition immediately.
FAKE_AWS = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_AWS_CALLS"
case "$*" in
  *describe-instances*PublicIpAddress*) echo "203.0.113.10" ;;
  *describe-instances*) cat "$FAKE_AWS_STATE_FILE" ;;
  *start-instances*) echo "running" > "$FAKE_AWS_STATE_FILE" ;;
  *stop-instances*) echo "stopped" > "$FAKE_AWS_STATE_FILE" ;;
  *wait*) exit 0 ;;
  *) exit 0 ;;
esac
"""

FAKE_TERRAFORM = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_TERRAFORM_CALLS"
exit 0
"""


class AwsLifecycleTest(unittest.TestCase):
    """Runs the real start-aws.sh/stop-aws.sh against a fake `aws` and `terraform`."""

    def setUp(self):
        """Installs fake aws/terraform on PATH, DUCKDNS_TOKEN unset."""
        self._tmp = tempfile.TemporaryDirectory()
        for name, content in (("aws", FAKE_AWS), ("terraform", FAKE_TERRAFORM)):
            fake = pathlib.Path(self._tmp.name) / name
            fake.write_text(content)
            fake.chmod(fake.stat().st_mode | stat.S_IEXEC)
        self._aws_calls = pathlib.Path(self._tmp.name) / "aws_calls.log"
        self._tf_calls = pathlib.Path(self._tmp.name) / "tf_calls.log"
        self._state_file = pathlib.Path(self._tmp.name) / "state"
        self._aws_calls.write_text("")
        self._tf_calls.write_text("")
        self._env = dict(os.environ)
        self._env.pop("DUCKDNS_TOKEN", None)
        self._env["PATH"] = f"{self._tmp.name}:/usr/bin:/bin"
        self._env["FAKE_AWS_CALLS"] = str(self._aws_calls)
        self._env["FAKE_TERRAFORM_CALLS"] = str(self._tf_calls)
        self._env["FAKE_AWS_STATE_FILE"] = str(self._state_file)
        self._env["AWS_INSTANCE_ID"] = "i-0123456789abcdef0"

    def tearDown(self):
        self._tmp.cleanup()

    def _aws_calls_list(self):
        return self._aws_calls.read_text().splitlines()

    def _tf_calls_list(self):
        return self._tf_calls.read_text().splitlines()

    def test_start_resumes_a_stopped_instance(self):
        """Given a stopped instance, start-aws.sh resumes it via `start-instances`.

        Task #157's second criterion: AWS start/stop scripts resume and
        pause the EC2 k3s instance without destroying state — no
        `terraform destroy`/`apply` call on resume.
        """
        self._state_file.write_text("stopped")
        result = subprocess.run(
            ["bash", str(START_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(any("start-instances" in c for c in self._aws_calls_list()))
        self.assertFalse(any("apply" in c or "destroy" in c for c in self._tf_calls_list()))

    def test_start_is_a_no_op_when_already_running(self):
        """Given a running instance, start-aws.sh issues no start call."""
        self._state_file.write_text("running")
        result = subprocess.run(
            ["bash", str(START_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any("start-instances" in c for c in self._aws_calls_list()))
        self.assertIn("already running", result.stdout)

    def test_stop_pauses_a_running_instance_and_preserves_data(self):
        """Given a running instance, stop-aws.sh pauses it via `stop-instances`.

        Task #157's second criterion, stop half: no `terraform destroy`
        call, and the printed cost summary confirms the k3s EBS data
        volume is preserved rather than torn down.
        """
        self._state_file.write_text("running")
        result = subprocess.run(
            ["bash", str(STOP_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(any("stop-instances" in c for c in self._aws_calls_list()))
        self.assertFalse(any("destroy" in c for c in self._tf_calls_list()))
        self.assertIn("preserved on the EBS volume", result.stdout)

    def test_stop_is_a_no_op_when_already_stopped(self):
        """Given an already-stopped instance, stop-aws.sh issues no stop call."""
        self._state_file.write_text("stopped")
        result = subprocess.run(
            ["bash", str(STOP_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any("stop-instances" in c for c in self._aws_calls_list()))
        self.assertIn("already stopped", result.stdout)


if __name__ == "__main__":
    unittest.main()
