#!/usr/bin/env python3
"""Verify an Experience Virtual OS runtime root filesystem.

Checks, without executing anything:
  * required files exist (shell, QEMU, qemu-img, UEFI firmware);
  * every required executable is an ELF64 little-endian AArch64 binary;
  * its program interpreter is the musl loader present in the rootfs;
  * every DT_NEEDED library resolves inside the rootfs (transitively).

Usage: verify-runtime-rootfs.py <rootfs>
"""
import os
import struct
import sys

REQUIRED_EXECUTABLES = [
    "bin/busybox",
    "usr/bin/qemu-system-aarch64",
    "usr/bin/qemu-img",
    "sbin/mke2fs",
]
REQUIRED_FILES = [
    "bin/sh",
    "lib/ld-musl-aarch64.so.1",
    "etc/apk/keys",
    "usr/share/qemu/edk2-aarch64-code.fd",
]
LIB_DIRS = ["lib", "usr/lib"]
EM_AARCH64 = 183
PT_INTERP = 3
PT_DYNAMIC = 2
DT_NEEDED = 1
DT_STRTAB = 5
PT_LOAD = 1


def resolve(root, rel):
    """Resolve a path inside root, following symlinks as the guest would."""
    parts = [p for p in rel.strip("/").split("/") if p]
    current = []
    hops = 0
    while parts:
        part = parts.pop(0)
        if part == ".":
            continue
        if part == "..":
            if current:
                current.pop()
            continue
        current.append(part)
        host = os.path.join(root, *current)
        if os.path.islink(host):
            hops += 1
            if hops > 40:
                raise RuntimeError(f"symlink loop at /{'/'.join(current)}")
            target = os.readlink(host)
            current.pop()
            if target.startswith("/"):
                current = []
            parts = [p for p in target.split("/") if p] + parts
    return os.path.join(root, *current)


def read_elf(path):
    with open(path, "rb") as f:
        data = f.read()
    if data[:4] != b"\x7fELF":
        raise ValueError("not an ELF file")
    if data[4] != 2 or data[5] != 1:
        raise ValueError("not ELF64 little-endian")
    machine = struct.unpack_from("<H", data, 18)[0]
    phoff = struct.unpack_from("<Q", data, 32)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 54)
    interp = None
    dynamic = None
    loads = []
    for i in range(phnum):
        off = phoff + i * phentsize
        p_type, _flags, p_offset, p_vaddr, _paddr, p_filesz = struct.unpack_from("<IIQQQQ", data, off)
        if p_type == PT_INTERP:
            interp = data[p_offset:p_offset + p_filesz].rstrip(b"\0").decode()
        elif p_type == PT_DYNAMIC:
            dynamic = (p_offset, p_filesz)
        elif p_type == PT_LOAD:
            loads.append((p_vaddr, p_offset, p_filesz))
    needed = []
    if dynamic:
        entries = []
        strtab = None
        for j in range(0, dynamic[1], 16):
            tag, val = struct.unpack_from("<qQ", data, dynamic[0] + j)
            if tag == 0:
                break
            if tag == DT_NEEDED:
                entries.append(val)
            elif tag == DT_STRTAB:
                strtab = val
        if strtab is not None:
            base = None
            for vaddr, offset, size in loads:
                if vaddr <= strtab < vaddr + size:
                    base = strtab - vaddr + offset
            if base is None:
                raise ValueError("cannot map DT_STRTAB")
            for e in entries:
                end = data.index(b"\0", base + e)
                needed.append(data[base + e:end].decode())
    return machine, interp, needed


def find_library(root, name):
    for d in LIB_DIRS:
        candidate = resolve(root, f"{d}/{name}")
        if os.path.isfile(candidate):
            return candidate
    return None


def main():
    if len(sys.argv) != 2:
        print(__doc__)
        return 2
    root = os.path.abspath(sys.argv[1])
    errors = []
    for rel in REQUIRED_FILES:
        if not os.path.lexists(os.path.join(root, rel)):
            errors.append(f"missing /{rel}")
    seen = {}
    queue = []
    for rel in REQUIRED_EXECUTABLES:
        host = resolve(root, rel)
        if not os.path.isfile(host):
            errors.append(f"missing executable /{rel}")
            continue
        if not os.access(host, os.X_OK):
            errors.append(f"/{rel} is not executable")
        queue.append((rel, host))
    while queue:
        rel, host = queue.pop()
        if host in seen:
            continue
        try:
            machine, interp, needed = read_elf(host)
        except (ValueError, OSError) as e:
            errors.append(f"/{rel}: {e}")
            continue
        seen[host] = rel
        if machine != EM_AARCH64:
            errors.append(f"/{rel}: e_machine {machine}, expected AArch64 ({EM_AARCH64})")
        if interp:
            if interp != "/lib/ld-musl-aarch64.so.1":
                errors.append(f"/{rel}: unexpected interpreter {interp} (glibc binaries cannot run here)")
            elif not os.path.isfile(resolve(root, interp)):
                errors.append(f"/{rel}: interpreter {interp} missing")
        for lib in needed:
            if lib == "libc.musl-aarch64.so.1":
                lib_host = resolve(root, "lib/ld-musl-aarch64.so.1")
                if not os.path.isfile(lib_host):
                    errors.append(f"/{rel}: needs {lib}, missing")
                continue
            found = find_library(root, lib)
            if found is None:
                errors.append(f"/{rel}: needs {lib}, not found in rootfs")
            else:
                queue.append((os.path.relpath(found, root), found))
    print(f"Checked {len(seen)} ELF objects under {root}")
    if errors:
        for e in errors:
            print("ERROR:", e)
        return 1
    print("Runtime rootfs verification passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
