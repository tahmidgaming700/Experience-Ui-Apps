#!/usr/bin/env bash
# Builds the Experience Virtual OS runtime archive (base.tar.gz).
#
# The archive is a Linux ARM64 userspace that the Android app runs under PRoot.
# It is NOT an Android emulator: it contains Alpine Linux (musl) plus Alpine's
# own qemu-system-aarch64 / qemu-img builds. Those binaries are musl-linked and
# only run inside the Alpine root under PRoot, never directly on Bionic.
#
# Layout of base.tar.gz:
#   runtime-info.json     component versions, generated here
#   rootfs/               Alpine aarch64 root filesystem with QEMU preinstalled
#
# Usage: scripts/build-experience-virtual-os-runtime.sh <output-dir>
# Requires: curl, tar, gzip, sha256sum, python3. Runs on x86_64 or aarch64 Linux.
# Must run as root (or under sudo) so apk can write a normal package database.
set -euo pipefail

OUT="${1:-runtime-dist}"
ALPINE_VERSION="3.24.4"
ALPINE_BRANCH="v3.24"
MINIROOTFS_SHA256="4b14c18f7e90a063b61462ea1462a5732c154bf6adad594974fe6487cd98a8de"
APK_TOOLS_STATIC="apk-tools-static-3.0.9-r0.apk"
declare -A APK_TOOLS_STATIC_SHA256=(
  [x86_64]="0a1a0682375f66a83b7b903f6e4638ccfe312547d50d336b5f2be9fc391281be"
  [aarch64]="1f4c822fd14e8eb51ba27e17d5a1649aef33c91611c77eddeb318e8f2cb57fb1"
)
MIRROR="https://dl-cdn.alpinelinux.org/alpine"
# Packages installed into the guest userspace. Each is signature-checked by apk
# against the Alpine keys shipped in the verified minirootfs.
PACKAGES=(
  qemu-system-aarch64
  qemu-img
  qemu-hw-display-virtio-gpu
  qemu-hw-display-virtio-gpu-pci
  e2fsprogs
  ca-certificates
)
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1767225600}"

