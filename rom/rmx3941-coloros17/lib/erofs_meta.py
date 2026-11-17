#!/usr/bin/env python3
"""Read per-file metadata (mode, uid, gid, xattrs) from an EROFS image.

fsck.erofs --extract (erofs-utils 1.7) restores owners and modes but drops
xattrs, so the SELinux label of every file would be lost on repack. This
reads them straight from the on-disk format. It parses metadata only:
inodes, xattrs and directories, which EROFS never compresses. File data is
left to fsck.erofs.

  erofs_meta.py dump <image> <out.tsv>
      one line per path: path<TAB>mode(octal)<TAB>uid<TAB>gid<TAB>xattrs
      xattrs is ';'-joined name=hexvalue pairs
  erofs_meta.py verify <image> <expected.tsv>
      exit 1 if any path's metadata differs or a path is missing/extra
"""
import struct
import sys

EROFS_MAGIC = 0xE0F5E1E2
XATTR_PREFIX = {1: "user.", 2: "system.posix_acl_access", 3: "system.posix_acl_default",
                4: "trusted.", 6: "security."}
FT_DIR = 2


class Erofs:
    def __init__(self, path):
        self.f = open(path, "rb")
        sb = self._read(1024, 128)
        magic, = struct.unpack_from("<I", sb, 0)
        if magic != EROFS_MAGIC:
            raise SystemExit(f"{path}: not an EROFS image")
        self.blkszbits = sb[12]
        self.bs = 1 << self.blkszbits
        self.root_nid, = struct.unpack_from("<H", sb, 14)
        self.meta_blkaddr, self.xattr_blkaddr = struct.unpack_from("<II", sb, 40)

    def _read(self, off, n):
        self.f.seek(off)
        b = self.f.read(n)
        if len(b) != n:
            raise SystemExit("short read in image")
        return b

    def inode(self, nid):
        off = self.meta_blkaddr * self.bs + nid * 32
        hdr = self._read(off, 64)
        fmt, xcount, mode = struct.unpack_from("<HHH", hdr, 0)
        extended = fmt & 1
        layout = (fmt >> 1) & 7
        if extended:
            size, = struct.unpack_from("<Q", hdr, 8)
            raw, = struct.unpack_from("<I", hdr, 16)
            uid, gid = struct.unpack_from("<II", hdr, 24)
            isize = 64
        else:
            size, = struct.unpack_from("<I", hdr, 8)
            raw, = struct.unpack_from("<I", hdr, 16)
            uid, gid = struct.unpack_from("<HH", hdr, 24)
            isize = 32
        xsize = 12 + (xcount - 1) * 4 if xcount else 0
        xattrs = self._xattrs(off + isize, xsize) if xsize else {}
        return dict(mode=mode, uid=uid, gid=gid, size=size, layout=layout, raw=raw,
                    meta_end=off + isize + xsize, xattrs=xattrs)

    def _entry(self, buf, pos):
        nlen, nidx, vsize = struct.unpack_from("<BBH", buf, pos)
        if nidx & 0x80:
            raise SystemExit("long xattr name prefixes are not supported")
        name = XATTR_PREFIX.get(nidx, f"?{nidx}.") + buf[pos + 4:pos + 4 + nlen].decode()
        val = buf[pos + 4 + nlen:pos + 4 + nlen + vsize]
        return name, val, (4 + nlen + vsize + 3) & ~3

    def _xattrs(self, off, xsize):
        buf = self._read(off, xsize)
        shared = buf[4]
        out = {}
        for i in range(shared):
            sid, = struct.unpack_from("<I", buf, 12 + 4 * i)
            sb = self._read(self.xattr_blkaddr * self.bs + sid * 4, 4 + 255 + 65536)
            n, v, _ = self._entry(sb, 0)
            out[n] = v
        pos = 12 + 4 * shared
        while pos + 4 <= xsize:
            n, v, step = self._entry(buf, pos)
            out[n] = v
            pos += step
        return out

    def dir_data(self, ino):
        size, layout = ino["size"], ino["layout"]
        if layout == 0:      # flat plain
            return self._read(ino["raw"] * self.bs, size)
        if layout == 2:      # flat inline: full blocks, then a tail right after the inode
            full = (size // self.bs) * self.bs
            head = self._read(ino["raw"] * self.bs, full) if full else b""
            return head + self._read(ino["meta_end"], size - full)
        raise SystemExit(f"directory with unsupported layout {layout}")

    def entries(self, ino):
        data = self.dir_data(ino)
        for b in range(0, len(data), self.bs):
            blk = data[b:b + self.bs]
            first_off, = struct.unpack_from("<H", blk, 8)
            count = first_off // 12
            for i in range(count):
                nid, noff, ftype = struct.unpack_from("<QHB", blk, i * 12)
                end = struct.unpack_from("<H", blk, (i + 1) * 12 + 8)[0] if i + 1 < count else len(blk)
                name = blk[noff:end].split(b"\0", 1)[0].decode("utf-8", "surrogateescape")
                if name not in (".", ".."):
                    yield name, nid, ftype

    def walk(self):
        stack = [("", self.root_nid)]
        while stack:
            path, nid = stack.pop()
            ino = self.inode(nid)
            yield path or "/", ino
            if (ino["mode"] & 0o170000) == 0o040000:
                for name, cnid, _ in self.entries(ino):
                    stack.append((f"{path}/{name}", cnid))


def fmt_line(path, ino):
    xs = ";".join(f"{k}={v.hex()}" for k, v in sorted(ino["xattrs"].items()))
    return f"{path}\t{ino['mode'] & 0o7777 | (ino['mode'] & 0o170000):o}\t{ino['uid']}\t{ino['gid']}\t{xs}"


def load_tsv(path):
    out = {}
    with open(path, encoding="utf-8", errors="surrogateescape") as f:
        for line in f:
            p, rest = line.rstrip("\n").split("\t", 1)
            out[p] = rest
    return out


def main():
    if len(sys.argv) != 4 or sys.argv[1] not in ("dump", "verify"):
        raise SystemExit(__doc__)
    fs = Erofs(sys.argv[2])
    if sys.argv[1] == "dump":
        with open(sys.argv[3], "w", encoding="utf-8", errors="surrogateescape") as o:
            for path, ino in sorted(fs.walk(), key=lambda t: t[0]):
                o.write(fmt_line(path, ino) + "\n")
        return
    want = load_tsv(sys.argv[3])
    got = {}
    for path, ino in fs.walk():
        got[path] = fmt_line(path, ino).split("\t", 1)[1]
    def norm(rest):
        # Symlink permission bits are never checked by the kernel, and Linux
        # cannot chmod a symlink, so only their type is compared.
        if rest is None or not rest.startswith("120"):
            return rest
        mode, tail = rest.split("\t", 1)
        return "120000\t" + tail

    bad = 0
    for p in sorted(set(want) | set(got)):
        if norm(want.get(p)) != norm(got.get(p)):
            bad += 1
            if bad <= 20:
                print(f"MISMATCH {p}\n  want {want.get(p)}\n  got  {got.get(p)}")
    print(f"verify: {len(got)} paths, {bad} mismatches")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
