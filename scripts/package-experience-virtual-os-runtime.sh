#!/usr/bin/env bash
set -euo pipefail
ROOT="apps/experience-virtual-os/app/src/main"
OUT="${GITHUB_WORKSPACE:-.}/runtime-dist"
STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT
ALPINE="$ROOT/assets/alpine/alpine-full-rootfs.bin"
KERNEL="$ROOT/assets/kernel/kernel-qemu"
PROOT="$ROOT/jniLibs/arm64-v8a/libproot.so"
LOADER="$ROOT/jniLibs/arm64-v8a/libproot-loader.so"
for file in "$ALPINE" "$KERNEL" "$PROOT" "$LOADER"; do
  test -s "$file" || { echo "Missing runtime input: $file" >&2; exit 1; }
done
mkdir -p "$STAGE/alpine" "$STAGE/kernel" "$STAGE/lib/arm64-v8a" "$OUT"
install -m 0644 "$ALPINE" "$STAGE/alpine/alpine-full-rootfs.bin"
install -m 0644 "$KERNEL" "$STAGE/kernel/kernel-qemu"
install -m 0755 "$PROOT" "$STAGE/lib/arm64-v8a/libproot.so"
install -m 0755 "$LOADER" "$STAGE/lib/arm64-v8a/libproot-loader.so"
tar -czf "$OUT/base.tar.gz" -C "$STAGE" .
tar -tzf "$OUT/base.tar.gz" >/dev/null
(cd "$OUT" && shasum -a 256 base.tar.gz > SHA256SUMS)
SIZE="$(stat -f%z "$OUT/base.tar.gz")"
SHA="$(awk '{print $1}' "$OUT/SHA256SUMS")"
echo "Runtime bootstrap archive: $SIZE bytes"
echo "SHA-256: $SHA"
if [[ "${GITHUB_REF_TYPE:-}" == "tag" ]]; then
  TAG="${GITHUB_REF_NAME}"
  VERSION="${TAG#v}"
  python3 - "$OUT/manifest.json" "$GITHUB_REPOSITORY" "$TAG" "$VERSION" "$SIZE" "$SHA" <<'PY'
import json, pathlib, sys
path, repo, tag, version, size, sha = sys.argv[1:]
manifest = {
  "schemaVersion": 1,
  "runtimeVersion": version,
  "platform": "android",
  "architecture": "arm64-v8a",
  "status": "published",
  "assets": [{
    "id": "base",
    "fileName": "base.tar.gz",
    "url": f"https://github.com/{repo}/releases/download/{tag}/base.tar.gz",
    "sizeBytes": int(size),
    "sha256": sha,
    "contents": ["alpine/alpine-full-rootfs.bin", "kernel/kernel-qemu", "lib/arm64-v8a/libproot.so", "lib/arm64-v8a/libproot-loader.so"],
    "notes": "Bootstrap environment only. QEMU packages are currently installed inside Alpine by the app; this archive does not contain a native Android-host QEMU executable or a bootable Android guest image."
  }]
}
pathlib.Path(path).write_text(json.dumps(manifest, indent=2) + "\n")
PY
  cat "$OUT/manifest.json"
fi
