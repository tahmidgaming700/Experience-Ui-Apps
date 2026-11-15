#!/usr/bin/env bash
#
# Build the Experience UI system image in an already synced and patched tree.
#
#   EXPERIENCE_BUILD_ID=202611151530 scripts/rom/build.sh
#
# Produces $EXPERIENCE_OUT/dist/system.img (+ build.prop copies) and logs in
# $EXPERIENCE_OUT/logs. Any failing step aborts the build.
#
source "$(dirname "$0")/common.sh"

: "${EXPERIENCE_BUILD_ID:=$(date -u +%Y%m%d%H%M)}"
export EXPERIENCE_BUILD_ID
mkdir -p "$EXPERIENCE_OUT/dist" "$EXPERIENCE_OUT/logs"
LOG="$EXPERIENCE_OUT/logs/build-$EXPERIENCE_BUILD_ID.log"

cd "$EXPERIENCE_TOP" || die "cannot enter $EXPERIENCE_TOP"
[[ -f vendor/experience/prebuilt/apps/ExperienceLauncher/ExperienceLauncher.apk ]] \
    || die "Experience app prebuilts are missing (run collect-app-prebuilts.sh)"

# Same environment as the upstream buildbot.
export WITHOUT_CHECK_API=true
export LC_ALL=C
set +u  # envsetup.sh is not nounset-clean
# shellcheck disable=SC1091
source build/envsetup.sh
lunch "$EXPERIENCE_LUNCH-$EXPERIENCE_VARIANT"

log "building $EXPERIENCE_LUNCH-$EXPERIENCE_VARIANT (build id $EXPERIENCE_BUILD_ID), log: $LOG"
{
    m installclean
    m -j"${EXPERIENCE_BUILD_JOBS:-$(nproc)}" systemimage
    # Upstream GSI builds also compile the VNDK sepolicy compatibility test.
    m vndk-test-sepolicy
} 2>&1 | tee "$LOG"

OUT_DIR_PRODUCT="$(get_build_var PRODUCT_OUT)"
cp "$OUT_DIR_PRODUCT/system.img" "$EXPERIENCE_OUT/dist/system.img"
cp "$OUT_DIR_PRODUCT/system/build.prop" "$EXPERIENCE_OUT/dist/system-build.prop"
cp "$OUT_DIR_PRODUCT/system/product/build.prop" "$EXPERIENCE_OUT/dist/product-build.prop" 2>/dev/null || true
cp "$EXPERIENCE_TOP/experience-pinned-manifest.xml" "$EXPERIENCE_OUT/dist/" 2>/dev/null || true
echo "$EXPERIENCE_BUILD_ID" > "$EXPERIENCE_OUT/dist/BUILD_ID"
log "system image: $EXPERIENCE_OUT/dist/system.img ($(du -h "$EXPERIENCE_OUT/dist/system.img" | cut -f1))"
