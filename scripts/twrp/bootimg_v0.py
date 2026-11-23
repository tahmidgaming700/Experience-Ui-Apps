#!/usr/bin/env python3
"""Repack an Android boot image (header v0) with a new ramdisk, keeping everything else.

The original image's header (load addresses, page size, cmdline, os_version, name) and
kernel are reused unchanged; only ramdisk_size and the header id (SHA-1 over kernel,
ramdisk, second and their sizes, as mkbootimg computes it) are updated. A Samsung
'SEANDROIDENFORCE' footer present on the original is re-appended.

  bootimg_v0.py repack ORIGINAL.img NEW_RAMDISK OUT.img
  bootimg_v0.py info ORIGINAL.img
"""
import hashlib
import struct
import sys

HDR = struct.Struct("<8s10I16s512s32s1024s")  # v0 header


def parse(img):
    f = HDR.unpack_from(img, 0)
    magic, ksz, kaddr, rsz, raddr, ssz, saddr, tags, page, hv, osv, name, cmd, ident, xcmd = f
    if magic != b"ANDROID!" or hv != 0:
        sys.exit(f"not a v0 boot image (magic={magic!r} header_version={hv})")
    return dict(ksz=ksz, rsz=rsz, ssz=ssz, page=page, fields=list(f))


def pad(n, page):
    return (page - n % page) % page


def layout(img, h):
    p = h["page"]
    off = p
    k = img[off:off + h["ksz"]]; off += h["ksz"] + pad(h["ksz"], p)
    r = img[off:off + h["rsz"]]; off += h["rsz"] + pad(h["rsz"], p)
    s = img[off:off + h["ssz"]]; off += h["ssz"] + pad(h["ssz"], p)
    return k, r, s, off


def build(orig, ramdisk):
    h = parse(orig)
    k, _r, s, end = layout(orig, h)
    tail = orig[end:]
    footer = b"SEANDROIDENFORCE" if tail.startswith(b"SEANDROIDENFORCE") else b""
    if tail.rstrip(b"\0") not in (b"", b"SEANDROIDENFORCE"):
        sys.exit(f"unexpected {len(tail)} trailing bytes after the image payload")
    f = h["fields"]
    f[3] = len(ramdisk)
    sha = hashlib.sha1()
    for blob in (k, ramdisk, s):
        sha.update(blob)
        sha.update(struct.pack("<I", len(blob)))
    f[13] = sha.digest().ljust(32, b"\0")
    p = h["page"]
    hdr = HDR.pack(*f)
    out = hdr + b"\0" * pad(len(hdr), p)
    for blob in (k, ramdisk, s):
        out += blob + b"\0" * pad(len(blob), p)
    return out + footer


def main():
    if len(sys.argv) >= 3 and sys.argv[1] == "info":
        img = open(sys.argv[2], "rb").read(); h = parse(img); k, r, s, end = layout(img, h)
        print(f"page={h['page']} kernel={h['ksz']} ramdisk={h['rsz']} second={h['ssz']} payload_end={end} file={len(img)} tail={img[end:end+16]!r}")
        return
    if len(sys.argv) != 5 or sys.argv[1] != "repack":
        sys.exit(__doc__)
    orig = open(sys.argv[2], "rb").read()
    out = build(orig, open(sys.argv[3], "rb").read())
    open(sys.argv[4], "wb").write(out)
    print(f"wrote {sys.argv[4]}: {len(out)} bytes ({len(out)/2**20:.2f} MiB)")


if __name__ == "__main__":
    main()
