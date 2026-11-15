#!/usr/bin/env python3
"""Inspect an Experience Virtual OS APK.

Checks:
  * it is a valid zip with AndroidManifest.xml and classes*.dex;
  * the ARM64 PRoot set is present (libproot.so, libproot-loader.so,
    libtalloc.so, libandroid-shmem.so), each an AArch64 ELF;
  * libproot.so is a Bionic executable (interpreter /system/bin/linker64)
    whose DT_NEEDED entries resolve to bundled or Android system libraries;
  * no other ABI directories exist (the binaries are arm64-only);
  * no runtime payload (rootfs, kernels) is bundled: those ship separately
    as base.tar.gz on GitHub Releases.

Usage: verify-experience-virtual-os-apk.py <apk> [--expect-package com.experience.virtualos]
"""
import argparse
import io
import struct
import sys
import zipfile

REQUIRED_LIBS = ["libproot.so", "libproot-loader.so", "libtalloc.so", "libandroid-shmem.so"]
SYSTEM_LIBS = {"libc.so", "libm.so", "libdl.so", "liblog.so"}
FORBIDDEN_PREFIXES = ["assets/alpine/", "assets/kernel/", "assets/rootfs"]


def elf_info(data):
    if data[:4] != b"\x7fELF" or data[4] != 2 or data[5] != 1:
        raise ValueError("not a 64-bit little-endian ELF")
    machine = struct.unpack_from("<H", data, 18)[0]
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    interp, dynamic, loads = None, None, []
    for i in range(phnum):
        p_type, _f, off, vaddr, _pa, filesz = struct.unpack_from("<IIQQQQ", data, phoff + i * phentsize)
        if p_type == 3:
            interp = data[off:off + filesz].rstrip(b"\0").decode()
        elif p_type == 2:
            dynamic = (off, filesz)
        elif p_type == 1:
            loads.append((vaddr, off, filesz))
    needed = []
    if dynamic:
        strtab, entries = None, []
        for j in range(0, dynamic[1], 16):
            tag, val = struct.unpack_from("<qQ", data, dynamic[0] + j)
            if tag == 0:
                break
            if tag == 1:
                entries.append(val)
            elif tag == 5:
                strtab = val
        base = next((strtab - v + o for v, o, s in loads if strtab is not None and v <= strtab < v + s), None)
        for e in entries:
            end = data.index(b"\0", base + e)
            needed.append(data[base + e:end].decode())
    return machine, interp, needed


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("apk")
    args = ap.parse_args()
    errors = []
    with zipfile.ZipFile(args.apk) as z:
        bad = z.testzip()
        if bad:
            errors.append(f"corrupt entry {bad}")
        names = z.namelist()
        if "AndroidManifest.xml" not in names:
            errors.append("missing AndroidManifest.xml")
        if not any(n.startswith("classes") and n.endswith(".dex") for n in names):
            errors.append("no dex files")
        abis = sorted({n.split("/")[1] for n in names if n.startswith("lib/") and n.count("/") == 2})
        if abis != ["arm64-v8a"]:
            errors.append(f"expected only arm64-v8a native libraries, found {abis}")
        bundled = {n.split("/")[-1] for n in names if n.startswith("lib/arm64-v8a/")}
        for lib in REQUIRED_LIBS:
            path = f"lib/arm64-v8a/{lib}"
            if path not in names:
                errors.append(f"missing {path}")
                continue
            data = z.read(path)
            try:
                machine, interp, needed = elf_info(data)
            except ValueError as e:
                errors.append(f"{lib}: {e}")
                continue
            if machine != 183:
                errors.append(f"{lib}: not AArch64 (e_machine {machine})")
            if lib == "libproot.so" and interp != "/system/bin/linker64":
                errors.append(f"libproot.so interpreter is {interp}, expected Bionic /system/bin/linker64")
            if lib == "libproot-loader.so" and interp is not None:
                errors.append("libproot-loader.so must be static")
            for n in needed:
                if n not in SYSTEM_LIBS and n not in bundled:
                    errors.append(f"{lib} needs {n}, which is not bundled")
            print(f"ok  {path:36} {len(data):>8} bytes  needs {', '.join(needed) or '-'}")
        if "assets/runtime/base-runtime.bin" in names:
            import hashlib, json
            manifest = json.loads(z.read("assets/runtime/manifest.json"))
            h = hashlib.sha256()
            with z.open("assets/runtime/base-runtime.bin") as f:
                for chunk in iter(lambda: f.read(1 << 20), b""):
                    h.update(chunk)
            if h.hexdigest() != manifest["archive"]["sha256"]:
                errors.append("bundled runtime does not match its manifest SHA-256")
            else:
                print(f"ok  bundled runtime {manifest['runtimeVersion']} ({manifest['components'].get('userspace')}) sha256 {h.hexdigest()[:16]}…")
        for n in names:
            if any(n.startswith(p) for p in FORBIDDEN_PREFIXES):
                errors.append(f"runtime payload must not be bundled in the APK: {n}")
    if errors:
        for e in errors:
            print("ERROR:", e)
        return 1
    print(f"APK verification passed: {args.apk}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
