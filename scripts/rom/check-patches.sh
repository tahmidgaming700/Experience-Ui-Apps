#!/usr/bin/env bash
#
# Verify, without a full source sync, that the patch stack applies to the projects
# Experience UI patches or overlays (frameworks/base, packages/apps/Settings,
# packages/apps/Trebuchet).
#
# Clones those LineageOS 18.1 projects shallowly (~2.5 GB) plus the pinned patch
# sets into a scratch "source tree" and runs apply-patches.sh --only on them.
# Used by CI on GitHub-hosted runners.
#
source "$(dirname "$0")/common.sh"

EXPERIENCE_TOP="${1:-$EXPERIENCE_OUT/patch-check}"
export EXPERIENCE_TOP
mkdir -p "$EXPERIENCE_TOP"

declare -A PROJECTS=(
    [frameworks/base]=LineageOS/android_frameworks_base
    [packages/apps/Settings]=LineageOS/android_packages_apps_Settings
    [packages/apps/Trebuchet]=LineageOS/android_packages_apps_Trebuchet
)
for path in "${!PROJECTS[@]}"; do
    if [[ ! -e "$EXPERIENCE_TOP/$path/.git" ]]; then
        log "clone ${PROJECTS[$path]} ($LINEAGE_BRANCH)"
        git clone -q --depth 1 -b "$LINEAGE_BRANCH" "https://github.com/${PROJECTS[$path]}" "$EXPERIENCE_TOP/$path"
    fi
    # Mark the unpatched revision once, so apply-patches.sh can reset to it on reruns.
    git -C "$EXPERIENCE_TOP/$path" rev-parse -q --verify refs/tags/experience-base >/dev/null \
        || git -C "$EXPERIENCE_TOP/$path" tag experience-base HEAD
    log "$path base $(git -C "$EXPERIENCE_TOP/$path" log -1 --format='%h %s' experience-base)"
done
fetch_commit "$LPU_URL" "$LPU_COMMIT" "$EXPERIENCE_TOP/lineage_patches_unified"

"$(dirname "$0")/apply-patches.sh" --only "${!PROJECTS[@]}"
log "patch stack OK"