HOST_ARCH="$(uname -m)"
[[ -n "${APK_TOOLS_STATIC_SHA256[$HOST_ARCH]:-}" ]] || { echo "Unsupported build host: $HOST_ARCH" >&2; exit 1; }
[[ "$(id -u)" == "0" ]] || { echo "Run as root (sudo) so apk can populate the root filesystem." >&2; exit 1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

fetch() { curl -fsSL --retry 5 --retry-all-errors --retry-delay 3 "$1" -o "$2"; }
verify() { echo "$1  $2" | sha256sum -c - >/dev/null || { echo "SHA-256 mismatch for $2" >&2; exit 1; }; }

echo "==> Alpine minirootfs $ALPINE_VERSION (aarch64)"
MINI="alpine-minirootfs-$ALPINE_VERSION-aarch64.tar.gz"
fetch "$MIRROR/$ALPINE_BRANCH/releases/aarch64/$MINI" "$WORK/$MINI"
verify "$MINIROOTFS_SHA256" "$WORK/$MINI"

echo "==> apk-tools-static for $HOST_ARCH"
fetch "$MIRROR/$ALPINE_BRANCH/main/$HOST_ARCH/$APK_TOOLS_STATIC" "$WORK/apk-tools-static.apk"
verify "${APK_TOOLS_STATIC_SHA256[$HOST_ARCH]}" "$WORK/apk-tools-static.apk"
mkdir -p "$WORK/apk-tools"
tar -xzf "$WORK/apk-tools-static.apk" -C "$WORK/apk-tools" sbin/apk.static 2>/dev/null
APK="$WORK/apk-tools/sbin/apk.static"

STAGE="$WORK/stage"
ROOT="$STAGE/rootfs"
mkdir -p "$ROOT"
tar -xzf "$WORK/$MINI" -C "$ROOT" --numeric-owner
test -x "$ROOT/bin/busybox" && test -L "$ROOT/bin/sh" || { echo "minirootfs is missing /bin/sh" >&2; exit 1; }

cat > "$ROOT/etc/apk/repositories" <<EOF
$MIRROR/$ALPINE_BRANCH/main
$MIRROR/$ALPINE_BRANCH/community
EOF

echo "==> Installing ${PACKAGES[*]}"
# --no-scripts: package scripts are aarch64 code that cannot run on the build
# host. None of the selected packages need them for a PRoot userspace (the
# qemu package's script only creates a 'qemu' system group).
# apk reads the signing keys from $ROOT/etc/apk/keys (from the verified minirootfs).
"$APK" --root "$ROOT" --arch aarch64 --repositories-file "$ROOT/etc/apk/repositories" \
  --no-scripts --no-cache --update-cache add "${PACKAGES[@]}"

printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$ROOT/etc/resolv.conf"
# apk.log records wall-clock install times; empty it so builds are reproducible.
: > "$ROOT/var/log/apk.log"
mkdir -p "$ROOT/root" "$ROOT/tmp" "$ROOT/workspace"
chmod 1777 "$ROOT/tmp"

python3 -I - "$ROOT" "$STAGE/runtime-info.json" "$ALPINE_VERSION" "$SOURCE_DATE_EPOCH" <<'PY'
import json, sys, pathlib
root, out, alpine, epoch = sys.argv[1:]
installed = {}
name = None
for line in (pathlib.Path(root) / "lib/apk/db/installed").read_text().splitlines():
    if line.startswith("P:"):
        name = line[2:]
    elif line.startswith("V:") and name:
        installed[name] = line[2:]
info = {
    "format": 1,
    "abi": "arm64-v8a",
    "userspace": {"distribution": "alpine", "version": alpine, "libc": "musl"},
    "qemu": installed.get("qemu-system-aarch64"),
    "packages": dict(sorted(installed.items())),
    "builtAt": int(epoch),
}
pathlib.Path(out).write_text(json.dumps(info, indent=2) + "\n")
print(f"QEMU {info['qemu']}, {len(installed)} packages")
PY

echo "==> Verifying ELF architecture and shared-library closure"
python3 -I "$(dirname "$0")/verify-runtime-rootfs.py" "$ROOT"

if command -v qemu-aarch64 >/dev/null 2>&1; then
  echo "==> Smoke test via qemu-aarch64 user-mode emulation"
  qemu-aarch64 -L "$ROOT" "$ROOT/usr/bin/qemu-system-aarch64" --version
  qemu-aarch64 -L "$ROOT" "$ROOT/usr/bin/qemu-img" --version
  qemu-aarch64 -L "$ROOT" "$ROOT/usr/bin/qemu-system-aarch64" -machine virt -device help \
    | grep -q 'virtio-gpu-pci' || { echo "virtio-gpu-pci module missing" >&2; exit 1; }
  # Real RFB handshake: start a machine-less QEMU with VNC on localhost.
  qemu-aarch64 -L "$ROOT" "$ROOT/usr/bin/qemu-system-aarch64" -machine none -S \
    -monitor none -serial none -vnc 127.0.0.1:59 &
  QPID=$!
  BANNER=""
  for _ in $(seq 1 30); do
    BANNER="$(timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/5959 && head -c 12 <&3' 2>/dev/null || true)"
    [[ -n "$BANNER" ]] && break
    sleep 1
  done
  kill "$QPID" 2>/dev/null || true
  wait "$QPID" 2>/dev/null || true
  [[ "$BANNER" == RFB\ 003.* ]] || { echo "QEMU VNC server did not answer with an RFB banner" >&2; exit 1; }
  echo "VNC handshake OK: $BANNER"
else
  echo "qemu-aarch64 not available; skipping execution smoke test"
fi

echo "==> Packing base.tar.gz"
tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner \
  -C "$STAGE" -cf - runtime-info.json rootfs | gzip -n -9 > "$OUT/base.tar.gz"
cp "$STAGE/runtime-info.json" "$OUT/runtime-info.json"
(cd "$OUT" && sha256sum base.tar.gz > SHA256SUMS)
ls -l "$OUT"
cat "$OUT/SHA256SUMS"
