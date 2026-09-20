#!/bin/bash
#
# Checks docker-compose files for settings:
#
# 1. All named volumes use explicit names starting with `lmdb_`. Without one,
#    Compose names volumes after the directory (`docker_<volume_name>`), which
#    collides across projects sharing common folder names (e.g. `docker/`).
# 2. The bind mounts for the Postgres init scripts and the Vosk models carry
#    the `z` label, so containers can read them when SELinux is on.
#
# Run: infrastructure/scripts/test-compose-volume-names.sh

DOCKER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../docker" && pwd)"
COMPOSE="$DOCKER_DIR/docker-compose.yml"
COMPOSE_ELK="$DOCKER_DIR/docker-compose.elk.yml"
failures=0

# Prints PASS/FAIL for one check. $1 is the name, $2 is "true" or "false".
check() {
  if [ "$2" = "true" ]; then echo "PASS  $1"; else echo "FAIL  $1"; failures=$((failures + 1)); fi
}

check_file_volumes() {
  local file="$1"
  local rel_file="$(basename "$file")"
  
  # Parse volume declarations under `volumes:` section
  local vols
  vols=$(awk '
    /^volumes:/ { in_vols = 1; next }
    in_vols && /^networks:/ { exit }
    in_vols && /^  [a-zA-Z0-9_-]+:/ {
      vol = $1;
      gsub(/^  /, "", vol);
      gsub(/:$/, "", vol);
      print vol;
    }
  ' "$file")

  for vol in $vols; do
    local declared_name
    declared_name=$(awk -v target="$vol" '
      /^volumes:/ { in_vols = 1; next }
      in_vols && /^networks:/ { exit }
      in_vols && $1 == target ":" { in_target = 1; next }
      in_target && /^  [a-zA-Z0-9_-]+:/ { exit }
      in_target && /^    name:/ { print $2; exit }
    ' "$file")

    check "$rel_file: volume $vol has explicit name lmdb_$vol" \
      "$([ "$declared_name" = "lmdb_$vol" ] && echo true || echo false)"
  done
}

check_file_volumes "$COMPOSE"
check_file_volumes "$COMPOSE_ELK"

# True when the mount line for $1 (a host path prefix) ends in options with z.
has_z_label() {
  grep -E "^\s+- $1:.*:[a-z,]*z[a-z,]*\s*$" "$COMPOSE" > /dev/null
}
check "the init-scripts mount has the z label" \
  "$(has_z_label '\./init-scripts' && echo true || echo false)"
check "the Vosk models mount has the z label" \
  "$(has_z_label '\./models' && echo true || echo false)"

[ "$failures" -eq 0 ]

