#!/bin/bash
#
# Chooses the Compose files for the local stack. Sourced by
# start-infrastructure.sh, stop-infrastructure.sh and logs.sh, so all three
# always see the same containers.
#
# Sets:
#   COMPOSE_FILES  the -f arguments. docker-compose.gpu.yml is added when
#                  detect_nvidia_gpu finds a usable GPU.
#   GPU_STATUS     "gpu" or "cpu", for the start script's summary.
#
# Usage: source "$(dirname "${BASH_SOURCE[0]}")/compose-files.sh"   (run from the docker directory)

# Succeeds when an NVIDIA GPU can be passed into a container. Exports
# NVIDIA_DRIVER_VERSION and NVIDIA_LIB_DIR for docker-compose.gpu.yml.
#
# It needs all of: nvidia-smi answering, the /dev/nvidia0 device file, and the
# driver's libcuda for that exact driver version.
#
# NVIDIA_DEV_DIR and LDCONFIG exist only so a test can replace /dev and ldconfig.
detect_nvidia_gpu() {
    command -v nvidia-smi > /dev/null 2>&1 || return 1

    local version
    version="$(nvidia-smi --query-gpu=driver_version --format=csv,noheader 2> /dev/null | head -n 1)"
    [ -n "$version" ] || return 1

    [ -e "${NVIDIA_DEV_DIR:-/dev}/nvidia0" ] || return 1

    local cuda_lib
    cuda_lib="$(${LDCONFIG:-ldconfig} -p 2> /dev/null | awk '/libcuda\.so\.1 / {print $NF; exit}')"
    [ -n "$cuda_lib" ] || return 1

    local lib_dir
    lib_dir="$(dirname "$(readlink -f "$cuda_lib")")"
    [ -e "$lib_dir/libcuda.so.$version" ] || return 1

    export NVIDIA_DRIVER_VERSION="$version"
    export NVIDIA_LIB_DIR="$lib_dir"
}

COMPOSE_FILES="-f docker-compose.yml -f docker-compose.elk.yml"
GPU_STATUS="cpu"
if detect_nvidia_gpu; then
    COMPOSE_FILES="$COMPOSE_FILES -f docker-compose.gpu.yml"
    GPU_STATUS="gpu"
fi
