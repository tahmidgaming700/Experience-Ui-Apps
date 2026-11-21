#!/usr/bin/env bash
# Builds the experimental native engine prototype: gVisor's runsc for
# linux/arm64 (static, pure Go — no Bionic or glibc dependency), with the
# patches in patches/gvisor applied for Android kernels.
#
# Not used by the app yet. See docs/experience-virtual-os-native-engine.md.
#
# Usage: scripts/build-experience-gvisor-engine.sh <output-dir>
# Requires: git, go (GOTOOLCHAIN=auto downloads the version gVisor needs).
set -euo pipefail

OUT="${1:-engine-dist}"
GVISOR_REPO="https://github.com/google/gvisor"
GVISOR_COMMIT="3eadbee7083407d44e0a12b94a9fdf4e59293f93"   # branch "go", 2026-11-21
HERE="$(cd "$(dirname "$0")/.." && pwd)"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
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

(cd "$WORK/gvisor" && GOTOOLCHAIN=auto CGO_ENABLED=0 GOOS=linux GOARCH=arm64 \
  go build -trimpath -ldflags="-s -w -buildid=" -o "$OUT/runsc-arm64" ./runsc)
file "$OUT/runsc-arm64" | grep -q 'ARM aarch64, .*statically linked' || { echo "unexpected binary type" >&2; exit 1; }
cat > "$OUT/engine-info.json" <<JSON
{"component": "gvisor-runsc", "upstream": "$GVISOR_REPO", "commit": "$GVISOR_COMMIT",
 "patches": [$(cd "$HERE/patches/gvisor" && ls *.patch | sed 's/.*/"&"/' | paste -sd,)],
 "abi": "arm64-v8a", "status": "experimental-prototype"}
JSON
(cd "$OUT" && sha256sum runsc-arm64 > ENGINE-SHA256SUMS)
ls -l "$OUT"; cat "$OUT/ENGINE-SHA256SUMS"
