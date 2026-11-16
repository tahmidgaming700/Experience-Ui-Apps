#!/usr/bin/env python3
"""Build Experience UI resource overlays as standalone RRO APKs.

When Experience UI is built from source, rom/vendor/experience/overlay/static is
compiled straight into framework-res, SystemUI, Settings and Launcher3. When it is
added to a prebuilt GSI instead (scripts/gsi/repack-gsi.py), the same resources ship
as *static* runtime resource overlays in /system/product/overlay: enabled from first
boot, not user-switchable. On Android 11, framework-res, SystemUI, Settings and
Launcher3 declare no <overlayable>, so preinstalled overlays may change any of their
resources (idmap2 CheckOverlayable).

The accent colour overlays (rom/vendor/experience/overlay/accents) are built as normal,
switchable overlays in category android.theme.customization.accent_color.

  build-overlays.py --out DIR --android-jar platform-30/android.jar --aapt2 PATH
                    --key platform.pk8 --cert platform.x509.pem --apksigner PATH --zipalign PATH
                    [--drop RESOURCE_NAME ...]

--drop removes resources from the overlays (e.g. config_defaultSms when the base image
ships a fuller SMS app).
"""
import argparse
import glob
import os
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OVERLAY = os.path.join(REPO, "rom", "vendor", "experience", "overlay")

# overlay source dir (relative to overlay/static) -> (target package, overlay module name, overlay package)
STATIC_TARGETS = {
    "frameworks/base/core/res/res": ("android", "ExperienceOverlayFramework", "com.experience.overlay.framework"),
    "frameworks/base/packages/SystemUI/res": ("com.android.systemui", "ExperienceOverlaySystemUI", "com.experience.overlay.systemui"),
    "packages/apps/Settings/res": ("com.android.settings", "ExperienceOverlaySettings", "com.experience.overlay.settings"),
    "packages/apps/Trebuchet/quickstep/res": ("com.android.launcher3", "ExperienceOverlayLauncher3", "com.experience.overlay.launcher3"),
}
# Higher than the overlays a prebuilt LineageOS/GApps GSI already enables (priority 0-1).
STATIC_PRIORITY = 10

LICENSE = "<!-- Copyright (C) 2026 The Experience UI Project. Licensed under the Apache License, Version 2.0. -->"


def run(cmd):
    subprocess.run(cmd, check=True)


def filtered_res(src, dst, drop):
    """Copy a res dir, removing <item>s whose name is in drop; return number of resources kept."""
    kept = 0
    for path in glob.glob(os.path.join(src, "**", "*"), recursive=True):
        if os.path.isdir(path):
            continue
        rel = os.path.relpath(path, src)
        out = os.path.join(dst, rel)
        os.makedirs(os.path.dirname(out), exist_ok=True)
        if rel.startswith("values") and path.endswith(".xml"):
            tree = ET.parse(path)
            root = tree.getroot()
            for el in list(root):
                if el.get("name") in drop:
                    root.remove(el)
                else:
                    kept += 1
            if len(root):
                tree.write(out, encoding="utf-8", xml_declaration=True)
        else:
            stem = os.path.splitext(os.path.basename(path))[0]
            if stem not in drop:
                shutil.copyfile(path, out)
                kept += 1
    return kept


def build(a, name, package, manifest_xml, res_dir, work):
    mdir = os.path.join(work, name)
    os.makedirs(mdir, exist_ok=True)
    manifest = os.path.join(mdir, "AndroidManifest.xml")
    with open(manifest, "w") as f:
        f.write(manifest_xml)
    flat = os.path.join(mdir, "res.zip")
    run([a.aapt2, "compile", "--dir", res_dir, "-o", flat])
    unsigned = os.path.join(mdir, "unsigned.apk")
    run([a.aapt2, "link", "-o", unsigned, "--manifest", manifest, "-I", a.android_jar,
         "--min-sdk-version", "30", "--target-sdk-version", "30",
         "--version-code", "10000", "--version-name", "1.0.0", flat])
    aligned = os.path.join(mdir, "aligned.apk")
    run([a.zipalign, "-p", "-f", "4", unsigned, aligned])
    out = os.path.join(a.out, name + ".apk")
    run([a.apksigner, "sign", "--v4-signing-enabled", "false", "--key", a.key, "--cert", a.cert, "--out", out, aligned])
    print(f"{name}: {package}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    for opt in ("out", "android-jar", "aapt2", "key", "cert", "apksigner", "zipalign"):
        ap.add_argument("--" + opt, required=True)
    ap.add_argument("--drop", action="append", default=[])
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    drop = set(a.drop)
    with tempfile.TemporaryDirectory() as work:
        for rel, (target, name, package) in STATIC_TARGETS.items():
            res = os.path.join(work, name + "-res")
            if filtered_res(os.path.join(OVERLAY, "static", rel), res, drop) == 0:
                continue
            manifest = f'''<?xml version="1.0" encoding="utf-8"?>
{LICENSE}
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}">
    <overlay android:targetPackage="{target}" android:isStatic="true" android:priority="{STATIC_PRIORITY}" />
    <application android:hasCode="false" />
</manifest>
'''
            build(a, name, package, manifest, res, work)
        for d in sorted(glob.glob(os.path.join(OVERLAY, "accents", "ExperienceAccent*"))):
            name = os.path.basename(d)
            src_manifest = open(os.path.join(d, "AndroidManifest.xml")).read()
            package = re.search(r'package="([^"]+)"', src_manifest).group(1)
            build(a, name, package, src_manifest, os.path.join(d, "res"), work)


if __name__ == "__main__":
    sys.exit(main())
