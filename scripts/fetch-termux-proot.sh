#!/usr/bin/env bash
# Fetches the Android (Bionic) ARM64 PRoot build from the Termux package
# repository, verifies each .deb against a pinned SHA-256, and installs the
# binaries as APK native libraries:
#
#   libproot.so          proot executable (PIE, /system/bin/linker64)
#   libproot-loader.so   proot's static loader (PROOT_LOADER)
#   libtalloc.so         talloc runtime dependency
#   libandroid-shmem.so  System V shm emulation dependency
#
# Android only extracts files named lib*.so from an APK, so NEEDED entries
# that refer to versioned sonames are rewritten with patchelf, and the
# Termux-specific RUNPATH (/data/data/com.termux/...) is removed. The app
# sets LD_LIBRARY_PATH to its nativeLibraryDir instead.
#
# Termux removes superseded package versions from its pool. If a download
# 404s, update the pins below from
#   https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages
# and re-verify on a device. A mismatch never falls back to an unpinned file.
#
# Usage: scripts/fetch-termux-proot.sh <jniLibs/arm64-v8a output dir>
# Requires: curl, ar (binutils), tar, xz/zstd as needed, patchelf, readelf.
set -euo pipefail

OUT="${1:?usage: fetch-termux-proot.sh <output-dir>}"
REPO="https://packages.termux.dev/apt/termux-main"
PINS=(
  "proot|pool/main/p/proot/proot_5.1.107.102_aarch64.deb|10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030"
  "libtalloc|pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb|556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7"
  "libandroid-shmem|pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb|0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6"
)
PREFIX="data/data/com.termux/files/usr"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"

for pin in "${PINS[@]}"; do
  IFS='|' read -r name path sha <<<"$pin"
  deb="$WORK/$name.deb"
  curl -fsSL --retry 5 --retry-all-errors --retry-delay 3 "$REPO/$path" -o "$deb"
  echo "$sha  $deb" | sha256sum -c - >/dev/null || { echo "SHA-256 mismatch for $name" >&2; exit 1; }
  mkdir -p "$WORK/$name"
  (cd "$WORK/$name" && ar x "$deb" && tar -xf data.tar.*)
  echo "Verified $name ($path)"
done

install -m 0755 "$WORK/proot/$PREFIX/bin/proot" "$OUT/libproot.so"
install -m 0755 "$WORK/proot/$PREFIX/libexec/proot/loader" "$OUT/libproot-loader.so"
install -m 0755 "$WORK/libtalloc/$PREFIX/lib/libtalloc.so.2.5.0" "$OUT/libtalloc.so"
install -m 0755 "$WORK/libandroid-shmem/$PREFIX/lib/libandroid-shmem.so" "$OUT/libandroid-shmem.so"

patchelf --replace-needed libtalloc.so.2 libtalloc.so "$OUT/libproot.so"
patchelf --set-soname libtalloc.so "$OUT/libtalloc.so"
for f in libproot.so libtalloc.so libandroid-shmem.so; do
  patchelf --remove-rpath "$OUT/$f"
done

# Verify: every library is AArch64, Bionic-linked, and resolvable from the set
# we ship plus Android system libraries.
SYSTEM_LIBS=" libc.so libm.so libdl.so liblog.so "
for f in libproot.so libtalloc.so libandroid-shmem.so libproot-loader.so; do
  header="$(readelf -h "$OUT/$f")"
  grep -q 'Machine:.*AArch64' <<<"$header" || { echo "$f is not AArch64" >&2; exit 1; }
  if readelf -d "$OUT/$f" | grep -q -E 'RUNPATH|RPATH'; then echo "$f still has a RUNPATH" >&2; exit 1; fi
  while read -r lib; do
    [[ -z "$lib" ]] && continue
    if [[ "$SYSTEM_LIBS" != *" $lib "* && ! -f "$OUT/$lib" ]]; then
      echo "$f needs $lib, which is neither bundled nor an Android system library" >&2; exit 1
    fi
  done < <(readelf -d "$OUT/$f" | sed -n 's/.*Shared library: \[\(.*\)\]/\1/p')
done
readelf -l "$OUT/libproot.so" | grep -q '/system/bin/linker64' || { echo "libproot.so is not a Bionic executable" >&2; exit 1; }
if readelf -l "$OUT/libproot-loader.so" | grep -q INTERP; then echo "loader must be static" >&2; exit 1; fi
ls -l "$OUT"
