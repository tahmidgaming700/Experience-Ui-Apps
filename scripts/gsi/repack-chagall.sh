#!/usr/bin/env bash
#
# Rebuild the Experience UI chagalllte device package and the Experience TWRP, from
# the two public source images, using the committed repack scripts. Reproduces exactly
# what was done by hand for the Galaxy Tab S 10.5 (chagalllte / SM-T805).
#
#   BASE_URL=<drive download url for lineage-18.1-...-chagalllte.zip> \
#   scripts/gsi/repack-chagall.sh <out-dir>
#
# Env:
#   BASE_URL        required: a direct download URL for the LineageOS 18.1 chagalllte zip
#   BASE_SHA256     expected sha256 of that zip (default: the 20221230 build)
#   TWRP_URL        official TWRP image URL (default: 3.7.0_9-0)
#   TWRP_SHA256     expected sha256 of the TWRP image
#   ANDROID_HOME    Android SDK (build-tools 35, platforms android-30 + android-35)
#   EXPERIENCE_BUILD_ID   UTC YYYYMMDDHHMM (default: now)
#
set -euo pipefail
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${1:?usage: repack-chagall.sh <out-dir>}"; mkdir -p "$OUT"
: "${BASE_URL:?set BASE_URL to the LineageOS chagalllte zip download URL}"
: "${BASE_SHA256:=ea4d14af1c52aa239a76ef1da0eb969231511177fb34f0373d8d622f5a7d19fe}"
: "${TWRP_URL:=https://eu.dl.twrp.me/chagalllte/twrp-3.7.0_9-0-chagalllte.img}"
: "${TWRP_SHA256:=be3cf1fa236f3e00067a70b8852c13a3bdde356cbe83d18f9a09f0ffe34b1538}"
: "${ANDROID_HOME:?set ANDROID_HOME}"
: "${EXPERIENCE_BUILD_ID:=$(date -u +%Y%m%d%H%M)}"
BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
JAR="$ANDROID_HOME/platforms/android-30/android.jar"
PY311="$(command -v python3.11 || true)"   # img2sdat needs a python with `imp`; else common.py is stubbed
log(){ printf '\033[1;34m[chagall]\033[0m %s\n' "$*" >&2; }

work="$OUT/work"; tools="$OUT/tools"; keys="$OUT/keys"; mkdir -p "$work" "$tools" "$keys"

log "fetch AOSP keys (platform = app signing, testkey = OTA)"
sec="https://raw.githubusercontent.com/LineageOS/android_build/lineage-18.1/target/product/security"
for k in platform.pk8 platform.x509.pem testkey.pk8 testkey.x509.pem; do curl -fsSL "$sec/$k" -o "$keys/$k"; done

log "fetch sdat2img / img2sdat"
curl -fsSL "https://raw.githubusercontent.com/xpirt/sdat2img/master/sdat2img.py" -o "$tools/sdat2img.py"
mkdir -p "$tools/img2sdat"
for f in img2sdat.py blockimgdiff.py rangelib.py sparse_img.py common.py; do
  curl -fsSL "https://raw.githubusercontent.com/xpirt/img2sdat/master/$f" -o "$tools/img2sdat/$f"
done
IMG2SDAT_PY="$PY311"
if [ -z "$IMG2SDAT_PY" ]; then sed -i 's/^import imp$/import types as _t; imp=_t.ModuleType("imp")/' "$tools/img2sdat/common.py"; IMG2SDAT_PY="python3"; fi

log "download base LineageOS zip"
# BASE_URL may be a plain direct URL, a Google Drive file id, or a Drive share URL.
# For a large Drive file, the first response is a "virus scan" HTML page carrying a
# confirm uuid; fetch that, then re-request with confirm=t&uuid=.
drive_id=""
case "$BASE_URL" in
  https://drive.*|https://*drive.google.com*) drive_id="$(printf '%s' "$BASE_URL" | sed -n 's/.*[?&]id=\([-_A-Za-z0-9]*\).*/\1/p')" ;;
  [A-Za-z0-9_-][A-Za-z0-9_-]*) [ "${#BASE_URL}" -ge 25 ] && ! printf '%s' "$BASE_URL" | grep -q '://' && drive_id="$BASE_URL" ;;
