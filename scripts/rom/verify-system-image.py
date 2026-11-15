#!/usr/bin/env python3
"""Static validation of an Experience UI system image (no boot involved).

Checks, and records in a JSON report:
  * image format: Android sparse or raw ext4; ext4 superblock, block size, size
  * AVB: presence of an AVB footer (hashtree) at the end of the image
  * system-as-root layout: / holds init, the system/ directory and system/build.prop
  * build properties: Android 11 / SDK 30 / arm64 ABI list, Experience identity props,
    display ID, default sounds; the security patch level is reported, never assumed
  * Experience apps installed in the right partition directories; replaced apps absent
  * permissions/sysconfig XML, boot animation, wallpapers, sounds, accent overlays

This is image validation only. It says nothing about whether the image boots on a
device; see docs/compatibility.md for boot-test evidence.

  verify-system-image.py system.img [--report report.json] [--expect-build-number N]

Requires debugfs (e2fsprogs). Exit status is non-zero if any check fails.
"""
import argparse
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile

SPARSE_MAGIC = 0xED26FF3A
EXT4_MAGIC = 0xEF53

PRIV_APPS = ["ExperienceLauncher", "ExperienceSettings", "ExperienceSetupWizard", "ExperienceDialer",
             "ExperienceMessages", "ExperienceFiles"]
APPS = ["ExperienceContacts", "ExperienceNotes", "ExperienceClock", "ExperienceCalendar",
        "ExperienceCalculator", "ExperienceGallery", "ExperienceCamera", "ExperienceConverter"]
REPLACED = ["LineageSetupWizard", "Provision", "Updater", "Contacts", "DeskClock", "Etar", "ExactCalculator"]
ACCENTS = ["Indigo", "Azure", "Teal", "Verdant", "Amber", "Coral", "Rose", "Violet", "Graphite"]


class Report:
    def __init__(self):
        self.checks = []
        self.info = {}

    def check(self, name, ok, detail=""):
        self.checks.append({"check": name, "ok": bool(ok), "detail": detail})
        mark = "PASS" if ok else "FAIL"
        print(f"[{mark}] {name}" + (f": {detail}" if detail else ""))
        return ok

    @property
    def ok(self):
        return all(c["ok"] for c in self.checks)


