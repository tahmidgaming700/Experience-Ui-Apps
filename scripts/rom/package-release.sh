#!/usr/bin/env bash
#
# Package a built and verified system image as release assets.
#
#   package-release.sh   (reads $EXPERIENCE_OUT/dist, writes $EXPERIENCE_OUT/release)
#
# Outputs:
#   ExperienceUI-<ver>-<build id>-arm64_bvN-<variant>.img.xz   compressed system image
#   *.sha256 / SHA256SUMS                                       checksums (image is hashed
#                                                               uncompressed and compressed)
#   build-info.json                                             identity, pins, apps, verification
#   logs-<build id>.tar.xz                                      build log, verification report
#
# Refuses to package an image whose verification report is missing or failing.
#
source "$(dirname "$0")/common.sh"

DIST="$EXPERIENCE_OUT/dist"
REL="$EXPERIENCE_OUT/release"
REPORT="$DIST/verification.json"
[[ -f "$DIST/system.img" ]] || die "no $DIST/system.img"
[[ -f "$REPORT" ]] || die "no verification report; run verify-system-image.py first"
python3 -I -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1]))["ok"] else 1)' "$REPORT" \
    || die "verification failed; not packaging"

BUILD_ID="$(cat "$DIST/BUILD_ID")"
VERSION="$(sed -n 's/^experience.versionName=//p' "$EXPERIENCE_REPO/apps/experience-suite/gradle.properties")"
NAME="ExperienceUI-${VERSION}-${BUILD_ID}-arm64_bvN-${EXPERIENCE_VARIANT}"
rm -rf "$REL"; mkdir -p "$REL"

log "hashing and compressing system image"
IMG_SHA="$(sha256sum "$DIST/system.img" | cut -d' ' -f1)"
xz -T0 -6 -c "$DIST/system.img" > "$REL/$NAME.img.xz"
xz -t "$REL/$NAME.img.xz"
echo "$IMG_SHA  $NAME.img" > "$REL/$NAME.img.sha256"

python3 -I - "$REPORT" "$DIST" "$REL/build-info.json" "$NAME" "$IMG_SHA" "$EXPERIENCE_REPO" <<'PY'
import hashlib, json, os, re, subprocess, sys
report, dist, out, name, img_sha, repo = sys.argv[1:]
rep = json.load(open(report))
props = rep["info"]["properties"]
pins = dict(l.strip().split("=", 1) for l in open(os.path.join(repo, "rom/manifests/pins.env"))
            if "=" in l and not l.lstrip().startswith("#"))
apps = {}
prebuilts = os.path.join(os.environ.get("EXPERIENCE_TOP", ""), "vendor/experience/prebuilt/apps")
if os.path.isdir(prebuilts):
    for m in sorted(os.listdir(prebuilts)):
        apk = os.path.join(prebuilts, m, m + ".apk")
        if os.path.isfile(apk):
            apps[m] = hashlib.sha256(open(apk, "rb").read()).hexdigest()
info = {
    "product": "Experience UI",
    "version": props.get("ro.experience.version"),
    "display_version": props.get("ro.experience.display.version"),
    "build_number": props.get("ro.experience.build.number"),
    "display_id": props.get("ro.build.display.id"),
    "android_version": props.get("ro.build.version.release"),
    "sdk": props.get("ro.build.version.sdk"),
    "android_security_patch": props.get("ro.build.version.security_patch"),
    "fingerprint": props.get("ro.system.build.fingerprint") or props.get("ro.build.fingerprint"),
    "base": props.get("ro.experience.base"),
    "lineage_version": props.get("ro.lineage.version"),
    "edition": props.get("ro.experience.edition"),
    "target": "arm64 A/B system-as-root GSI (" + pins["EXPERIENCE_LUNCH"] + "-" + pins["EXPERIENCE_VARIANT"] + ")",
    "image": {"file": name + ".img.xz", "uncompressed_sha256": img_sha,
              "format": rep["info"].get("format"), "bytes": rep["info"].get("image_bytes"),
              "avb_footer": rep["info"].get("avb_footer")},
    "source_pins": {k: v for k, v in pins.items() if k.endswith(("_COMMIT", "_BRANCH", "_URL"))},
    "experience_commit": subprocess.run(["git", "-C", repo, "rev-parse", "HEAD"], capture_output=True, text=True).stdout.strip(),
    "experience_apps_sha256": apps,
    "verification": {"static_image_checks_passed": rep["ok"], "checks": len(rep["checks"])},
    "boot_tested": False,
    "boot_test_note": "Static image validation only. No boot test has been recorded for this build.",
}
json.dump(info, open(out, "w"), indent=2)
PY

( cd "$REL" && sha256sum ./*.img.xz build-info.json | sed 's# \./# #' > SHA256SUMS )
tar -C "$EXPERIENCE_OUT" -cJf "$REL/logs-$BUILD_ID.tar.xz" logs dist/verification.json \
    $( [[ -f "$DIST/experience-pinned-manifest.xml" ]] && echo dist/experience-pinned-manifest.xml )
log "release assets in $REL:"; ls -la "$REL" >&2