esac
if [ -n "$drive_id" ]; then
  curl -sL -c "$work/ck" "https://drive.usercontent.google.com/download?id=$drive_id&export=download" -o "$work/page.html"
  uuid="$(sed -n 's/.*name="uuid" value="\([^"]*\)".*/\1/p' "$work/page.html" | head -1)"
  curl -fL --retry 5 -b "$work/ck" -o "$work/base.zip" \
    "https://drive.usercontent.google.com/download?id=$drive_id&export=download&confirm=t&uuid=$uuid"
else
  curl -fL --retry 5 -o "$work/base.zip" "$BASE_URL"
fi
echo "$BASE_SHA256  $work/base.zip" | sha256sum -c -

log "build + platform-sign the 14 apps"
EXPERIENCE_TOP="$OUT" bash "$REPO/scripts/rom/collect-app-prebuilts.sh" "$OUT/unsigned" >/dev/null
mkdir -p "$OUT/apks"
declare -A MODS=( [ExperienceLauncher]=launcher [ExperienceSettings]=settings [ExperienceSetupWizard]=setupwizard
  [ExperienceDialer]=dialer [ExperienceMessages]=messages [ExperienceContacts]=contacts [ExperienceNotes]=notes
  [ExperienceClock]=clock [ExperienceCalendar]=calendar [ExperienceCalculator]=calculator [ExperienceFiles]=files
  [ExperienceGallery]=gallery [ExperienceCamera]=camera [ExperienceConverter]=converter )
for M in "${!MODS[@]}"; do
  "$BT/zipalign" -p -f 4 "$OUT/unsigned/$M/$M.apk" "$OUT/apks/$M.a" >/dev/null 2>&1 || "$BT/zipalign" -p -f 4 "$OUT/unsigned/$M/$M.apk" "$OUT/apks/$M.a"
  "$BT/apksigner" sign --v4-signing-enabled false --key "$keys/platform.pk8" --cert "$keys/platform.x509.pem" --out "$OUT/apks/$M.apk" "$OUT/apks/$M.a"
  rm -f "$OUT/apks/$M.a"
done

log "build overlays (platform-signed, keep Lineage SMS default)"
python3 -I "$REPO/scripts/gsi/build-overlays.py" --out "$OUT/overlays" --android-jar "$JAR" \
  --aapt2 "$BT/aapt2" --apksigner "$BT/apksigner" --zipalign "$BT/zipalign" \
  --key "$keys/platform.pk8" --cert "$keys/platform.x509.pem" --drop config_defaultSms >/dev/null

log "extract system image from the block OTA"
unzip -o -q "$work/base.zip" system.transfer.list system.new.dat.br system.patch.dat boot.img -d "$work"
python3 -c "
import brotli
d=brotli.Decompressor()
with open('$work/system.new.dat.br','rb') as f, open('$work/system.new.dat','wb') as o:
    while (c:=f.read(1<<22)): o.write(d.process(c))"
rm -f "$work/system.new.dat.br"
python3 "$tools/sdat2img.py" "$work/system.transfer.list" "$work/system.new.dat" "$work/system.img" >/dev/null
rm -f "$work/system.new.dat"

log "install Experience into the system image"
python3 -I "$REPO/scripts/gsi/repack-system-image.py" --image "$work/system.img" --apks "$OUT/apks" \
  --overlays "$OUT/overlays" --platform-cert "$keys/platform.x509.pem" --apksigner "$BT/apksigner" \
  --build-id "$EXPERIENCE_BUILD_ID" --base-label "LineageOS 18.1 chagalllte 20221230 (unofficial) - Android 11" \
  --edition Vanilla --out-manifest "$OUT/repack-manifest.json"

log "convert back to block OTA and repackage"
img2simg "$work/system.img" "$work/system.sparse.img"; rm -f "$work/system.img"
( cd "$work" && PYTHONPATH="$tools/img2sdat" "$IMG2SDAT_PY" "$tools/img2sdat/img2sdat.py" system.sparse.img -o sdat -v 4 -p system >/dev/null )
rm -f "$work/system.sparse.img"
python3 -c "
import brotli
c=brotli.Compressor(quality=9)
with open('$work/sdat/system.new.dat','rb') as f, open('$work/sdat/system.new.dat.br','wb') as o:
    while (ch:=f.read(1<<22)): o.write(c.process(ch))
    o.write(c.finish())"
rm -f "$work/sdat/system.new.dat"
python3 "$REPO/scripts/gsi/rebuild-block-ota-zip.py" "$work/base.zip" "$work/experience-unsigned.zip" "$work/sdat"
ZIP="$OUT/ExperienceUI-1.0.0-$EXPERIENCE_BUILD_ID-chagalllte.zip"
python3 -I "$REPO/scripts/gsi/sign-ota-wholefile.py" --in "$work/experience-unsigned.zip" --out "$ZIP" \
  --key "$keys/testkey.pk8" --cert "$keys/testkey.x509.pem"
( cd "$OUT" && sha256sum "$(basename "$ZIP")" > "$(basename "$ZIP").sha256" )

log "reskin official TWRP"
curl -fL --retry 5 -e "$TWRP_URL.html" -o "$work/twrp.img" "$TWRP_URL"
echo "$TWRP_SHA256  $work/twrp.img" | sha256sum -c -
unpack_bootimg --boot_img "$work/twrp.img" --out "$work/tw" --format mkbootimg >/dev/null
xz --format=lzma -dc "$work/tw/ramdisk" > "$work/rd.cpio"
mkdir -p "$work/rd" && ( cd "$work/rd" && cpio -idm --quiet < "$work/rd.cpio" )
SEMI="$(fc-match -f '%{file}' 'Inter:weight=600' 2>/dev/null || echo /usr/share/fonts/opentype/inter/Inter-SemiBold.otf)"
python3 -I "$REPO/scripts/twrp/skin.py" "$work/rd/twres" "$SEMI" "$SEMI"
( cd "$work/rd" && find . | LC_ALL=C sort | cpio -o -H newc --quiet > "$work/new.cpio" )
xz --format=lzma -9 -c "$work/new.cpio" > "$work/new.ramdisk"
IMG="$OUT/ExperienceTWRP-chagalllte-3.7.0_9.img"
python3 -I "$REPO/scripts/twrp/bootimg_v0.py" repack "$work/twrp.img" "$work/new.ramdisk" "$IMG"
( cd "$OUT" && cp "$(basename "$IMG")" recovery.img && tar -H ustar -c recovery.img > tw.tar \
  && md5=$(md5sum tw.tar | cut -d' ' -f1) && printf '%s  tw.tar\n' "$md5" >> tw.tar \
  && mv tw.tar ExperienceTWRP-chagalllte.tar.md5 && rm -f recovery.img \
  && sha256sum "$(basename "$IMG")" ExperienceTWRP-chagalllte.tar.md5 > ExperienceTWRP-chagalllte.sha256 )

cp "$REPO/docs/releases/chagall/ExperienceUI-chagalllte-README.txt" "$REPO/docs/releases/chagall/ExperienceTWRP-README.txt" "$OUT/"
log "done. assets in $OUT:"; ls -la "$OUT"/*.zip "$OUT"/*.img "$OUT"/*.tar.md5 "$OUT"/*.sha256 "$OUT"/*.txt >&2
