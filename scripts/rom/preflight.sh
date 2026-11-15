#!/usr/bin/env bash
#
# Refuse to start a ROM build on a machine that cannot finish it.
#
source "$(dirname "$0")/common.sh"

: "${EXPERIENCE_MIN_DISK_GB:=350}"
: "${EXPERIENCE_MIN_MEM_GB:=30}"
: "${EXPERIENCE_MIN_CPUS:=8}"

mkdir -p "$EXPERIENCE_TOP"
fail=0
disk_gb=$(( $(df -Pk "$EXPERIENCE_TOP" | awk 'NR==2 {print $4}') / 1024 / 1024 ))
mem_gb=$(( ( $(awk '/MemTotal/ {print $2}' /proc/meminfo) + $(awk '/SwapTotal/ {print $2}' /proc/meminfo) ) / 1024 / 1024 ))
cpus=$(nproc)
# Space already used by an existing tree counts towards the requirement.
used_gb=0
[[ -d "$EXPERIENCE_TOP/.repo" ]] && used_gb=$(( $(du -sk "$EXPERIENCE_TOP" | cut -f1) / 1024 / 1024 ))

log "disk free ${disk_gb} GB (+${used_gb} GB existing tree), RAM+swap ${mem_gb} GB, CPUs ${cpus}"
(( disk_gb + used_gb >= EXPERIENCE_MIN_DISK_GB )) || { log "need >= ${EXPERIENCE_MIN_DISK_GB} GB disk"; fail=1; }
(( mem_gb >= EXPERIENCE_MIN_MEM_GB )) || { log "need >= ${EXPERIENCE_MIN_MEM_GB} GB RAM+swap"; fail=1; }
(( cpus >= EXPERIENCE_MIN_CPUS )) || { log "need >= ${EXPERIENCE_MIN_CPUS} CPUs"; fail=1; }
for tool in repo git python3 rsync debugfs xz java make; do
    command -v "$tool" >/dev/null || { log "missing tool: $tool (see scripts/rom/setup-build-host.sh)"; fail=1; }
done
[[ "$(uname -m)" == x86_64 ]] || { log "Android 11 host tools require an x86_64 build host"; fail=1; }
[[ $fail -eq 0 ]] || die "build host does not meet the requirements"
log "preflight OK"
