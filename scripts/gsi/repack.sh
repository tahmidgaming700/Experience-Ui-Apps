#!/usr/bin/env bash
#
# Experience UI on a prebuilt GSI: sign apps, build overlays, repack, verify, package.
#
#   scripts/gsi/repack.sh <base-image.img[.xz]> [out-dir]
#
# Environment:
#   ANDROID_HOME              Android SDK (build-tools 35, platforms;android-30)
#   EXPERIENCE_BUILD_ID       UTC YYYYMMDDHHMM (default: now)
#   EXPERIENCE_BASE_LABEL     description of the base image for ro.experience.base
#   EXPERIENCE_EDITION        GApps (default) or Vanilla, matching the base image
#   EXPERIENCE_SKIP_GRADLE=1  use already built release APKs
#
# The base must be signed with the public AOSP test keys (as LineageOS unofficial GSIs
# such as AndyYan's are): the Experience apps are signed with the same platform key so
# they keep their system permissions. repack-gsi.py verifies this before changing anything.
#
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
BASE="${1:?usage: repack.sh <base-image> [out-dir]}"
OUT="${2:-$REPO/out/gsi}"
: "${ANDROID_HOME:?set ANDROID_HOME}"
: "${EXPERIENCE_BUILD_ID:=$(date -u +%Y%m%d%H%M)}"
: "${EXPERIENCE_EDITION:=GApps}"
BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
JAR="$ANDROID_HOME/platforms/android-30/android.jar"
[[ -f "$JAR" ]] || { echo "need platforms;android-30" >&2; exit 1; }
log() { printf '\033[1;34m[gsi]\033[0m %s\n' "$*" >&2; }

mkdir -p "$OUT"
KEYS="$OUT/keys"
if [[ ! -f "$KEYS/platform.pk8" ]]; then
    # Public AOSP test keys (build/make/target/product/security), as shipped in LineageOS 18.1.
    mkdir -p "$KEYS"
    base_url="https://raw.githubusercontent.com/LineageOS/android_build/lineage-18.1/target/product/security"
    curl -fsSL "$base_url/platform.pk8" -o "$KEYS/platform.pk8"
    curl -fsSL "$base_url/platform.x509.pem" -o "$KEYS/platform.x509.pem"
fi
fp="$(openssl x509 -in "$KEYS/platform.x509.pem" -noout -fingerprint -sha256 | cut -d= -f2 | tr -d : | tr A-F a-f)"
[[ "$fp" == c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8 ]] \
    || { echo "unexpected platform test certificate $fp" >&2; exit 1; }

log "release APKs"
EXPERIENCE_TOP="$OUT" EXPERIENCE_SKIP_GRADLE="${EXPERIENCE_SKIP_GRADLE:-0}" \
    bash "$REPO/scripts/rom/collect-app-prebuilts.sh" "$OUT/unsigned" >/dev/null
mkdir -p "$OUT/apks"
for d in "$OUT"/unsigned/*/; do
    m="$(basename "$d")"
    "$BT/zipalign" -p -f 4 "$d/$m.apk" "$OUT/apks/$m.aligned.apk"
    "$BT/apksigner" sign --v4-signing-enabled false --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" \
        --out "$OUT/apks/$m.apk" "$OUT/apks/$m.aligned.apk"
    rm "$OUT/apks/$m.aligned.apk"
done
log "signed $(ls "$OUT"/apks/*.apk | wc -l) apps with the platform key"

log "overlays"
drop=()
# A GApps base ships Google Messages, which supports MMS: keep it as the default SMS app.
[[ "$EXPERIENCE_EDITION" == GApps ]] && drop=(--drop config_defaultSms)
python3 -I "$REPO/scripts/gsi/build-overlays.py" --out "$OUT/overlays" --android-jar "$JAR" \
    --aapt2 "$BT/aapt2" --apksigner "$BT/apksigner" --zipalign "$BT/zipalign" \
    --key "$KEYS/platform.pk8" --cert "$KEYS/platform.x509.pem" "${drop[@]}" >/dev/null

log "repack"
python3 -I "$REPO/scripts/gsi/repack-gsi.py" --base "$BASE" --apks "$OUT/apks" --overlays "$OUT/overlays" \
    --platform-cert "$KEYS/platform.x509.pem" --apksigner "$BT/apksigner" --out "$OUT/image" \
    --build-id "$EXPERIENCE_BUILD_ID" --edition "$EXPERIENCE_EDITION" --base-label "${EXPERIENCE_BASE_LABEL:-}"

log "verify"
python3 -I "$REPO/scripts/rom/verify-system-image.py" "$OUT/image/system.img" --edition "$EXPERIENCE_EDITION" \
    --expect-build-number "EXP1.0.0.$EXPERIENCE_BUILD_ID.userdebug" --report "$OUT/image/verification.json"

log "package"
name="ExperienceUI-1.0.0-$EXPERIENCE_BUILD_ID-arm64_b$( [[ $EXPERIENCE_EDITION == GApps ]] && echo g || echo v )S-repack"
rel="$OUT/release"; rm -rf "$rel"; mkdir -p "$rel"
img_sha="$(sha256sum "$OUT/image/system.img" | cut -d' ' -f1)"
echo "$img_sha  $name.img" > "$rel/$name.img.sha256"
xz -T0 -6 -c "$OUT/image/system.img" > "$rel/$name.img.xz"
xz -t "$rel/$name.img.xz"
cp "$OUT/image/repack-manifest.json" "$OUT/image/verification.json" "$rel/"
( cd "$rel" && sha256sum ./*.img.xz ./*.json | sed 's# \./# #' > SHA256SUMS )
log "done: $rel"
ls -la "$rel" >&2
