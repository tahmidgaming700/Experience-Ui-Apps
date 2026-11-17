#!/usr/bin/env python3
"""Rebuild an EROFS partition from an unpacked tree, carrying over SELinux
labels, owners and modes, then check the new image against them.

  repack.py <tree> <out.img> <mount-point> <donor.tsv> [<imports.txt> <base.tsv>] [--extra extra.tsv]

<donor.tsv>/<base.tsv> come from `erofs_meta.py dump` of the original images.
A path takes its metadata from <base.tsv> when it, or a parent directory, is
listed in <imports.txt> (written by patch_port.py for files copied from the
RMX3941 build); otherwise from <donor.tsv>. <extra.tsv> gives metadata for
files that exist in neither image. A path found nowhere fails the build:
an unlabelled file on a read-only partition would be denied at runtime.

The only xattr Android sets on these partitions is security.selinux, and
mkfs.erofs applies it from a file_contexts file. Any other xattr (for
example security.capability) fails the build instead of being dropped.
"""
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from erofs_meta import load_tsv  # noqa: E402

SEL = "security.selinux"


def parse_meta(rest):
    mode, uid, gid, xs = rest.split("\t")
    xattrs = {}
    if xs:
        for kv in xs.split(";"):
            k, v = kv.split("=", 1)
            xattrs[k] = bytes.fromhex(v)
    return int(mode, 8), int(uid), int(gid), xattrs


def walk(tree):
    yield "/"
    for root, dirs, files in os.walk(tree):
        dirs.sort()
        rel = os.path.relpath(root, tree)
        base = "" if rel == "." else "/" + rel
        for n in sorted(dirs) + sorted(files):
            yield f"{base}/{n}"


def main():
    args = sys.argv[1:]
    extra = {}
    if "--extra" in args:
        i = args.index("--extra")
        extra = load_tsv(args[i + 1])
        del args[i:i + 2]
    if len(args) not in (4, 6):
        raise SystemExit(__doc__)
    tree, out, mount = args[0], args[1], args[2]
    donor = load_tsv(args[3])
    imports, base = [], {}
    if len(args) == 6:
        with open(args[4], encoding="utf-8") as f:
            imports = [l.strip() for l in f if l.strip()]
        base = load_tsv(args[5])

    def is_imported(p):
        return any(p == i or p.startswith(i + "/") for i in imports)

    expected, fc_lines, missing = {}, [], []
    for p in walk(tree):
        src = base if is_imported(p) else donor
        rest = extra.get(p) or src.get(p)
        if rest is None:
            missing.append(p)
            continue
        mode, uid, gid, xattrs = parse_meta(rest)
        other = set(xattrs) - {SEL}
        if other:
            raise SystemExit(f"{p}: xattrs {sorted(other)} cannot be carried by this repack")
        fs_path = os.path.join(tree, p.lstrip("/")) if p != "/" else tree
        st = os.lstat(fs_path)
        if (st.st_mode & 0o170000) != (mode & 0o170000):
            raise SystemExit(f"{p}: file type differs from its recorded metadata")
        os.lchown(fs_path, uid, gid)
        if not os.path.islink(fs_path):
            os.chmod(fs_path, mode & 0o7777)
        if SEL in xattrs:
            label = xattrs[SEL].rstrip(b"\0").decode()
            target = mount if p == "/" else mount + p
            fc_lines.append(f"{re.escape(target)} {label}")
        expected[p] = rest
    if missing:
        for p in missing[:30]:
            print("no metadata for", p, file=sys.stderr)
        raise SystemExit(f"{len(missing)} paths have no recorded metadata")

    fc = out + ".file_contexts"
    with open(fc, "w", encoding="utf-8", errors="surrogateescape") as f:
        f.write("\n".join(fc_lines) + "\n")
    exp = out + ".expected.tsv"
    with open(exp, "w", encoding="utf-8", errors="surrogateescape") as f:
        for p in sorted(expected):
            f.write(f"{p}\t{expected[p]}\n")

    if os.path.exists(out):
        os.remove(out)
    # lz4hc only: the RMX3941 runs kernel 5.10, which predates the EROFS
    # lzma, ztailpacking and fragment features.
    subprocess.run(["mkfs.erofs", "-zlz4hc,9", "-T", "1230768000", f"--mount-point={mount}",
                    f"--file-contexts={fc}", out, tree], check=True)
    subprocess.run(["fsck.erofs", out], check=True, stdout=subprocess.DEVNULL)
    subprocess.run([sys.executable, "-I", os.path.join(HERE, "erofs_meta.py"), "verify", out, exp], check=True)


if __name__ == "__main__":
    main()
