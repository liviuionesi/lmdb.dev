#!/usr/bin/env python3
"""Tests for start-azure.sh and stop-azure.sh (#157, audited by #303).

Both scripts drive the real `az` CLI and `terraform`, neither of which is
installed in this environment (same constraint documented in
test_tunnel_scripts.py and test_start_infrastructure_tunnel_flag.py for
`docker`). A fake `az` executable goes on `PATH` that records every call
and answers from a small state file, so the real resume/pause branching in
each script runs and is asserted for real: resuming a stopped cluster must
call `az aks start`, pausing a running one must call `az aks stop`, and
neither script may fall through to `terraform apply` (which would
re-provision instead of resuming/pausing — Task #157's "without destroying
state" claim). `AZURE_RESOURCE_GROUP`/`AZURE_CLUSTER_NAME` are set so
neither script needs `terraform output` to resolve the cluster, and
`kubectl` is excluded from `PATH` so the post-resume Kubernetes rollout
block (which needs a real cluster) is skipped via the scripts' own
"kubectl not found" branch.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import os
import pathlib
import re
import stat
import subprocess
import tempfile
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
START_SCRIPT = REPO_ROOT / "infrastructure/scripts/start-azure.sh"
STOP_SCRIPT = REPO_ROOT / "infrastructure/scripts/stop-azure.sh"

# `$1 $2` is always the az subcommand ("aks show" / "aks start" / "aks
# stop"); state is tracked in a file so the scripts' own polling loops see
# the transition immediately instead of sleeping through a real 300s
# timeout.
FAKE_AZ = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_AZ_CALLS"
case "$1 $2" in
  "aks show") cat "$FAKE_AZ_STATE_FILE" ;;
  "aks start") echo "Running" > "$FAKE_AZ_STATE_FILE" ;;
  "aks stop") echo "Stopped" > "$FAKE_AZ_STATE_FILE" ;;
  *) exit 0 ;;
esac
"""

# Only `init` is ever reached (env vars supply RG/cluster name, and the
# fake az state keeps CURRENT_STATE out of the "provision from scratch"
# branch), but every subcommand is logged so a stray `apply`/`destroy`
# call is visible to assertions.
FAKE_TERRAFORM = """#!/usr/bin/env bash
echo "$*" >> "$FAKE_TERRAFORM_CALLS"
exit 0
"""


class AzureLifecycleTest(unittest.TestCase):
    """Runs the real start-azure.sh/stop-azure.sh against a fake `az` and `terraform`."""

    def setUp(self):
        """Installs fake az/terraform on a restricted PATH, without kubectl."""
        self._tmp = tempfile.TemporaryDirectory()
        for name, content in (("az", FAKE_AZ), ("terraform", FAKE_TERRAFORM)):
            fake = pathlib.Path(self._tmp.name) / name
            fake.write_text(content)
            fake.chmod(fake.stat().st_mode | stat.S_IEXEC)
        self._az_calls = pathlib.Path(self._tmp.name) / "az_calls.log"
        self._tf_calls = pathlib.Path(self._tmp.name) / "tf_calls.log"
        self._state_file = pathlib.Path(self._tmp.name) / "state"
        self._az_calls.write_text("")
        self._tf_calls.write_text("")
        self._env = dict(os.environ)
        # /usr/bin and /bin carry sed, cat, sleep etc.; kubectl is not
        # installed in this environment (confirmed absent from both), so
        # `command -v kubectl` genuinely fails and the rollout block skips.
        self._env["PATH"] = f"{self._tmp.name}:/usr/bin:/bin"
        self._env["FAKE_AZ_CALLS"] = str(self._az_calls)
        self._env["FAKE_TERRAFORM_CALLS"] = str(self._tf_calls)
        self._env["FAKE_AZ_STATE_FILE"] = str(self._state_file)
        self._env["AZURE_RESOURCE_GROUP"] = "test-rg"
        self._env["AZURE_CLUSTER_NAME"] = "test-cluster"

    def tearDown(self):
        self._tmp.cleanup()

    def _az_calls_list(self):
        return self._az_calls.read_text().splitlines()

    def _tf_calls_list(self):
        return self._tf_calls.read_text().splitlines()

    def test_start_resumes_a_stopped_cluster_without_reprovisioning(self):
        """Given a Stopped cluster, start-azure.sh resumes it via `az aks start`.

        Task #157's first criterion: Azure start/stop scripts resume and
        pause AKS compute nodes without destroying state. Resuming must
        never fall through to `terraform apply`, which would re-provision
        the cluster instead of waking the existing one.
        """
        self._state_file.write_text("Stopped")
        result = subprocess.run(
            ["bash", str(START_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(any(c.startswith("aks start") for c in self._az_calls_list()))
        self.assertFalse(any(c.startswith("apply") for c in self._tf_calls_list()))

    def test_start_is_a_no_op_when_already_running(self):
        """Given a Running cluster, start-azure.sh issues no start/stop/apply call."""
        self._state_file.write_text("Running")
        result = subprocess.run(
            ["bash", str(START_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any(c.startswith("aks start") for c in self._az_calls_list()))
        self.assertFalse(any(c.startswith("apply") for c in self._tf_calls_list()))
        self.assertIn("already Running", result.stdout)

    def test_stop_pauses_a_running_cluster_and_preserves_data(self):
        """Given a Running cluster, stop-azure.sh pauses it via `az aks stop`.

        Task #157's first criterion, stop half: the cluster is paused, not
        destroyed — no terraform `destroy` call, and the script's own
        printed cost summary confirms PVC data (Postgres/MongoDB/Redis/
        Ollama) is preserved rather than torn down.
        """
        self._state_file.write_text("Running")
        result = subprocess.run(
            ["bash", str(STOP_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(any(c.startswith("aks stop") for c in self._az_calls_list()))
        self.assertFalse(any("destroy" in c for c in self._tf_calls_list()))
        self.assertIn("preserved on PVCs", result.stdout)

    def test_stop_is_a_no_op_when_already_stopped(self):
        """Given an already-Stopped cluster, stop-azure.sh issues no stop call."""
        self._state_file.write_text("Stopped")
        result = subprocess.run(
            ["bash", str(STOP_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(any(c.startswith("aks stop") for c in self._az_calls_list()))
        self.assertIn("already Stopped", result.stdout)

    def test_cost_summary_total_matches_its_own_itemized_rate(self):
        """The printed "Total idle cost" must equal the itemized hourly rate x 24.

        Story #160's AC1 claims an "itemized, internally-consistent cost
        summary". Audited by #304: the summary used to itemize both an
        Azure Disk PVC rate and a separate Static Public IP rate, then
        print a Total that only reflected the disk rate (ADR-018's
        decision table always priced the stopped state as disk-only) —
        stated one cost, itemized a second one adding up to something
        else. This locks the invariant so the total can never again
        silently drift from what the summary itemizes above it.
        """
        self._state_file.write_text("Running")
        result = subprocess.run(
            ["bash", str(STOP_SCRIPT)], env=self._env, capture_output=True,
            text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

        disk_rate = float(re.search(r"Azure Disk PVCs[^\n]*\$([\d.]+)/hr", result.stdout).group(1))
        total_daily = float(re.search(r"Total idle cost:\s*~\$([\d.]+)/day", result.stdout).group(1))
        self.assertAlmostEqual(disk_rate * 24, total_daily, delta=0.015)
        self.assertNotIn("Static Public IP", result.stdout)


if __name__ == "__main__":
    unittest.main()