def unsparse(src, dst):
    """Convert an Android sparse image to a raw image (format v1.0)."""
    with open(src, "rb") as f, open(dst, "wb") as out:
        hdr = f.read(28)
        magic, major, minor, fhs, chs, blk, total_blks, total_chunks, _crc = struct.unpack("<IHHHHIIII", hdr)
        if magic != SPARSE_MAGIC or major != 1:
            raise ValueError("not an Android sparse image v1")
        f.seek(fhs)
        for _ in range(total_chunks):
            ctype, _res, csz, tsz = struct.unpack("<HHII", f.read(chs)[:12])
            data_len = tsz - chs
            if ctype == 0xCAC1:    # RAW
                remaining = data_len
                while remaining:
                    buf = f.read(min(remaining, 1 << 20))
                    out.write(buf)
                    remaining -= len(buf)
            elif ctype == 0xCAC2:  # FILL
                pattern = f.read(4)
                out.write(pattern * (csz * blk // 4))
            elif ctype == 0xCAC3:  # DONT_CARE
                out.seek(csz * blk, 1)
            elif ctype == 0xCAC4:  # CRC32
                f.read(data_len)
            else:
                raise ValueError(f"unknown chunk type {ctype:#x}")
        out.truncate(total_blks * blk)
    return blk, total_blks


def debugfs(image, command):
    r = subprocess.run(["debugfs", "-R", command, image], capture_output=True, text=True)
    return r.returncode, r.stdout


def exists(image, path):
    rc, out = debugfs(image, f"stat {path}")
    return rc == 0 and "Inode:" in out


def cat(image, path):
    rc, out = debugfs(image, f"cat {path}")
    return out if rc == 0 else None


def parse_props(text):
    props = {}
    for line in (text or "").splitlines():
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            k, v = line.split("=", 1)
            props[k] = v  # later definitions win, like init
    return props


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("image")
    ap.add_argument("--report")
    ap.add_argument("--expect-build-number")
    a = ap.parse_args()
    if shutil.which("debugfs") is None:
        sys.exit("debugfs (e2fsprogs) is required")

    rep = Report()
    size = os.path.getsize(a.image)
    rep.info["image_bytes"] = size
    tmp = tempfile.mkdtemp(prefix="expverify-")
    try:
        with open(a.image, "rb") as f:
            magic = struct.unpack("<I", f.read(4))[0]
        raw = a.image
        if magic == SPARSE_MAGIC:
            raw = os.path.join(tmp, "system.raw.img")
            blk, blocks = unsparse(a.image, raw)
            rep.info["format"] = "android-sparse"
            rep.check("sparse image converts to raw", True, f"{blocks} x {blk} B")
        else:
            rep.info["format"] = "raw"
        with open(raw, "rb") as f:
            f.seek(1024)
            sb = f.read(1024)
        ext4 = struct.unpack_from("<H", sb, 56)[0] == EXT4_MAGIC
        rep.check("ext4 superblock", ext4)
        if not ext4:
            raise SystemExit(1)
        block_size = 1024 << struct.unpack_from("<I", sb, 24)[0]
        blocks = struct.unpack_from("<I", sb, 4)[0]
        rep.info["fs_block_size"] = block_size
        rep.info["fs_bytes"] = block_size * blocks
        rep.check("filesystem block size is 4096", block_size == 4096, str(block_size))

        with open(raw, "rb") as f:
            f.seek(-64, os.SEEK_END)
            footer = f.read(64)
        rep.info["avb_footer"] = footer[:4] == b"AVBf"
        print(f"[INFO] AVB footer present: {rep.info['avb_footer']}")

        # --- system-as-root layout --------------------------------------------------
        rep.check("SAR layout: /system directory at image root", exists(raw, "/system"))
        rep.check("SAR layout: /init at image root", exists(raw, "/init"))
        rep.check("SAR layout: /system/build.prop", exists(raw, "/system/build.prop"))

        props = {}
        for p in ("/system/etc/prop.default", "/system/build.prop", "/system/product/build.prop"):
            text = cat(raw, p)
            rep.check(f"readable {p}", text is not None)
            props.update(parse_props(text))
        info = {k: props.get(k, "") for k in (
            "ro.build.version.release", "ro.build.version.sdk", "ro.build.version.security_patch",
            "ro.build.display.id", "ro.build.fingerprint", "ro.system.build.fingerprint",
            "ro.system.product.cpu.abilist", "ro.product.system.model", "ro.product.system.brand",
            "ro.experience.version", "ro.experience.display.version", "ro.experience.build.number",
            "ro.experience.edition", "ro.experience.base", "ro.lineage.version",
            "ro.config.ringtone", "ro.config.notification_sound", "ro.config.alarm_alert")}
        rep.info["properties"] = info

        rep.check("Android 11", info["ro.build.version.release"] == "11", info["ro.build.version.release"])
        rep.check("SDK 30", info["ro.build.version.sdk"] == "30", info["ro.build.version.sdk"])
        rep.check("arm64 ABI first", info["ro.system.product.cpu.abilist"].startswith("arm64-v8a"),
                  info["ro.system.product.cpu.abilist"])
        rep.check("security patch level recorded", re.fullmatch(r"\d{4}-\d{2}-\d{2}", info["ro.build.version.security_patch"] or "") is not None,
                  info["ro.build.version.security_patch"])
        rep.check("Experience version 1.0.0", info["ro.experience.version"] == "1.0.0", info["ro.experience.version"])
        rep.check("Experience display version", info["ro.experience.display.version"] == "Experience UI 1.0",
                  info["ro.experience.display.version"])
        rep.check("display ID starts with Experience UI 1.0", info["ro.build.display.id"].startswith("Experience UI 1.0"),
                  info["ro.build.display.id"])
        bn = info["ro.experience.build.number"]
        rep.check("Experience build number", re.fullmatch(r"EXP1\.0\.0\.\d{12}\.(userdebug|user|eng)", bn) is not None, bn)
        if a.expect_build_number:
            rep.check("build number matches expected", bn == a.expect_build_number, a.expect_build_number)
        rep.check("system model", info["ro.product.system.model"] == "Experience Device", info["ro.product.system.model"])
        rep.check("edition Vanilla", info["ro.experience.edition"] == "Vanilla")
        rep.check("default ringtone", info["ro.config.ringtone"] == "Experience_Horizon.ogg", info["ro.config.ringtone"])
        rep.check("default notification sound", info["ro.config.notification_sound"] == "Experience_Bloom.ogg",
                  info["ro.config.notification_sound"])
        rep.check("default alarm", info["ro.config.alarm_alert"] == "Experience_Daybreak.ogg", info["ro.config.alarm_alert"])

        # --- applications ---------------------------------------------------------------
        for m in PRIV_APPS:
            rep.check(f"priv-app {m}", exists(raw, f"/system/priv-app/{m}/{m}.apk"))
        for m in APPS:
            rep.check(f"app {m}", exists(raw, f"/system/app/{m}/{m}.apk"))
        for m in REPLACED:
            present = any(exists(raw, f"{d}/{m}") for d in ("/system/app", "/system/priv-app",
                                                             "/system/product/app", "/system/product/priv-app",
                                                             "/system/system_ext/app", "/system/system_ext/priv-app"))
            rep.check(f"replaced app {m} not installed", not present)
        rep.check("Launcher3/Trebuchet kept for recents",
                  any(exists(raw, f"{d}/TrebuchetQuickStep") for d in ("/system/system_ext/priv-app", "/system/product/priv-app", "/system/priv-app")))

        # --- configuration and assets ------------------------------------------------------
        for p in ("/system/etc/permissions/privapp-permissions-experience.xml",
                  "/system/etc/default-permissions/default-permissions-experience.xml",
                  "/system/etc/sysconfig/sysconfig-experience.xml",
                  "/system/media/bootanimation.zip",
                  "/system/product/media/audio/ringtones/Experience_Horizon.ogg",
                  "/system/product/media/audio/notifications/Experience_Bloom.ogg",
                  "/system/product/media/audio/alarms/Experience_Daybreak.ogg",
                  "/system/product/media/experience/wallpapers/experience_bloom.png"):
            rep.check(f"file {p}", exists(raw, p))
        for acc in ACCENTS:
            rep.check(f"accent overlay {acc}", exists(raw, f"/system/product/overlay/ExperienceAccent{acc}/ExperienceAccent{acc}.apk")
                      or exists(raw, f"/system/product/overlay/ExperienceAccent{acc}.apk"))
    finally:
        shutil.rmtree(tmp, ignore_errors=True)

    result = {"image": os.path.basename(a.image), "ok": rep.ok, "info": rep.info, "checks": rep.checks,
              "note": "Static image validation only; not a boot test."}
    if a.report:
        with open(a.report, "w") as f:
            json.dump(result, f, indent=2)
    failed = [c["check"] for c in rep.checks if not c["ok"]]
    print(f"\n{len(rep.checks) - len(failed)}/{len(rep.checks)} checks passed")
    sys.exit(0 if not failed else 1)


if __name__ == "__main__":
    main()
