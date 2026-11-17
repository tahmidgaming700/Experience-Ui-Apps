#!/usr/bin/env bash
# Port ColorOS 17 to the realme C75 (RMX3941, MediaTek MT6768/"MT6769").
#
#   port.sh <work-dir> <out-dir>
#
# Base  (hardware side: vendor, odm, kernel, firmware): the official RMX3941
#        package from realme's own server, every image checked against the
#        MD5 list realme ships inside it.
# Donor (software side: system, system_ext, product, my_*): a ColorOS 17
#        full OTA for the OPPO Find X3 (PEDM00). It is a community build, not
#        an OPPO release, and carries no checksum we can check it against.
#
# Output: <out-dir>/ holds the images, super_empty.img, flash scripts and
# SHA256SUMS. Nothing here has been booted on a phone; see README.md.
#
# Needs: python3, curl, clang, libssl-dev, zlib1g-dev, libfmt-dev,
#        erofs-utils (mkfs.erofs/fsck.erofs), android-sdk-libsparse-utils (simg2img)
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
source "$HERE/config.env"
WORK=$(mkdir -p "${1:?work dir}" && cd "$1" && pwd)
OUT=$(mkdir -p "${2:?out dir}" && cd "$2" && pwd)
LIB=$HERE/lib
PY="python3 -I"
BIN=$WORK/bin
export PATH="$BIN:$PATH"

B=$WORK/base; D=$WORK/donor; T=$WORK/tree; M=$WORK/meta; R=$WORK/repacked
mkdir -p "$B" "$D" "$T" "$M" "$R"
log() { printf '\n== %s\n' "$*"; }

log "tools"
command -v lpmake >/dev/null || bash "$HERE/tools/build-lptools.sh" "$WORK/aosp" "$BIN"
for t in mkfs.erofs fsck.erofs simg2img lpmake; do command -v $t >/dev/null || { echo "missing $t"; exit 1; }; done

