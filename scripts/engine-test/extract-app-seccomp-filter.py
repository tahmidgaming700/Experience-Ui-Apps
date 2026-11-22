#!/usr/bin/env python3
"""Extract Android's arm64 app seccomp table from a device's libandroid_runtime.so.

Zygote installs a generated allowlist into every app process; anything else
gets SECCOMP_RET_TRAP (SIGSYS). This finds the generated BPF tables in the
library, evaluates each one, picks the arm64 *app* table (the one that
traps setuid and mount, unlike the system table) and writes a C header for
applock.c. It also prints which syscalls the table traps.

Usage: extract-app-seccomp-filter.py libandroid_runtime.so app_filter_arm64.h
(get the library with: adb pull /system/lib64/libandroid_runtime.so)
"""
import struct
import sys

OK_CODES = {0x15, 0x35, 0x25, 0x06, 0x05}  # jeq/jge/jgt k, ret k, ja
ALLOW = 0x7FFF0000
SETUID_ARM64, MOUNT_ARM64, ARM_PRIVATE = 146, 40, 0xF0002


def tables(data):
    off = 0
    while off + 8 <= len(data):
        i, insns = off, []
        while i + 8 <= len(data):
            ins = struct.unpack_from("<HBBI", data, i)
            if ins[0] not in OK_CODES:
                break
            insns.append(ins)
            i += 8
        if len(insns) >= 20:
            start = 0
            for j, ins in enumerate(insns):
                if ins[0] == 0x06 and ins[3] == ALLOW:
                    if j - start >= 10:
                        yield off + 8 * start, insns[start:j + 1]
                    start = j + 1
            off = i
        else:
            off += 8


def allowed(insns, nr):
    pc = 0
    while pc < len(insns):
        code, jt, jf, k = insns[pc]
        if code == 0x06:
            return k == ALLOW
        if code == 0x05:
            pc += 1 + k
            continue
        cond = {0x15: nr == k, 0x35: nr >= k, 0x25: nr > k}[code]
        pc += 1 + (jt if cond else jf)
    return False  # past the table: the caller appends SECCOMP_RET_TRAP


def main():
    data = open(sys.argv[1], "rb").read()
    picked = None
    for off, insns in tables(data):
        if allowed(insns, ARM_PRIVATE):
            continue  # 32-bit ARM table
        if not allowed(insns, SETUID_ARM64) and not allowed(insns, MOUNT_ARM64):
            picked = (off, insns)
    if not picked:
        sys.exit("arm64 app table not found")
    off, insns = picked
    trapped = [nr for nr in range(0, 480) if not allowed(insns, nr)]
    print(f"arm64 app table at {off:#x}: {len(insns)} instructions; traps {len(trapped)} syscall numbers < 480:")
    print(" ".join(map(str, trapped)))
    rows = "\n".join(f"  {{0x{c:02x}, {jt}, {jf}, 0x{k:08x}}}," for c, jt, jf, k in insns)
    with open(sys.argv[2], "w") as f:
        f.write(f"/* arm64 app seccomp table extracted from {sys.argv[1]} at {off:#x}. */\n"
                f"#define APP_TABLE_LEN {len(insns)}\n"
                f"static const struct sock_filter app_table[APP_TABLE_LEN] = {{\n{rows}\n}};\n")


if __name__ == "__main__":
    main()
