#!/usr/bin/env python3
"""Read members of a large remote zip over HTTP range requests.

Both firmware packages are far bigger than the disk a CI runner has free,
and the port only needs some of their members, so nothing downloads the
whole archive. zipfile checks each member's CRC-32 as it is read, so a
corrupted transfer raises instead of producing a bad image.

  remote_zip.py list    <url>
  remote_zip.py get     <url> <out-dir> <member>...       # writes <out-dir>/<basename>
  remote_zip.py sdat    <url> <out-dir> <partition>...    # <p>.new.dat + <p>.transfer.list -> <out-dir>/<p>.img
"""
import io
import os
import shutil
import sys
import time
import urllib.request
import zipfile

CHUNK = 8 << 20


class HttpFile(io.RawIOBase):
    def __init__(self, url):
        self.url, self.pos = url, 0
        req = urllib.request.Request(url, method="HEAD")
        with urllib.request.urlopen(req, timeout=120) as r:
            # Pin the mirror a redirect picked, so every range comes from one file.
            self.url = r.geturl()
            self.size = int(r.headers["Content-Length"])
            if r.headers.get("Accept-Ranges", "bytes") != "bytes":
                raise SystemExit(f"server does not support range requests: {url}")

    def readable(self):
        return True

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, off, whence=0):
        self.pos = {0: off, 1: self.pos + off, 2: self.size + off}[whence]
        return self.pos

    def readinto(self, buf):
        n = min(len(buf), self.size - self.pos)
        if n <= 0:
            return 0
        rng = f"bytes={self.pos}-{self.pos + n - 1}"
        for attempt in range(6):
            try:
                req = urllib.request.Request(self.url, headers={"Range": rng})
                with urllib.request.urlopen(req, timeout=300) as r:
                    data = r.read()
                if len(data) != n:
                    raise IOError(f"short range read {len(data)}/{n}")
                break
            except Exception as e:  # network hiccup: back off and retry
                if attempt == 5:
                    raise
                print(f"retry {attempt + 1} after {e}", file=sys.stderr)
                time.sleep(2 ** attempt)
        buf[:n] = data
        self.pos += n
        return n


def open_zip(url):
    return zipfile.ZipFile(io.BufferedReader(HttpFile(url), buffer_size=CHUNK))


def cmd_list(z):
    for i in z.infolist():
        print(f"{i.file_size:>14} {i.filename}")


def cmd_get(z, out, names):
    for name in names:
        dst = os.path.join(out, os.path.basename(name))
        with z.open(name) as s, open(dst + ".part", "wb") as d:
            shutil.copyfileobj(s, d, CHUNK)
        os.replace(dst + ".part", dst)
        print("got", name, flush=True)


def parse_transfer_list(text):
    """Return (block_count, [(start, end), ...] of 'new' ranges in data order)."""
    lines = text.splitlines()
    version = int(lines[0])
    body = lines[4:] if version >= 2 else lines[2:]
    news, max_block = [], 0
    for line in body:
        if not line.strip():
            continue
        cmd, ranges = line.split(" ", 1)
        nums = [int(x) for x in ranges.split(",")]
        if nums[0] != len(nums) - 1:
            raise SystemExit(f"malformed range set: {line[:80]}")
        pairs = list(zip(nums[1::2], nums[2::2]))
        max_block = max([max_block] + [e for _, e in pairs])
        if cmd == "new":
            news += pairs
        elif cmd not in ("erase", "zero"):
            raise SystemExit(f"unsupported transfer command in a full OTA: {cmd}")
    return max_block, news


def cmd_sdat(z, out, parts):
    for p in parts:
        max_block, news = parse_transfer_list(z.read(f"{p}.transfer.list").decode())
        dst = os.path.join(out, f"{p}.img")
        with z.open(f"{p}.new.dat") as s, open(dst + ".part", "wb") as d:
            d.truncate(max_block * 4096)  # 'zero'/'erase' blocks stay sparse zeros
            for start, end in news:
                d.seek(start * 4096)
                left = (end - start) * 4096
                while left:
                    b = s.read(min(left, CHUNK))
                    if not b:
                        raise SystemExit(f"{p}.new.dat ended early")
                    d.write(b)
                    left -= len(b)
            if s.read(1):
                raise SystemExit(f"{p}.new.dat has data past its transfer list")
        os.replace(dst + ".part", dst)
        print("done", p, max_block * 4096, flush=True)


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    cmd, url = sys.argv[1], sys.argv[2]
    z = open_zip(url)
    if cmd == "list":
        cmd_list(z)
    elif cmd == "get":
        cmd_get(z, sys.argv[3], sys.argv[4:])
    elif cmd == "sdat":
        cmd_sdat(z, sys.argv[3], sys.argv[4:])
    else:
        raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
