#!/usr/bin/env python3
"""Tests for the local Cloudflare Tunnel manifests (#142/#143).

Covers `infrastructure/docker/docker-compose.tunnel.yml` and
`infrastructure/kubernetes/overlays/local/cloudflare-tunnel.yaml`: both
point the `cloudflared` container at the internal API Gateway rather than
at a hardcoded public address, so a Vercel-deployed frontend can reach a
local or Minikube backend over public HTTPS.

No YAML library: every other Python script in this repo (`audit-check.py`,
`test_audit_check.py`) is stdlib-only,
and there is no `requirements.txt` anywhere to declare a dependency in.
Both manifests are small and flat enough to check with plain string and
regex matching instead.

No cluster or running daemon is required: the Docker Compose check calls
`docker compose config`, which only parses and merges YAML. It skips
(rather than fails) when the `docker compose` plugin itself is not
available, since that is an environment gap, not a manifest defect —
checking only that `docker` is on PATH would not catch an installation
that has the `docker` binary but not the Compose v2 plugin.

Run: python3 -m unittest discover -s infrastructure/scripts -p 'test_*.py'
"""
import pathlib
import re
import subprocess
import unittest

REPO_ROOT = pathlib.Path(__file__).resolve().parents[2]
COMPOSE_BASE = REPO_ROOT / "infrastructure/docker/docker-compose.yml"
COMPOSE_TUNNEL = REPO_ROOT / "infrastructure/docker/docker-compose.tunnel.yml"
K8S_TUNNEL = REPO_ROOT / "infrastructure/kubernetes/overlays/local/cloudflare-tunnel.yaml"
CLOUDFLARED_IMAGE = "docker.io/cloudflare/cloudflared:latest"
GATEWAY_URL = "http://api-gateway:8080"


def _docker_compose_available():
    """True if the `docker compose` (v2) plugin actually runs, not just `docker`."""
    try:
        return subprocess.run(
            ["docker", "compose", "version"], capture_output=True,
        ).returncode == 0
    except FileNotFoundError:
        return False


class DockerComposeTunnelTest(unittest.TestCase):
    """Checks `docker-compose.tunnel.yml`, the Docker Compose half of #143."""

    def test_service_targets_internal_gateway(self):
        """The compose file's own command line names the gateway, unmerged.

        Given the raw override file, when its `cloudflare-tunnel` service is
        read, then its image is the official `cloudflared` image and its
        `command` targets `api-gateway:8080` rather than a public host, so
        the tunnel forwards to the in-network gateway instead of leaking
        outside the compose network.
        """
        text = COMPOSE_TUNNEL.read_text()
        self.assertIn(f"image: {CLOUDFLARED_IMAGE}", text)
        self.assertRegex(
            text, r"command:\s*tunnel --no-autoupdate --url " + re.escape(GATEWAY_URL))

    @unittest.skipUnless(_docker_compose_available(), "docker compose plugin not available")
    def test_merges_cleanly_with_the_base_compose_file(self):
        """`docker compose config` resolves the override against the base file.

        Given the base compose file (which defines `lmdb-network` and the
        `api-gateway` service) and the tunnel override, when merged with
        `docker compose config`, then the result is valid Compose YAML and
        keeps `cloudflare-tunnel` wired to `api-gateway` and `lmdb-network`.
        Applying `docker-compose.tunnel.yml` on its own fails this check,
        because it depends on definitions that only exist in the base file.
        """
        result = subprocess.run(
            ["docker", "compose", "-f", str(COMPOSE_BASE), "-f", str(COMPOSE_TUNNEL), "config"],
            capture_output=True, text=True, cwd=REPO_ROOT,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        service = re.search(r"^  cloudflare-tunnel:\n(.*?)(?=^  \S|\Z)",
                             result.stdout, re.M | re.S)
        self.assertIsNotNone(service, "cloudflare-tunnel service missing from merged config")
        self.assertIn("lmdb-network", service.group(1))
        self.assertIn("api-gateway", service.group(1))


class KubernetesTunnelManifestTest(unittest.TestCase):
    """Checks `cloudflare-tunnel.yaml`, the Kubernetes half of #143."""

    def test_deployment_targets_internal_gateway_service(self):
        """The Deployment's container args name the in-cluster gateway.

        Given the manifest, when its `kind` and container spec are read,
        then it defines a `Deployment` running the same `cloudflared` image
        as the Compose override, with `--url http://api-gateway:8080` in
        its args, so the tunnel reaches the gateway through the
        cluster-internal Service name rather than a NodePort or external
        address.
        """
        text = K8S_TUNNEL.read_text()
        self.assertRegex(text, re.compile(r"^kind:\s*Deployment\s*$", re.M))
        self.assertIn(f"image: {CLOUDFLARED_IMAGE}", text)
        args_block = re.search(r"args:\n((?:\s+-.*\n)+)", text)
        self.assertIsNotNone(args_block, "container args block not found")
        args = [line.strip("- \n") for line in args_block.group(1).splitlines()]
        self.assertIn(GATEWAY_URL, args)


if __name__ == "__main__":
    unittest.main()
