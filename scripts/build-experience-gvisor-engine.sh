#!/usr/bin/env bash
# Builds the experimental native engine: gVisor (runsc + sentry and its C
# sidecars) for linux/arm64 with the patches in patches/gvisor applied, so it
# runs inside an ordinary Android app (no root, no user namespaces).
#
# Output files are named lib*.so because Android only executes files that an
# APK installs into its nativeLibraryDir; they are static executables, not
# shared libraries. Pass the app's jniLibs/arm64-v8a directory as the output
# to package them, e.g.
#
#   scripts/build-experience-gvisor-engine.sh engine-dist
#
# Requires: git, go (GOTOOLCHAIN=auto downloads the version gVisor needs),
# clang + lld and Linux UAPI headers for arm64. On an x86_64 host also
# qemu-aarch64 (qemu-user), which runs the seccomp precompiler for arm64.
# See docs/experience-virtual-os-native-engine.md.
set -euo pipefail

OUT="${1:-engine-dist}"
GVISOR_REPO="https://github.com/google/gvisor"
GVISOR_COMMIT="3eadbee7083407d44e0a12b94a9fdf4e59293f93"   # branch "go", 2026-11-21
HERE="$(cd "$(dirname "$0")/.." && pwd)"

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK:?}"' EXIT
mkdir -p "$OUT"
OUT="$(cd "$OUT" && pwd)"

git -C "$WORK" init -q gvisor
git -C "$WORK/gvisor" fetch -q --depth 1 "$GVISOR_REPO" "$GVISOR_COMMIT"
git -C "$WORK/gvisor" checkout -q FETCH_HEAD
test "$(git -C "$WORK/gvisor" rev-parse HEAD)" == "$GVISOR_COMMIT"
for p in "$HERE"/patches/gvisor/*.patch; do
  echo "Applying $(basename "$p")"
  git -C "$WORK/gvisor" apply --check "$p"
  git -C "$WORK/gvisor" apply "$p"
done

export GOTOOLCHAIN=auto CGO_ENABLED=0 GOOS=linux GOARCH=arm64
cd "$WORK/gvisor"

# The Go-branch checkout carries the platform stub seccomp programs
# precompiled for amd64 only; regenerate them for arm64. The generator must
# run as an arm64 process because syscall numbers are compile-time constants.
go build -o "$WORK/evos_precompile" ./tools/evos_precompile
case "$(uname -m)" in
  aarch64|arm64) "$WORK/evos_precompile" . ;;
  *)
    QEMU="$(command -v qemu-aarch64 || command -v qemu-aarch64-static || true)"
    [[ -n "$QEMU" ]] || { echo "qemu-aarch64 is required on $(uname -m) hosts" >&2; exit 1; }
    "$QEMU" "$WORK/evos_precompile" .
    ;;
esac
grep -q 0xc00000b7 pkg/sentry/platform/systrap/stubfilter_precompiled.go \
  || { echo "systrap seccomp programs were not regenerated for arm64" >&2; exit 1; }

GOFLAGS_BUILD=(-trimpath -ldflags="-s -w -buildid=")
go build "${GOFLAGS_BUILD[@]}" -o "$OUT/libevos_runsc.so" ./runsc
go build "${GOFLAGS_BUILD[@]}" -o "$OUT/libevos_gvisor_sentry.so" ./runsc/cmd/sentry

CFLAGS=(--target=aarch64-linux-gnu -fuse-ld=lld -O2 -static -nostdlib -ffreestanding
        -fno-stack-protector -fno-builtin -Wl,--build-id=none)
if [[ -d /usr/aarch64-linux-gnu/include ]]; then CFLAGS+=(-isystem /usr/aarch64-linux-gnu/include); fi
for pair in "prewarmer prewarmer" "fdparking fdparking" "procumounter procumounter"; do
  set -- $pair
  clang "${CFLAGS[@]}" -o "$OUT/libevos_gvisor_$2.so" "runsc/$1/$1.c"
done

cd "$OUT"
for f in libevos_runsc.so libevos_gvisor_sentry.so libevos_gvisor_prewarmer.so \
         libevos_gvisor_fdparking.so libevos_gvisor_procumounter.so; do
  file "$f" | grep -q 'ARM aarch64, .*statically linked' || { echo "$f: unexpected binary type" >&2; file "$f" >&2; exit 1; }
done
cat > engine-info.json <<JSON
{"component": "gvisor", "upstream": "$GVISOR_REPO", "commit": "$GVISOR_COMMIT",
 "patches": [$(cd "$HERE/patches/gvisor" && ls *.patch | sed 's/.*/"&"/' | paste -sd,)],
 "abi": "arm64-v8a", "mode": "android-host", "status": "experimental"}
JSON
sha256sum libevos_*.so > ENGINE-SHA256SUMS
ls -l; cat ENGINE-SHA256SUMS
