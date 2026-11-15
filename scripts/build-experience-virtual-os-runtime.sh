#!/usr/bin/env bash
# Builds the Experience Virtual OS runtime archive (base.tar.gz).
#
# The archive is a Linux ARM64 userspace that the Android app runs under PRoot.
# It is NOT an Android emulator: it contains Ubuntu (glibc) plus Ubuntu's own
# qemu-system-aarch64 / qemu-img builds. These binaries are glibc-linked and
# only run inside the Ubuntu root under PRoot, never directly on Bionic.
#
# Layout of base.tar.gz:
#   runtime-info.json     component versions, generated here
#   rootfs/               Ubuntu arm64 root filesystem with QEMU preinstalled
#
# Usage: scripts/build-experience-virtual-os-runtime.sh <output-dir>
# Requires: curl, tar, gzip, sha256sum, python3, apt-get, proot; qemu-aarch64 on
# non-ARM64 hosts. Must run as root (or sudo) so file ownership is preserved.
#
# Packages are resolved and downloaded by the host's apt (arm64 configuration,
# signatures verified with the keyring from the checksum-verified base tarball)
# and installed by the root's own dpkg inside PRoot (emulated with qemu-aarch64
# on x86_64 hosts), so maintainer scripts and ldconfig run as on a real system.
#
# Optional environment:
#   UBUNTU_MIRROR   default http://ports.ubuntu.com/ubuntu-ports
#   APT_OPTS        extra options for the host apt-get (e.g. proxy settings)
#   APT_BINDS       extra "-b host:guest" PRoot binds
set -euo pipefail

OUT="${1:-runtime-dist}"
UBUNTU_VERSION="26.04.1"
UBUNTU_SUITE="resolute"
BASE_TARBALL="ubuntu-base-${UBUNTU_VERSION}-base-arm64.tar.gz"
BASE_SHA256="5a1906794ced63a71a8119c3f211ef5f0bbe0a243001b4bbd41fdf80c5b219fd"
BASE_URL="https://cdimage.ubuntu.com/ubuntu-base/releases/${UBUNTU_VERSION%.*}/release/${BASE_TARBALL}"
UBUNTU_MIRROR="${UBUNTU_MIRROR:-http://ports.ubuntu.com/ubuntu-ports}"
PACKAGES=(
  qemu-system-arm          # provides qemu-system-aarch64 (VNC built in)
  qemu-utils               # qemu-img
  qemu-efi-aarch64         # AAVMF (EDK II) UEFI firmware for the virt machine
  e2fsprogs                # mke2fs for Android userdata disks
  ca-certificates
)
SOURCE_DATE_EPOCH="${SOURCE_DATE_EPOCH:-1767225600}"

[[ "$(id -u)" == "0" ]] || { echo "Run as root (sudo) so the root filesystem keeps its ownership." >&2; exit 1; }
command -v proot >/dev/null || { echo "proot is required (apt install proot)" >&2; exit 1; }
QEMU_FLAG=()
if [[ "$(uname -m)" != "aarch64" ]]; then
  command -v qemu-aarch64 >/dev/null || { echo "qemu-aarch64 (qemu-user) is required on $(uname -m) hosts" >&2; exit 1; }
  QEMU_FLAG=(-q qemu-aarch64)
fi

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"
STAGE="$WORK/stage"
ROOT="$STAGE/rootfs"
mkdir -p "$ROOT"

echo "==> Ubuntu base $UBUNTU_VERSION (arm64)"
curl -fsSL --retry 5 --retry-all-errors --retry-delay 3 "$BASE_URL" -o "$WORK/$BASE_TARBALL"
echo "$BASE_SHA256  $WORK/$BASE_TARBALL" | sha256sum -c - >/dev/null || { echo "SHA-256 mismatch for $BASE_TARBALL" >&2; exit 1; }
tar -xzf "$WORK/$BASE_TARBALL" -C "$ROOT" --numeric-owner
test -e "$ROOT/usr/bin/apt-get" || { echo "base tarball has no apt-get" >&2; exit 1; }

