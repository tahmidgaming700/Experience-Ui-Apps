#!/usr/bin/env bash
#
# Create or update the Experience UI Android 11 source tree in $EXPERIENCE_TOP.
#
#   EXPERIENCE_TOP=/srv/experience-src scripts/rom/sync.sh
#
# Needs: repo, git, ~120 GB free for a shallow LineageOS 18.1 checkout
# (plus ~150 GB for the build output). Every input is pinned in rom/manifests/pins.env
# and rom/manifests/experience.xml; the exact revisions of all ~800 projects are
# recorded in $EXPERIENCE_TOP/experience-pinned-manifest.xml after each sync.
#
source "$(dirname "$0")/common.sh"

command -v repo >/dev/null || die "repo is not installed (https://gerrit.googlesource.com/git-repo)"
mkdir -p "$EXPERIENCE_TOP"
cd "$EXPERIENCE_TOP" || die "cannot enter $EXPERIENCE_TOP"

if [[ ! -d .repo ]]; then
    log "repo init $LINEAGE_MANIFEST_URL ($LINEAGE_BRANCH)"
    repo init -q -u "$LINEAGE_MANIFEST_URL" -b "$LINEAGE_BRANCH" --depth=1 --git-lfs
fi

mkdir -p .repo/local_manifests
cp "$EXPERIENCE_REPO/rom/manifests/experience.xml" .repo/local_manifests/experience.xml

log "repo sync (this takes a while)"
# -d detaches every project at its manifest revision, discarding earlier patches.
repo sync -c -d -j"${EXPERIENCE_SYNC_JOBS:-$(nproc)}" --force-sync --no-clone-bundle --no-tags --fail-fast

log "fetch pinned GSI helpers and patch sets"
fetch_commit "$LBU_URL" "$LBU_COMMIT" "$EXPERIENCE_TOP/lineage_build_unified"
fetch_commit "$LPU_URL" "$LPU_COMMIT" "$EXPERIENCE_TOP/lineage_patches_unified"

log "install device/experience and vendor/experience"
mkdir -p device/experience vendor/experience
rsync -a --delete "$EXPERIENCE_REPO/rom/device/experience/" device/experience/
# Keep collected app prebuilts across re-syncs; everything else mirrors the repo.
rsync -a --delete --filter='P prebuilt/apps/*/' "$EXPERIENCE_REPO/rom/vendor/experience/" vendor/experience/

repo manifest -r -o "$EXPERIENCE_TOP/experience-pinned-manifest.xml"
log "source tree ready: $EXPERIENCE_TOP"
