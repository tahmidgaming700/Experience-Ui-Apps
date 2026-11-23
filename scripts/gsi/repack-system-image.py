#!/usr/bin/env python3
"""Install Experience UI into a raw ext4 /system image, in place (no resize).

Used for device-specific builds (block-based OTAs for A-only devices such as the
Galaxy Tab S 10.5 / chagalllte), where the image is already a plain raw ext4 that
must keep its exact size so it still fits the device's SYSTEM partition. The GSI path
(scripts/gsi/repack-gsi.py) handles sparse images, growing and dynamic partitions; this
one deliberately does neither.

It reuses repack-gsi.py's debugfs helpers (label/owner/mode copied from a neighbour,
fixed timestamps) and:
  * verifies the image's platform certificate matches the app-signing key,
  * removes the apps Experience replaces (full paths; missing ones skipped),
  * installs the Experience apps (priv-app/app), overlays (product/overlay as <Name>/<Name>.apk),
    permissions, sysconfig, boot animation, wallpapers and sounds,
  * adds ro.experience.* identity and the build display id, and sets the default sounds,
  * keeps the device's own product model/brand (honest identity on real hardware).

  repack-system-image.py --image system.img --apks DIR --overlays DIR --vendor rom/vendor/experience
      --platform-cert platform.x509.pem --apksigner PATH --build-id YYYYMMDDHHMM
      --base-label "..." [--edition Vanilla]
"""
import argparse
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))

# Reuse the GSI repack's Fs/debugfs helpers.
_spec = importlib.util.spec_from_file_location("repack_gsi", os.path.join(HERE, "repack-gsi.py"))
rg = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(rg)

PRIV_APPS = ["ExperienceLauncher", "ExperienceSettings", "ExperienceSetupWizard", "ExperienceDialer"]
APPS = ["ExperienceMessages", "ExperienceFiles", "ExperienceContacts", "ExperienceNotes", "ExperienceClock",
        "ExperienceCalendar", "ExperienceCalculator", "ExperienceGallery", "ExperienceCamera", "ExperienceConverter"]

# Apps replaced by Experience on a LineageOS 18.1 device build (vanilla). Missing paths skip.
REPLACED = [
    "/system/system_ext/priv-app/LineageSetupWizard",
    "/system/priv-app/Provision",
    "/system/product/app/Etar",
    "/system/product/app/DeskClock",
    "/system/product/app/ExactCalculator",
    "/system/product/priv-app/Contacts",
    "/system/product/priv-app/Dialer",
    "/system/system_ext/priv-app/Gallery2",
    "/system/system_ext/priv-app/Camera2",
    "/system/priv-app/Updater",
    "/system/system_ext/priv-app/Updater",
]


def log(m): print(f"[device-repack] {m}", flush=True)


