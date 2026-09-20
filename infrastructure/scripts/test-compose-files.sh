#!/bin/bash
#
# Tests compose-files.sh: docker-compose.gpu.yml is added exactly when an
# NVIDIA GPU can be passed into a container.
#
# Each case builds a small fake machine in a temp directory: a fake
# nvidia-smi, a fake ldconfig, a fake /dev and a fake driver library. PATH
# holds only symlinks to the few real tools the script needs, so this
# machine's own nvidia-smi is never used.
#
# Run: infrastructure/scripts/test-compose-files.sh

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

failures=0

# Prints PASS/FAIL for one check. $1 is the name, $2 is "true" or "false".
check() {
  if [ "$2" = "true" ]; then echo "PASS  $1"; else echo "FAIL  $1"; failures=$((failures + 1)); fi
}

# Builds a fake machine and prints "<status> <files> <version> <libdir>" as the
# script sees it. Options are environment variables:
#   HAS_SMI=0            no nvidia-smi
#   HAS_DEVICE=0         no /dev/nvidia0
#   LIB_VERSION=x        version of the libcuda file (default matches nvidia-smi)
#   LISTS_LIBCUDA=0      ldconfig does not list libcuda
run_case() {
  local root="$TMP/case-$RANDOM"
  mkdir -p "$root/bin" "$root/dev" "$root/lib"
  for tool in awk dirname readlink head sh bash; do
    ln -s "$(command -v "$tool")" "$root/bin/$tool"
  done

  if [ "${HAS_SMI:-1}" = "1" ]; then
    printf '#!/bin/sh\necho 615.71.09\n' > "$root/bin/nvidia-smi"
    chmod +x "$root/bin/nvidia-smi"
  fi
  [ "${HAS_DEVICE:-1}" = "1" ] && : > "$root/dev/nvidia0"

  : > "$root/lib/libcuda.so.${LIB_VERSION:-615.71.09}"
  ln -s "libcuda.so.${LIB_VERSION:-615.71.09}" "$root/lib/libcuda.so.1"
  if [ "${LISTS_LIBCUDA:-1}" = "1" ]; then
    printf '#!/bin/sh\necho "\\tlibcuda.so.1 (libc6,x86-64) => %s/libcuda.so.1"\n' "$root/lib" > "$root/bin/ldconfig"
  else
    printf '#!/bin/sh\necho "\\tlibother.so.1 (libc6,x86-64) => /usr/lib/libother.so.1"\n' > "$root/bin/ldconfig"
  fi
  chmod +x "$root/bin/ldconfig"

  PATH="$root/bin" NVIDIA_DEV_DIR="$root/dev" LDCONFIG="$root/bin/ldconfig" \
    "$root/bin/bash" -c "source '$SCRIPT_DIR/compose-files.sh'; echo \"\$GPU_STATUS|\$COMPOSE_FILES|\${NVIDIA_DRIVER_VERSION:-}|\${NVIDIA_LIB_DIR:-}\""
}

# A machine with everything in place uses the GPU overlay and passes the driver
# version and library folder to it.
out="$(run_case)"
check "adds the GPU file when the GPU is usable" \
  "$([[ "$out" == gpu\|*-f\ docker-compose.gpu.yml\|615.71.09\|*/lib ]] && echo true || echo false)"

# The base files are always kept.
check "keeps the app and ELK files" \
  "$([[ "$out" == *"-f docker-compose.yml -f docker-compose.elk.yml"* ]] && echo true || echo false)"

# Without nvidia-smi there is no GPU to use.
out="$(HAS_SMI=0 run_case)"
check "uses the CPU when nvidia-smi is missing" \
  "$([[ "$out" == cpu\|-f\ docker-compose.yml\ -f\ docker-compose.elk.yml\|\|  ]] && echo true || echo false)"

# The driver is installed but the device file is not there (module not loaded).
out="$(HAS_DEVICE=0 run_case)"
check "uses the CPU when /dev/nvidia0 is missing" \
  "$([[ "$out" == cpu\|* ]] && echo true || echo false)"

# The library on disk belongs to another driver version than nvidia-smi reports.
out="$(LIB_VERSION=999.0 run_case)"
check "uses the CPU when libcuda does not match the driver version" \
  "$([[ "$out" == cpu\|* ]] && echo true || echo false)"

# ldconfig does not know libcuda at all.
out="$(LISTS_LIBCUDA=0 run_case)"
check "uses the CPU when ldconfig does not list libcuda" \
  "$([[ "$out" == cpu\|* ]] && echo true || echo false)"

# The GPU overlay must not silently drop the GPU-only files when there is no GPU.
check "does not add the GPU file on a CPU-only machine" \
  "$([[ "$out" != *gpu.yml* ]] && echo true || echo false)"

[ "$failures" -eq 0 ]
