#!/bin/bash
#
# Checks two things in docker-compose.yml that broke Postgres on a real
# machine (Fedora, rootless podman, SELinux enforcing):
#
# 1. Postgres keeps its own named volume. Compose prefixes a volume with the
#    folder name, which here is `docker`. Any other project that keeps its
#    compose file in a folder called `docker` then shares
#    `docker_postgres_data`, with its own data and admin password. That left
#    user-service, actor-service and ai-service unable to log in.
# 2. The bind mounts that containers must read carry the `z` label. Without
#    it SELinux denies the read: a fresh Postgres cannot read its init
#    scripts, and ai-service cannot read the Vosk models.
#
# Run: infrastructure/scripts/test-compose-volume-names.sh

COMPOSE="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docker" && pwd)/docker-compose.yml"
failures=0

# Prints PASS/FAIL for one check. $1 is the name, $2 is "true" or "false".
check() {
  if [ "$2" = "true" ]; then echo "PASS  $1"; else echo "FAIL  $1"; failures=$((failures + 1)); fi
}

# The `name:` set under the top-level `postgres_data:` volume, if any.
declared_name=$(awk '
  /^volumes:/ { in_volumes = 1; next }
  in_volumes && /^  postgres_data:/ { in_postgres = 1; next }
  in_postgres && /^  [a-z_]+:/ { exit }
  in_postgres && /^    name:/ { print $2; exit }
' "$COMPOSE")
check "postgres_data has the explicit name lmdb_postgres_data" \
  "$([ "$declared_name" = "lmdb_postgres_data" ] && echo true || echo false)"

# True when the mount line for $1 (a host path prefix) ends in options with z.
has_z_label() {
  grep -E "^\s+- $1:.*:[a-z,]*z[a-z,]*\s*$" "$COMPOSE" > /dev/null
}
check "the init-scripts mount has the z label" \
  "$(has_z_label '\./init-scripts' && echo true || echo false)"
check "the Vosk models mount has the z label" \
  "$(has_z_label '\./models' && echo true || echo false)"

[ "$failures" -eq 0 ]