def first_overlay_apk_ref(fs):
    """An existing /system/product/overlay/<Name>/<Name>.apk to copy label/mode from."""
    out = fs.query("ls -p /system/product/overlay")
    for line in out.splitlines():
        parts = line.split("/")
        if len(parts) >= 6 and parts[2].startswith("04") and parts[5] not in (".", ".."):
            name = parts[5]
            apk = f"/system/product/overlay/{name}/{name}.apk"
            if fs.exists(apk):
                return f"/system/product/overlay/{name}", apk
    return None, None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--image", required=True)
    ap.add_argument("--apks", required=True)
    ap.add_argument("--overlays", required=True)
    ap.add_argument("--vendor", default=os.path.join(REPO, "rom/vendor/experience"))
    ap.add_argument("--platform-cert", required=True)
    ap.add_argument("--apksigner", required=True)
    ap.add_argument("--build-id", required=True)
    ap.add_argument("--base-label", required=True)
    ap.add_argument("--edition", default="Vanilla")
    ap.add_argument("--out-manifest", required=True)
    a = ap.parse_args()
    if not re.fullmatch(r"\d{12}", a.build_id):
        sys.exit("--build-id must be YYYYMMDDHHMM")
    for t in ("debugfs", "e2fsck"):
        if shutil.which(t) is None:
            sys.exit(f"{t} required")

    fs = rg.Fs(a.image, os.path.dirname(os.path.abspath(a.image)))
    props = fs.query('cat "/system/build.prop"')
    if "ro.build.version.sdk=30" not in props:
        sys.exit("not an Android 11 system image")
    if "ro.experience." in props:
        sys.exit("image already has Experience properties; use a clean base")

    key_cert = rg.cert_sha256(a.platform_cert)
    fr = os.path.join(fs.work, "framework-res.apk")
    subprocess.run(["debugfs", "-R", f'dump /system/framework/framework-res.apk "{fr}"', a.image],
                   capture_output=True)
    img_cert = rg.cert_sha256(fr, a.apksigner)
    if img_cert != key_cert:
        sys.exit(f"platform cert mismatch: image {img_cert} != app key {key_cert}")
    log(f"platform certificate matches ({img_cert[:16]}...)")

    priv_ref_dir = "/system/priv-app/Shell"
    priv_ref_apk = "/system/priv-app/Shell/Shell.apk"
    app_ref_dir = "/system/app/BasicDreams"
    app_ref_apk = "/system/app/BasicDreams/BasicDreams.apk"
    ov_ref_dir, ov_ref_apk = first_overlay_apk_ref(fs)
    for p in (priv_ref_apk, app_ref_apk, ov_ref_apk):
        if not p or not fs.exists(p):
            sys.exit(f"reference path missing: {p}")

    removed = [p for p in REPLACED if fs.exists(p)]
    for p in removed:
        fs.remove_tree(p)
    fs.commit()
    log("removed: " + ", ".join(os.path.basename(p) for p in removed))

    installed = {}
    import hashlib
    for group, root, rdir, rapk in ((PRIV_APPS, "/system/priv-app", priv_ref_dir, priv_ref_apk),
                                     (APPS, "/system/app", app_ref_dir, app_ref_apk)):
        for m in group:
            apk = os.path.join(a.apks, m + ".apk")
            if not os.path.isfile(apk):
                sys.exit(f"missing {apk}")
            if rg.cert_sha256(apk, a.apksigner) != key_cert:
                sys.exit(f"{m}.apk not signed with the platform key")
            fs.mkdir(f"{root}/{m}", rdir)
            fs.write(apk, f"{root}/{m}/{m}.apk", rapk)
            installed[m] = {"path": f"{root}/{m}/{m}.apk",
                            "sha256": hashlib.sha256(open(apk, "rb").read()).hexdigest()}
    fs.commit()

    overlays = sorted(f for f in os.listdir(a.overlays) if f.endswith(".apk"))
    for f in overlays:
        name = f[:-4]
        fs.mkdir(f"/system/product/overlay/{name}", ov_ref_dir)
        fs.write(os.path.join(a.overlays, f), f"/system/product/overlay/{name}/{name}.apk", ov_ref_apk)
    fs.commit()
    log(f"installed {len(installed)} apps, {len(overlays)} overlays")

    perm = os.path.join(a.vendor, "permissions")
    fs.write(os.path.join(perm, "privapp-permissions-experience.xml"),
             "/system/etc/permissions/privapp-permissions-experience.xml", "/system/etc/permissions", "644")
    fs.write(os.path.join(perm, "sysconfig-experience.xml"),
             "/system/etc/sysconfig/sysconfig-experience.xml", "/system/etc/sysconfig", "644")
    if not fs.exists("/system/etc/default-permissions"):
        fs.mkdir("/system/etc/default-permissions", "/system/etc/permissions")
    fs.commit()
    fs.write(os.path.join(perm, "default-permissions-experience.xml"),
             "/system/etc/default-permissions/default-permissions-experience.xml", "/system/etc/permissions", "644")

    boot = "/system/media/bootanimation.zip"
    if fs.exists(boot):
        fs.cmds.append(f'rm "{boot}"')
    fs.write(os.path.join(a.vendor, "bootanimation", "bootanimation.zip"), boot, "/system/media", "644")
    fs.commit()

    wp_dir = "/system/product/media/experience/wallpapers"
    if not fs.exists("/system/product/media/experience"):
        fs.mkdir("/system/product/media/experience", "/system/product/media")
        fs.mkdir(wp_dir, "/system/product/media")
    fs.commit()
    for w in sorted(os.listdir(os.path.join(a.vendor, "wallpapers"))):
        fs.write(os.path.join(a.vendor, "wallpapers", w), f"{wp_dir}/{w}",
                 "/system/product/media/audio/ringtones", "644")
    for kind, name in (("ringtones", "Experience_Horizon.ogg"), ("notifications", "Experience_Bloom.ogg"),
                       ("alarms", "Experience_Daybreak.ogg")):
        fs.write(os.path.join(a.vendor, "sounds", kind, name),
                 f"/system/product/media/audio/{kind}/{name}",
                 f"/system/product/media/audio/{kind}", "644")
    fs.commit()

    display = "Experience UI 1.0"
    build_number = f"EXP1.0.0.{a.build_id}.userdebug"
    rg.edit_props(fs, "/system/build.prop", "\n".join([
        "# Experience UI (added by scripts/gsi/repack-system-image.py)",
        "ro.experience.version=1.0.0",
        f"ro.experience.display.version={display}",
        f"ro.experience.build.number={build_number}",
        f"ro.experience.build.id={a.build_id}",
        "ro.experience.release.channel=development",
        f"ro.experience.edition={a.edition}",
        f"ro.experience.base={a.base_label}",
        "ro.experience.gsi=0",
        "ro.experience.repack=1",
        "ro.experience.updater.uri=https://raw.githubusercontent.com/tahmidgaming700/Experience-Ui-Apps/main/ota/experience-ui-11.json",
        f"ro.build.display.id={display} ({build_number})",
        "",  # device model/brand kept as-is: honest hardware identity
    ]))
    rg.edit_props(fs, "/system/product/build.prop", "\n".join([
        "# Experience UI default sounds (added by scripts/gsi/repack-system-image.py)",
        "ro.config.ringtone=Experience_Horizon.ogg",
        "ro.config.notification_sound=Experience_Bloom.ogg",
        "ro.config.alarm_alert=Experience_Daybreak.ogg",
        "",
    ]))
    fs.commit()

    r = subprocess.run(["e2fsck", "-fy", a.image], capture_output=True, text=True)
    if r.returncode not in (0, 1):
        sys.exit("e2fsck failed:\n" + r.stdout[-3000:])
    r = subprocess.run(["e2fsck", "-fn", a.image], capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit("filesystem not clean after edit:\n" + r.stdout[-3000:])

    json.dump({"build_number": build_number, "build_id": a.build_id, "edition": a.edition,
               "base_description": a.base_label, "platform_certificate_sha256": img_cert,
               "removed": removed, "installed_apps": installed, "overlays": overlays,
               "image_bytes": os.path.getsize(a.image)},
              open(a.out_manifest, "w"), indent=2)
    log(f"done: {len(installed)} apps, {len(overlays)} overlays; image {os.path.getsize(a.image)//2**20} MiB (unchanged size)")


if __name__ == "__main__":
    main()