log "base: fetch RMX3941 images"
BASE_FW="boot vendor_boot dtbo vbmeta vbmeta_system vbmeta_vendor lk md1img scp spmfw sspm tee gz"
BASE_SUPER="vendor odm my_product system_ext"
want=()
for p in $BASE_FW $BASE_SUPER; do [ -f "$B/$p.img" ] || want+=("IMAGES/$p.img"); done
[ -f "$B/all_files_checksum.txt" ] || want+=(all_files_checksum.txt)
[ -f "$B/build.prop" ] || want+=(MY_MANIFEST/build.prop)
[ -f "$B/super_meta.raw" ] || want+=("IMAGES/super_meta.$BASE_NV_ID.raw")
if [ ${#want[@]} -gt 0 ]; then
  $PY "$LIB/remote_zip.py" get "$BASE_URL" "$B" "${want[@]}"
  [ -f "$B/super_meta.$BASE_NV_ID.raw" ] && mv "$B/super_meta.$BASE_NV_ID.raw" "$B/super_meta.raw"
fi

log "base: verify against realme's MD5 list"
(cd "$B" && for p in $BASE_FW $BASE_SUPER; do
  [ -f "$p.img.verified" ] && continue
  sum=$(awk -v f="IMAGES/$p.img" '$2==f {print $1}' all_files_checksum.txt)
  [ -n "$sum" ] || { echo "no checksum listed for $p"; exit 1; }
  echo "$sum  $p.img" | md5sum -c --quiet - && touch "$p.img.verified"
done)
grep -q "^ro.product.model=$DEVICE_MODEL$" "$B/build.prop" || { echo "base is not $DEVICE_MODEL"; exit 1; }

log "base: unsparse super partitions"
for p in $BASE_SUPER; do
  if [ "$(od -An -tx1 -N4 "$B/$p.img" | tr -d ' ')" = 3aff26ed ]; then  # sparse magic
    simg2img "$B/$p.img" "$B/$p.raw" && mv "$B/$p.raw" "$B/$p.img"
  fi
done

log "donor: stream partitions out of the OTA"
DONOR_PARTS="system system_ext product my_product my_stock my_region my_manifest my_heytap my_carrier my_bigball my_engineering"
need=(); for p in $DONOR_PARTS; do [ -f "$D/$p.img" ] || need+=("$p"); done
[ ${#need[@]} -eq 0 ] || $PY "$LIB/remote_zip.py" sdat "$DONOR_URL" "$D" "${need[@]}"
for p in $DONOR_PARTS; do fsck.erofs "$D/$p.img" >/dev/null || { echo "donor $p is not a valid EROFS image"; exit 1; }; done

log "metadata (SELinux labels, owners, modes) of the original images"
for p in my_manifest my_product system_ext; do $PY "$LIB/erofs_meta.py" dump "$D/$p.img" "$M/donor_$p.tsv"; done
$PY "$LIB/erofs_meta.py" dump "$B/my_product.img" "$M/base_my_product.tsv"

log "patch my_manifest"
rm -rf "$T/my_manifest"
fsck.erofs --extract="$T/my_manifest" --preserve "$D/my_manifest.img" >/dev/null
$PY "$LIB/patch_port.py" manifest "$T/my_manifest" "$B/build.prop"
$PY "$LIB/repack.py" "$T/my_manifest" "$R/my_manifest.img" /my_manifest "$M/donor_my_manifest.tsv"
rm -rf "$T/my_manifest"

log "patch my_product"
rm -rf "$T/my_product" "$T/base_my_product"
fsck.erofs --extract="$T/my_product" --preserve "$D/my_product.img" >/dev/null
fsck.erofs --extract="$T/base_my_product" --preserve "$B/my_product.img" >/dev/null
$PY "$LIB/patch_port.py" my_product "$T/my_product" "$T/base_my_product" "$T/my_product.imports"
$PY "$LIB/repack.py" "$T/my_product" "$R/my_product.img" /my_product \
  "$M/donor_my_product.tsv" "$T/my_product.imports" "$M/base_my_product.tsv"
rm -rf "$T/my_product" "$T/base_my_product"

log "patch system_ext: add VNDK v31 for the RMX3941 vendor"
rm -rf "$T/vendor"
fsck.erofs --extract="$T/vendor" "$B/vendor.img" >/dev/null
VNDK=$(grep '^ro.vndk.version=' "$T/vendor/build.prop" | cut -d= -f2)
rm -rf "$T/vendor"
[ "$VNDK" = 31 ] || { echo "unexpected vendor VNDK version: $VNDK"; exit 1; }
rm -rf "$T/system_ext" "$T/base_system_ext"
fsck.erofs --extract="$T/system_ext" --preserve "$D/system_ext.img" >/dev/null
if [ ! -f "$T/system_ext/apex/com.android.vndk.v$VNDK.apex" ]; then
  fsck.erofs --extract="$T/base_system_ext" --preserve "$B/system_ext.img" >/dev/null
  cp "$T/base_system_ext/apex/com.android.vndk.v$VNDK.apex" "$T/system_ext/apex/"
  rm -rf "$T/base_system_ext"
fi
grep -P '^/apex/com\.android\.vndk\.v30\.apex\t' "$M/donor_system_ext.tsv" | sed "s/v30/v$VNDK/" > "$T/system_ext.extra.tsv"
$PY "$LIB/repack.py" "$T/system_ext" "$R/system_ext.img" /system_ext "$M/donor_system_ext.tsv" --extra "$T/system_ext.extra.tsv"
rm -rf "$T/system_ext"

log "collect images"
for p in my_manifest my_product system_ext; do mv "$R/$p.img" "$OUT/$p.img"; done
for p in system product my_stock my_region my_heytap my_carrier my_bigball my_engineering; do mv "$D/$p.img" "$OUT/$p.img"; done
for p in vendor odm; do cp "$B/$p.img" "$OUT/$p.img"; done
for p in $BASE_FW; do cp "$B/$p.img" "$OUT/$p.img"; done

log "vbmeta: set the disable-verity/disable-verification flags"
# AvbVBMetaImageHeader.flags is a big-endian u32 at offset 120; this is the
# same edit `fastboot --disable-verity --disable-verification` makes.
$PY - "$OUT/vbmeta.img" <<'EOF'
import struct, sys
p = sys.argv[1]; b = bytearray(open(p, "rb").read())
assert b[:4] == b"AVB0", "vbmeta.img has no AVB header"
struct.pack_into(">I", b, 120, struct.unpack_from(">I", b, 120)[0] | 3)
open(p, "wb").write(b)
EOF

log "super layout"
$PY "$LIB/lpmeta.py" "$B/super_meta.raw" | tee "$WORK/super_layout.txt"
SUPER_SIZE=$(awk '/^blockdev super/ {print $4}' "$WORK/super_layout.txt")
GROUP_MAX=$(awk '/^group main_a/ {print $6}' "$WORK/super_layout.txt")
LOGICAL="system system_ext product vendor odm my_product my_stock my_region my_manifest my_heytap my_carrier my_bigball my_engineering"
total=0
for p in $LOGICAL; do total=$((total + ( ($(stat -c %s "$OUT/$p.img") + 1048575) / 1048576 * 1048576 ))); done
echo "logical partitions: $total bytes of $GROUP_MAX available in main_a"
[ "$total" -le "$GROUP_MAX" ] || { echo "the port does not fit in super"; exit 1; }
args=(--metadata-size 65536 --super-name super --metadata-slots 3 --virtual-ab
      --device "super:$SUPER_SIZE" --group "main_a:$GROUP_MAX" --group "main_b:$GROUP_MAX")
for p in $LOGICAL; do args+=(--partition "${p}_a:readonly:0:main_a" --partition "${p}_b:readonly:0:main_b"); done
lpmake "${args[@]}" --output "$OUT/super_empty.img"

log "flash scripts and checksums"
cp "$HERE/flash/flash_all.sh" "$HERE/flash/flash_all.bat" "$OUT/"
echo "$LOGICAL" > "$OUT/logical_partitions.txt"
echo "$BASE_FW" > "$OUT/firmware_partitions.txt"
(cd "$OUT" && sha256sum *.img > SHA256SUMS)
ls -la "$OUT"
