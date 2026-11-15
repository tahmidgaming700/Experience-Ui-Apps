#!/usr/bin/env python3
"""Generate manifest.json for an Experience Virtual OS runtime release.

The manifest is derived entirely from the built artifact: the archive is
re-read, its SHA-256 and size are computed, SHA256SUMS is cross-checked,
every member path is checked for safety, and the required entry points are
confirmed to be present. Nothing is guessed.

Usage:
  generate-runtime-manifest.py --dist runtime-dist --repo owner/name \
      --tag runtime-v0.6.0 --min-app-version-code 6 [--output manifest.json]
"""
import argparse
import hashlib
import json
import os
import posixpath
import re
import sys
import tarfile

SCHEMA_VERSION = 2
KIND = "experience-virtual-os-runtime"
ENTRYPOINTS = {
    "shell": "rootfs/usr/bin/sh",
    "qemuSystemAarch64": "rootfs/usr/bin/qemu-system-aarch64",
    "qemuImg": "rootfs/usr/bin/qemu-img",
    "uefiFirmware": "rootfs/usr/share/AAVMF/AAVMF_CODE.fd",
    "uefiVars": "rootfs/usr/share/AAVMF/AAVMF_VARS.fd",
    "mke2fs": "rootfs/usr/sbin/mke2fs",
}
REQUIRED = list(ENTRYPOINTS.values()) + [
    "runtime-info.json",
    "rootfs/usr/lib/ld-linux-aarch64.so.1",
    "rootfs/etc/os-release",
]
# Capability levels are fixed by what has been verified for this runtime
# design; see docs/experience-virtual-os-guest-compatibility.md.
CAPABILITIES = {
    "linuxUserspace": "supported",
    "linuxGuestArm64": "experimental",
    "androidGuest": "experimental-console-only",
}
NOTES = (
    "Ubuntu ARM64 userspace with Ubuntu's glibc-linked QEMU, run under PRoot "
    "from the app. QEMU uses TCG software emulation unless /dev/kvm is usable. "
    "This is not the Android Emulator: Android system images run on QEMU's generic "
    "'virt' machine without goldfish/ranchu devices or host GPU acceleration, so "
    "Android guest support is experimental."
)


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def inspect_archive(path):
    names = set()
    unpacked = 0
    with tarfile.open(path, "r:gz") as tar:
        for m in tar:
            name = m.name
            norm = posixpath.normpath(name)
            if name.startswith("/") or norm == ".." or norm.startswith("../"):
                raise SystemExit(f"unsafe archive member: {name}")
            if not (m.isfile() or m.isdir() or m.issym() or m.islnk()):
                raise SystemExit(f"unsupported member type: {name}")
            if m.islnk():
                target = posixpath.normpath(m.linkname)
                if m.linkname.startswith("/") or target.startswith("../"):
                    raise SystemExit(f"hard link escapes archive: {name} -> {m.linkname}")
            names.add(norm[2:] if norm.startswith("./") else norm)
            if m.isfile():
                unpacked += m.size
        info_member = tar.getmember("runtime-info.json")
        info = json.load(tar.extractfile(info_member))
    missing = [p for p in REQUIRED if p not in names]
    if missing:
        raise SystemExit(f"archive is missing required paths: {missing}")
    return info, unpacked, len(names)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dist", required=True)
    ap.add_argument("--repo", required=True)
    ap.add_argument("--tag", required=True)
    ap.add_argument("--min-app-version-code", type=int, required=True)
    ap.add_argument("--output")
    args = ap.parse_args()

    match = re.fullmatch(r"(?:runtime-)?v(\d+\.\d+\.\d+(?:-[0-9A-Za-z.]+)?)", args.tag)
    if not match:
        raise SystemExit(f"tag must look like v1.2.3 or runtime-v1.2.3, got {args.tag}")
    version = match.group(1)

    archive = os.path.join(args.dist, "base.tar.gz")
    digest = sha256(archive)
    size = os.path.getsize(archive)
    with open(os.path.join(args.dist, "SHA256SUMS")) as f:
        sums = dict(reversed(line.split()) for line in f if line.strip())
    if sums.get("base.tar.gz") != digest:
        raise SystemExit("SHA256SUMS does not match base.tar.gz")

    info, unpacked, count = inspect_archive(archive)
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "kind": KIND,
        "runtimeVersion": version,
        "releaseTag": args.tag,
        "abi": info.get("abi", "arm64-v8a"),
        "minHostApi": 28,
        "minAppVersionCode": args.min_app_version_code,
        "archive": {
            "fileName": "base.tar.gz",
            "url": f"https://github.com/{args.repo}/releases/download/{args.tag}/base.tar.gz",
            "format": "tar+gzip",
            "sizeBytes": size,
            "sha256": digest,
            "unpackedSizeBytes": unpacked,
            "entries": count,
        },
        "components": {
            "userspace": f"{info['userspace']['distribution']} {info['userspace']['version']}",
            "libc": info["userspace"]["libc"],
            "qemu": info.get("qemu"),
        },
        "entrypoints": ENTRYPOINTS,
        "requiredPaths": REQUIRED,
        "capabilities": CAPABILITIES,
        "notes": NOTES,
    }
    text = json.dumps(manifest, indent=2) + "\n"
    out = args.output or os.path.join(args.dist, "manifest.json")
    with open(out, "w") as f:
        f.write(text)
    sys.stdout.write(text)


if __name__ == "__main__":
    main()
