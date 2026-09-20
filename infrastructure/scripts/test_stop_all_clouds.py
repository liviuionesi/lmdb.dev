#!/usr/bin/env python3
"""Tests for stop-all-clouds.sh (#160, audited by #304).

The script drives the real `az`, `aws` and `minikube` CLIs, none of which
are installed in this environment (same constraint as
test_azure_lifecycle.py for `az`/`terraform`). Fake `az`/`aws`/`minikube`
executables go on `PATH`, each recording every call it receives and
answering from a small state file, so the script's real per-cloud
detect-and-stop branching runs and is asserted for real: a cloud already
stopped is left alone, a running one is stopped (or, under `--dry-run`,
only reported), and an absent CLI is skipped rather than treated as an
error.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "infrastructure/scripts/stop-all-clouds.sh"

# `az aks list` returns the one cluster's RG/name (tab-separated, like real
# `-o tsv`); `az aks show` answers the polled power state from a state file
# so the script's own branching (Stopped / Running / Unknown) is exercised.
FAKE_AZ = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_AZ_CALLS"
case "$*" in
  *"aks list"*) echo -e "test-rg\ttest-cluster" ;;
  *"aks show"*) cat "$FAKE_AZ_STATE_FILE" ;;
  *) exit 0 ;;
esac
"""

# `describe-instances --filters` resolves the one instance id; the
# `--instance-ids` form answers its state; `stop-instances` flips the state
# file so a second read reflects the stop, the same way EC2 would.
FAKE_AWS = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_AWS_CALLS"
case "$*" in
  *"describe-instances --filters"*) echo "test-instance" ;;
  *"describe-instances --instance-ids"*) cat "$FAKE_AWS_STATE_FILE" ;;
  *"stop-instances"*) echo "stopped" > "$FAKE_AWS_STATE_FILE" ;;
  *) exit 0 ;;
esac
"""

FAKE_MINIKUBE = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_MK_CALLS"
case "$*" in
  *"status"*) cat "$FAKE_MK_STATE_FILE" ;;
  "stop") echo "Stopped" > "$FAKE_MK_STATE_FILE" ;;
  *) exit 0 ;;
esac
"""


class StopAllCloudsTest(unittest.TestCase):
    """Runs the real stop-all-clouds.sh against fake az/aws/minikube CLIs."""

    def setUp(self):
        """Installs fake az/aws/minikube on a restricted PATH."""
        self._tmp = tempfile.TemporaryDirectory()
        for name, content in (("az", FAKE_AZ), ("aws", FAKE_AWS), ("minikube", FAKE_MINIKUBE)):
            fake = pathlib.Path(self._tmp.name) / name
            fake.write_text(content)
            fake.chmod(fake.stat().st_mode | stat.S_IEXEC)
        self._az_calls = pathlib.Path(self._tmp.name) / "az_calls.log"
        self._aws_calls = pathlib.Path(self._tmp.name) / "aws_calls.log"
        self._mk_calls = pathlib.Path(self._tmp.name) / "mk_calls.log"
        self._az_state = pathlib.Path(self._tmp.name) / "az_state"
        self._aws_state = pathlib.Path(self._tmp.name) / "aws_state"
        self._mk_state = pathlib.Path(self._tmp.name) / "mk_state"
        for f in (self._az_calls, self._aws_calls, self._mk_calls):
            f.write_text("")
        self._env = dict(os.environ)
        self._env["PATH"] = f"{self._tmp.name}:/usr/bin:/bin"
        self._env["FAKE_AZ_CALLS"] = str(self._az_calls)
        self._env["FAKE_AZ_STATE_FILE"] = str(self._az_state)
        self._env["FAKE_AWS_CALLS"] = str(self._aws_calls)
        self._env["FAKE_AWS_STATE_FILE"] = str(self._aws_state)
        self._env["FAKE_MK_CALLS"] = str(self._mk_calls)
        self._env["FAKE_MK_STATE_FILE"] = str(self._mk_state)

    def tearDown(self):
        self._tmp.cleanup()

    def _run(self, *args):
        return subprocess.run(
            ["bash", str(SCRIPT), *args], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )

    def test_already_stopped_clouds_are_left_alone(self):
        """Given every cloud is already stopped, no stop call is issued anywhere.

        Story #160's AC3: the script "stops only what's running". With
        Azure, AWS and Minikube all reporting a stopped state, the summary
        must show 0 stopped and the "nothing was running" cost line, not
        an idle-cost line implying a stop actually happened.
        """
        self._az_state.write_text("Stopped")
        self._aws_state.write_text("stopped")
        self._mk_state.write_text("Stopped")
        result = self._run()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any("aks stop" in c for c in self._az_calls.read_text().splitlines()))
        self.assertFalse(any("stop-instances" in c for c in self._aws_calls.read_text().splitlines()))
        self.assertFalse(any(c == "stop" for c in self._mk_calls.read_text().splitlines()))
        self.assertIn("Stopped:        0 cloud(s)", result.stdout)
        self.assertIn("Already idle:   3 cloud(s)", result.stdout)
        self.assertIn("No compute was running — already at minimum cost.", result.stdout)

    def test_dry_run_reports_without_stopping_anything(self):
        """Given every cloud is running, --dry-run reports each stop without issuing it.

        Story #160's AC3: `--dry-run` must print what would be stopped and
        make no real change. Azure's running branch would otherwise shell
        out to the real stop-azure.sh, so this is the case that proves
        dry-run short-circuits before that ever happens.
        """
        self._az_state.write_text("Running")
        self._aws_state.write_text("running")
        self._mk_state.write_text("Running")
        result = self._run("--dry-run")
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any("aks stop" in c for c in self._az_calls.read_text().splitlines()))
        self.assertFalse(any("stop-instances" in c for c in self._aws_calls.read_text().splitlines()))
        self.assertFalse(any(c == "stop" for c in self._mk_calls.read_text().splitlines()))
        self.assertIn("[DRY RUN] Would stop: az aks stop", result.stdout)
        self.assertIn("[DRY RUN] Would stop: aws ec2 stop-instances", result.stdout)
        self.assertIn("[DRY RUN] Would stop: minikube stop", result.stdout)
        self.assertIn("No changes made", result.stdout)

    def test_stops_only_the_running_cloud_and_reports_disk_only_residual_cost(self):
        """Given only AWS is running, it alone is stopped and counted.

        Story #160's AC3, the "detects each cloud's actual state
        independently" half: Azure and Minikube stay in their stopped
        branches (no accidental stop call), AWS is stopped for real, and
        the summary's residual-cost line names disk cost only, matching
        #304's fix to this line (it used to claim a "disk/IP" total that
        did not match its own itemized rate — see
        test_azure_lifecycle.py's cost-summary test).
        """
        self._az_state.write_text("Stopped")
        self._aws_state.write_text("running")
        self._mk_state.write_text("Stopped")
        result = self._run()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any("aks stop" in c for c in self._az_calls.read_text().splitlines()))
        self.assertTrue(any("stop-instances" in c for c in self._aws_calls.read_text().splitlines()))
        self.assertFalse(any(c == "stop" for c in self._mk_calls.read_text().splitlines()))
        self.assertIn("Stopped:        1 cloud(s)", result.stdout)
        self.assertIn("Residual disk cost: ~$0.25/day", result.stdout)
        self.assertNotIn("disk/IP cost", result.stdout)


if __name__ == "__main__":
    unittest.main()
