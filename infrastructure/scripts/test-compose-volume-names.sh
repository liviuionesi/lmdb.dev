#!/bin/bash
#
# Checks docker-compose.yml for two settings:
#
# 1. Postgres uses a fixed volume name, `lmdb_postgres_data`. Without one,
#    Compose names the volume after the folder (`docker_postgres_data`).
# 2. The bind mounts for the Postgres init scripts and the Vosk models carry
#    the `z` label, so containers can read them when SELinux is on.
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
