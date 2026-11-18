#!/usr/bin/env python3
"""Read members of a large remote zip over HTTP range requests (or a local zip).

Both firmware packages are far bigger than the disk a CI runner has free,
and the port only needs some of their members, so nothing downloads the
whole archive. zipfile checks each member's CRC-32 as it is read, so a
corrupted transfer raises instead of producing a bad image.

  remote_zip.py list    <url>
  remote_zip.py get     <url> <out-dir> <member>...       # writes <out-dir>/<basename>
  remote_zip.py sdat    <url> <out-dir> <partition>...    # <p>.new.dat + <p>.transfer.list -> <out-dir>/<p>.img
  remote_zip.py mirrors <url>                             # SourceForge mirror URLs, one per line

<url> may also be a local zip file.
"""
import io
import os
import shutil
import sys
import time
import urllib.parse
import urllib.request
import zipfile

CHUNK = 8 << 20
MIN_RATE = 256 << 10  # bytes/s; slower streams are reopened


SF_MIRRORS = ["gigenet", "deac-riga", "deac-fra", "altushost-net", "razaoinfo", "netix",
              "freefr", "phoenixnap", "cytranet-dal"]


def pick_sourceforge_mirror(url):
    """SourceForge mirrors differ by 10x in speed and change hour to hour.
    For a downloads.sourceforge.net URL without use_mirror=, time a 4 MiB
    range from each mirror and use the fastest."""
    parts = urllib.parse.urlsplit(url)
    if parts.hostname != "downloads.sourceforge.net" or "use_mirror=" in parts.query:
        return url
    best, best_rate = url, 0.0
    for m in SF_MIRRORS:
        cand = urllib.parse.urlunsplit(parts._replace(query=f"use_mirror={m}"))
        try:
            t = time.time()
            req = urllib.request.Request(cand, headers={"Range": "bytes=1000000000-1004194303"})
            with urllib.request.urlopen(req, timeout=20) as r:
                n = len(r.read())
            rate = n / max(time.time() - t, 1e-3) / (1 << 20)
        except Exception as e:
            print(f"mirror {m}: {e}", file=sys.stderr)
            continue
        print(f"mirror {m}: {rate:.1f} MiB/s", file=sys.stderr)
        if n == 4 << 20 and rate > best_rate:
            best, best_rate = cand, rate
    print(f"using {best}", file=sys.stderr)
    return best


class HttpFile(io.RawIOBase):
    def __init__(self, url):
        url = pick_sourceforge_mirror(url)
        self.url, self.pos = url, 0
        for attempt in range(1, 7):
            try:
                req = urllib.request.Request(url, method="HEAD")
                with urllib.request.urlopen(req, timeout=60) as r:
                    # Pin the mirror a redirect picked, so every range comes from one file.
                    self.url = r.geturl()
                    self.size = int(r.headers["Content-Length"])
                    ranges = r.headers.get("Accept-Ranges", "bytes")
                break
            except Exception as e:
                if attempt == 6:
                    raise
                print(f"HEAD retry {attempt} after {e}", file=sys.stderr)
                time.sleep(2 ** attempt)
        if ranges != "bytes":
            raise SystemExit(f"server does not support range requests: {url}")

    def readable(self):
        return True

    def seekable(self):
        return True

    def tell(self):
        return self.pos

    def seek(self, off, whence=0):
        self.pos = {0: off, 1: self.pos + off, 2: self.size + off}[whence]
        return self.pos  # a stream at another offset is reopened on the next read

    def _open_stream(self):
        """One open-ended range request from the current position. Reads that
        continue where the last one stopped keep using it: a member is then
        fetched as one long stream, which mirrors serve ~10x faster than a
        new request per chunk."""
        self._close_stream()
        req = urllib.request.Request(self.url, headers={"Range": f"bytes={self.pos}-"})
        self._resp = urllib.request.urlopen(req, timeout=60)
        if self._resp.status != 206:
            raise IOError(f"server ignored the range request (HTTP {self._resp.status})")
        self._resp_pos = self.pos
        self._opened_at, self._opened_pos = time.time(), self.pos

    def _close_stream(self):
        if getattr(self, "_resp", None) is not None:
            self._resp.close()
        self._resp, self._resp_pos = None, -1

    def readinto(self, buf):
        n = min(len(buf), self.size - self.pos)
        if n <= 0:
            return 0
        view = memoryview(buf)[:n]
        done, failures = 0, 0
        while done < n:
            try:
                if getattr(self, "_resp", None) is None or self._resp_pos != self.pos:
                    self._open_stream()
                got = self._resp.readinto(view[done:])
                if not got:
                    raise IOError("connection closed early")
                done += got
                self.pos += got
                self._resp_pos += got
                # A connection that trickles (a throttled mirror) never trips the
                # socket timeout; reopen it if it averages under MIN_RATE.
                age = time.time() - self._opened_at
                if age > 90 and (self.pos - self._opened_pos) / age < MIN_RATE:
                    raise IOError(f"stream slower than {MIN_RATE >> 10} KiB/s for {age:.0f}s")
            except Exception as e:  # network hiccup: reopen at the current position
                self._close_stream()
                failures += 1
                if failures > 8:
                    raise
                print(f"retry {failures} at byte {self.pos} after {e}", file=sys.stderr)
                time.sleep(min(2 ** failures, 60))
        return n


def open_zip(url):
    if os.path.isfile(url):  # a local copy (port.sh may download the donor whole)
        return zipfile.ZipFile(url)
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
            total = sum(e - b for b, e in news) * 4096
            copied, t0, next_report = 0, time.time(), 512 << 20
            for start, end in news:
                d.seek(start * 4096)
                left = (end - start) * 4096
                while left:
                    b = s.read(min(left, CHUNK))
                    if not b:
                        raise SystemExit(f"{p}.new.dat ended early")
                    d.write(b)
                    left -= len(b)
                    copied += len(b)
                    if copied >= next_report:  # mirror speed varies a lot; log it
                        rate = copied / max(time.time() - t0, 1e-3) / (1 << 20)
                        print(f"  {p}: {copied >> 20}/{total >> 20} MiB at {rate:.1f} MiB/s", flush=True)
                        next_report += 512 << 20
            if s.read(1):
                raise SystemExit(f"{p}.new.dat has data past its transfer list")
        os.replace(dst + ".part", dst)
        print("done", p, max_block * 4096, flush=True)


def mirror_urls(url):
    """Every SourceForge mirror URL for a downloads.sourceforge.net link, for
    a multi-source downloader; any other URL as is."""
    parts = urllib.parse.urlsplit(url)
    if parts.hostname != "downloads.sourceforge.net" or "use_mirror=" in parts.query:
        return [url]
    return [urllib.parse.urlunsplit(parts._replace(query=f"use_mirror={m}")) for m in SF_MIRRORS]


def main():
    if len(sys.argv) < 3:
        raise SystemExit(__doc__)
    cmd, url = sys.argv[1], sys.argv[2]
    if cmd == "mirrors":
        print("\n".join(mirror_urls(url)))
        return
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