# Run a command inside the root as fake root (same mechanism the app uses).
# shellcheck disable=SC2206
EXTRA_BINDS=(${APT_BINDS:-})
in_root() {
  proot "${QEMU_FLAG[@]}" -0 -r "$ROOT" -b /dev -b /proc -b /sys "${EXTRA_BINDS[@]}" -w / \
    /usr/bin/env -i HOME=/root PATH=/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8 \
    DEBIAN_FRONTEND=noninteractive "$@"
}

cat > "$ROOT/etc/apt/sources.list.d/ubuntu.sources" <<EOF
Types: deb
URIs: $UBUNTU_MIRROR
Suites: $UBUNTU_SUITE $UBUNTU_SUITE-updates $UBUNTU_SUITE-security
Components: main universe
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
EOF
rm -f "$ROOT/etc/apt/sources.list"
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > "$ROOT/etc/resolv.conf"
# Keep the runtime small: no docs/man pages/locales (copyright files are kept).
cat > "$ROOT/etc/dpkg/dpkg.cfg.d/01-evos-slim" <<'EOF'
path-exclude=/usr/share/doc/*
path-include=/usr/share/doc/*/copyright
path-exclude=/usr/share/man/*
path-exclude=/usr/share/info/*
path-exclude=/usr/share/locale/*
path-include=/usr/share/locale/locale.alias
path-exclude=/usr/share/lintian/*
EOF

# 1) Resolve and download with the HOST's apt, configured for arm64 and for the
#    root's dpkg database. Signatures are verified against the Ubuntu archive
#    keyring from the checksum-verified base tarball. (Running apt itself under
#    an emulated PRoot breaks its temporary-keyring handling.)
echo "==> Resolving ${PACKAGES[*]}"
APTDIR="$WORK/apt"
mkdir -p "$APTDIR"/{lists/partial,archives/partial,etc/preferences.d,etc/apt.conf.d}
sed "s#Signed-By: /#Signed-By: $ROOT/#" "$ROOT/etc/apt/sources.list.d/ubuntu.sources" > "$APTDIR/etc/ubuntu.sources"
HOST_APT=(apt-get -o Dir::Etc::sourcelist=/dev/null -o Dir::Etc::sourceparts="$APTDIR/etc"
  -o Dir::Etc::preferencesparts="$APTDIR/etc/preferences.d" -o Dir::Etc::parts="$APTDIR/etc/apt.conf.d"
  -o Dir::State="$APTDIR" -o Dir::State::lists="$APTDIR/lists" -o Dir::State::status="$ROOT/var/lib/dpkg/status"
  -o Dir::Cache="$APTDIR" -o Dir::Cache::archives="$APTDIR/archives"
  -o APT::Architecture=arm64 -o APT::Architectures::=arm64 -o Acquire::Languages=none
  -o Acquire::Retries=5 -o APT::Sandbox::User=root -o Debug::NoLocking=1 ${APT_OPTS:-})
"${HOST_APT[@]}" update
"${HOST_APT[@]}" install -y --download-only --no-install-recommends "${PACKAGES[@]}"
ls "$APTDIR"/archives/*.deb | wc -l

# 2) Install inside the root with the root's own dpkg so maintainer scripts and
#    triggers (ldconfig, ca-certificates, …) run as on a real system.
echo "==> Installing $(ls "$APTDIR"/archives/*.deb | wc -l) packages with dpkg"
mkdir -p "$ROOT/tmp/evos-debs"
cp "$APTDIR"/archives/*.deb "$ROOT/tmp/evos-debs/"
# dpkg cannot order pre-dependencies inside one batch, so install in passes
# until every package is configured (normally two passes).
for pass in 1 2 3 4; do
  if in_root /bin/sh -c 'dpkg -i --skip-same-version /tmp/evos-debs/*.deb >/tmp/dpkg.log 2>&1; dpkg --configure -a >>/tmp/dpkg.log 2>&1; test -z "$(dpkg -C)"'; then
    echo "dpkg: all packages configured after pass $pass"
    break
  fi
  [[ $pass == 4 ]] && { cat "$ROOT/tmp/dpkg.log" >&2; echo "dpkg could not configure all packages" >&2; exit 1; }
done
in_root /bin/sh -c 'dpkg --audit && dpkg -C' || { echo "dpkg reports broken packages" >&2; exit 1; }
rm -rf "$ROOT/tmp/evos-debs"

python3 -I - "$ROOT" "$STAGE/runtime-info.json" "$UBUNTU_VERSION" "$SOURCE_DATE_EPOCH" <<'PY'
import json, sys, pathlib, re
root, out, version, epoch = sys.argv[1:]
installed, name, ver, ok = {}, None, None, False
for line in (pathlib.Path(root) / "var/lib/dpkg/status").read_text().splitlines() + [""]:
    if not line.strip():
        if name and ok:
            installed[name] = ver
        name, ver, ok = None, None, False
    elif line.startswith("Package: "):
        name = line[9:]
    elif line.startswith("Version: "):
        ver = line[9:]
    elif line.startswith("Status: "):
        ok = line.endswith("installed") and "not-installed" not in line
info = {
    "format": 1,
    "abi": "arm64-v8a",
    "userspace": {"distribution": "ubuntu", "version": version, "libc": "glibc", "glibc": installed.get("libc6")},
    "qemu": installed.get("qemu-system-arm"),
    "packages": dict(sorted(installed.items())),
    "builtAt": int(epoch),
}
pathlib.Path(out).write_text(json.dumps(info, indent=2) + "\n")
print(f"QEMU {info['qemu']}, glibc {info['userspace']['glibc']}, {len(installed)} packages")
PY

# Remove state that only records when/where the build ran.
rm -rf "$ROOT"/var/lib/apt/lists/* "$ROOT"/var/cache/apt/* "$ROOT"/var/log/* "$ROOT"/tmp/* \
  "$ROOT"/var/cache/debconf/*-old "$ROOT"/var/lib/dpkg/*-old "$ROOT"/var/cache/ldconfig/aux-cache
mkdir -p "$ROOT/root" "$ROOT/tmp" "$ROOT/data/evos"
chmod 1777 "$ROOT/tmp"

echo "==> Verifying ELF architecture and shared-library closure"
python3 -I "$(dirname "$0")/verify-runtime-rootfs.py" "$ROOT"

echo "==> Smoke test: run QEMU from the finished root through PRoot"
in_root /usr/bin/qemu-system-aarch64 --version
in_root /usr/bin/qemu-img --version | head -1
in_root /usr/bin/qemu-system-aarch64 -machine virt -device help | grep -q '"virtio-gpu-pci"' \
  || { echo "virtio-gpu-pci is missing from this QEMU build" >&2; exit 1; }
in_root /usr/bin/qemu-system-aarch64 -machine none -S -monitor none -serial none -vnc 127.0.0.1:59 &
QPID=$!
BANNER=""
for _ in $(seq 1 60); do
  BANNER="$(timeout 2 bash -c 'exec 3<>/dev/tcp/127.0.0.1/5959 && head -c 12 <&3' 2>/dev/null || true)"
  [[ -n "$BANNER" ]] && break
  sleep 1
done
kill "$QPID" 2>/dev/null || true
wait "$QPID" 2>/dev/null || true
[[ "$BANNER" == RFB\ 003.* ]] || { echo "QEMU VNC server did not answer with an RFB banner" >&2; exit 1; }
echo "VNC handshake OK: $BANNER"

echo "==> Packing base.tar.gz"
tar --sort=name --mtime="@$SOURCE_DATE_EPOCH" --owner=0 --group=0 --numeric-owner \
  -C "$STAGE" -cf - runtime-info.json rootfs | gzip -n -9 > "$OUT/base.tar.gz"
cp "$STAGE/runtime-info.json" "$OUT/runtime-info.json"
(cd "$OUT" && sha256sum base.tar.gz > SHA256SUMS)
ls -l "$OUT"
cat "$OUT/SHA256SUMS"
