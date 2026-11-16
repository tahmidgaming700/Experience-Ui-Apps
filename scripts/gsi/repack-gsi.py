#!/usr/bin/env python3
"""Add Experience UI to a prebuilt Android 11 GSI system image (no source build).

Starting from a LineageOS 18.1 / phh-TrebleDroid system image (e.g. AndyYan's
lineage-18.1-*-arm64_b?S.img.xz), this:

  1. decompresses (.xz) and unsparses the image;
  2. grows the ext4 filesystem and removes its read-only "shared_blocks" deduplication
     (e2fsck -E unshare_blocks), which these GSIs use;
  3. with debugfs, installs the platform-signed Experience apps, the Experience
     resource overlays (static RROs + accent RROs), permission/sysconfig XML, boot
     animation, wallpapers and sounds; removes the apps Experience replaces; appends
     Experience identity properties to build.prop. Every new file and directory copies
     its SELinux label, owner, group and mode from an existing reference path in the
     same location, and gets a fixed timestamp;
  4. checks the filesystem (e2fsck -fn), shrinks it to its contents plus a reserve,
     and writes a raw ext4 system image.

It refuses to touch an image whose platform certificate does not match the key the
Experience apps were signed with (they would lose their system permissions).

The base image's fingerprint, security patch level and LineageOS identity are not
changed: only ro.experience.*, the build display ID and the system-partition
product name/model are added.

  repack-gsi.py --base lineage.img.xz --apks DIR --overlays DIR --platform-cert platform.x509.pem
                --out OUT_DIR --build-id 202611160400 [--edition GApps] [--keep-sms-default]
"""
import argparse
import hashlib
import json
import lzma
import os
import re
import shutil
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
VENDOR = os.path.join(REPO, "rom", "vendor", "experience")
sys.path.insert(0, os.path.join(REPO, "scripts", "rom"))
FIXED_TIME = "@1230768000"  # 2009-01-01, the timestamp AOSP uses for reproducible images

PRIV_APPS = ["ExperienceLauncher", "ExperienceSettings", "ExperienceSetupWizard", "ExperienceDialer"]
APPS = ["ExperienceMessages", "ExperienceFiles", "ExperienceContacts", "ExperienceNotes", "ExperienceClock",
        "ExperienceCalendar", "ExperienceCalculator", "ExperienceGallery", "ExperienceCamera", "ExperienceConverter"]

# Apps replaced by Experience apps. Paths that do not exist in a given base are skipped.
REPLACED = [
    "/system/system_ext/priv-app/LineageSetupWizard",   # -> Experience Setup Wizard
    "/system/system_ext/priv-app/SetupWizard",          # Google setup wizard (GApps bases)
    "/system/priv-app/Provision",
    "/system/product/app/Etar",                         # -> Experience Calendar
    "/system/product/app/DeskClock",                    # -> Experience Clock
    "/system/product/app/DeskClockGoogle",
    "/system/product/app/ExactCalculator",              # -> Experience Calculator
    "/system/product/app/CalculatorGoogle",
    "/system/system_ext/priv-app/Camera2",              # -> Experience Camera
    "/system/product/priv-app/Updater",                 # LineageOS updater (official builds only)
    "/system/system_ext/priv-app/Updater",
]


def log(msg):
    print(f"[repack] {msg}", flush=True)


def sh(cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


def prepare_raw(base, dest):
    """Copy the base image to dest as a raw ext4 image (handles .xz and Android sparse)."""
    tmp = dest + ".tmp"
    if base.endswith(".xz"):
        log(f"decompressing {os.path.basename(base)}")
        with lzma.open(base) as src, open(tmp, "wb") as out:
            shutil.copyfileobj(src, out, 1 << 22)
    else:
        shutil.copyfile(base, tmp)
    with open(tmp, "rb") as f:
        magic = f.read(4)
    if magic == b"\x3a\xff\x26\xed":
        import importlib.util
        spec = importlib.util.spec_from_file_location("vsi", os.path.join(REPO, "scripts/rom/verify-system-image.py"))
        vsi = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(vsi)
        log("converting Android sparse image to raw")
        vsi.unsparse(tmp, dest)
        os.remove(tmp)
    else:
        os.replace(tmp, dest)


class Fs:
    """debugfs-based editing of an ext4 image."""

    def __init__(self, image, workdir):
        self.image = image
        self.work = workdir
        self.cmds = []
        self.labels = {}

    def query(self, cmd):
        r = subprocess.run(["debugfs", "-R", cmd, self.image], capture_output=True, text=True)
        return r.stdout

    def exists(self, path):
        return "Inode:" in self.query(f'stat "{path}"')

    def meta(self, path):
        """(label bytes file, mode octal, uid, gid) of an existing path."""
        out = self.query(f'stat "{path}"')
        if "Inode:" not in out:
            raise SystemExit(f"reference path missing in image: {path}")
        mode = re.search(r"Mode:\s+(\d+)", out).group(1)
        uid, gid = re.search(r"User:\s+(\d+)\s+Group:\s+(\d+)", out).groups()
        ea = self.query(f'ea_get "{path}" security.selinux')
        m = re.search(r'= "(.*)\\000"', ea)
        if not m:
            raise SystemExit(f"no SELinux label on reference path {path}")
        label = m.group(1)
        lf = self.labels.get(label)
        if lf is None:
            lf = os.path.join(self.work, f"label{len(self.labels)}")
            with open(lf, "wb") as f:
                f.write(label.encode() + b"\0")
            self.labels[label] = lf
        return lf, mode, uid, gid

    def _attrs(self, path, ref, is_dir, perm=None):
        lf, mode, uid, gid = self.meta(ref)
        kind = "040" if is_dir else "0100"
        perm = perm or mode[-3:]
        self.cmds += [
            f'sif "{path}" mode {kind}{perm}',
            f'sif "{path}" uid {uid}',
            f'sif "{path}" gid {gid}',
            f'sif "{path}" mtime {FIXED_TIME}',
            f'sif "{path}" atime {FIXED_TIME}',
            f'sif "{path}" ctime {FIXED_TIME}',
            f'ea_set -f "{lf}" "{path}" security.selinux',
        ]

    def mkdir(self, path, ref):
        self.cmds.append(f'mkdir "{path}"')
        self._attrs(path, ref, True)

    def write(self, local, path, ref, perm=None):
        """Write a file; label/owner come from ref, mode from ref unless perm is given."""
        self.cmds.append(f'write "{local}" "{path}"')
        self._attrs(path, ref, False, perm)

    def remove_tree(self, path):
        """Queue recursive deletion of path (file or directory)."""
        out = self.query(f'ls -p "{path}"')
        entries = [l.split("/") for l in out.splitlines() if l.startswith("/")]
        for e in entries:
            # /inode/mode/uid/gid/name/size/
            if len(e) < 6 or e[5] in (".", ".."):
                continue
            child = f"{path}/{e[5]}"
            if e[2].startswith("04"):
                self.remove_tree(child)
            else:
                self.cmds.append(f'rm "{child}"')
        self.cmds.append(f'rmdir "{path}"')

    def commit(self):
        script = os.path.join(self.work, "debugfs.cmd")
        with open(script, "w") as f:
            f.write("\n".join(self.cmds) + "\n")
        r = subprocess.run(["debugfs", "-w", "-f", script, self.image], capture_output=True, text=True)
        bad = [l for l in (r.stdout + r.stderr).splitlines()
               if re.search(r"error|not found|could not|cannot|No such|already exists|Bad|Usage", l, re.I)
               and not l.startswith("debugfs ")]
        if r.returncode != 0 or bad:
            raise SystemExit("debugfs reported problems:\n" + "\n".join(bad[:40]))
        log(f"applied {len(self.cmds)} filesystem operations")
        self.cmds = []


def cert_sha256(apk_or_pem, apksigner=None):
    if apk_or_pem.endswith(".pem"):
        der = subprocess.run(["openssl", "x509", "-in", apk_or_pem, "-outform", "DER"], check=True, capture_output=True).stdout
        return hashlib.sha256(der).hexdigest()
    out = subprocess.run([apksigner, "verify", "--print-certs", apk_or_pem], check=True, capture_output=True, text=True).stdout
    return re.search(r"Signer #1 certificate SHA-256 digest: ([0-9a-f]+)", out).group(1)


def edit_props(fs, path, block):
    local = os.path.join(fs.work, os.path.basename(path) + "." + hashlib.md5(path.encode()).hexdigest()[:6])
    sh(["debugfs", "-R", f'dump "{path}" "{local}"', fs.image], capture_output=True)
    text = open(local).read()
    if "ro.experience." in text:
        raise SystemExit(f"{path} already contains Experience properties; start from a clean base image")
    with open(local, "a") as f:
        f.write("\n" + block)
    # Replace the file, keeping the original's label/owner/mode: write it under a temporary
    # name using the original as reference, then swap.
    tmp_name = path + ".experience-new"
    fs.write(local, tmp_name, path)
    fs.cmds += [f'rm "{path}"', f'ln "{tmp_name}" "{path}"', f'unlink "{tmp_name}"']


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--base", required=True)
    ap.add_argument("--apks", required=True, help="dir with platform-signed <Module>.apk")
    ap.add_argument("--overlays", required=True, help="dir with overlay APKs from build-overlays.py")
    ap.add_argument("--platform-cert", required=True)
    ap.add_argument("--apksigner", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--build-id", required=True)
    ap.add_argument("--edition", default="GApps")
    ap.add_argument("--base-label", default="", help="human description of the base image")
    ap.add_argument("--grow-mb", type=int, default=1024)
    ap.add_argument("--reserve-mb", type=int, default=64)
    a = ap.parse_args()
    for tool in ("debugfs", "e2fsck", "resize2fs", "dumpe2fs", "openssl"):
        if shutil.which(tool) is None:
            sys.exit(f"{tool} is required (e2fsprogs/openssl)")
    if not re.fullmatch(r"\d{12}", a.build_id):
        sys.exit("--build-id must be a UTC timestamp YYYYMMDDHHMM")

    os.makedirs(a.out, exist_ok=True)
    work = tempfile.mkdtemp(prefix="exprepack-", dir=a.out)
    image = os.path.join(a.out, "system.img")
    try:
        prepare_raw(a.base, image)
        h = hashlib.sha256()
        with open(a.base, "rb") as f:
            for chunk in iter(lambda: f.read(1 << 22), b""):
                h.update(chunk)
        base_sha = h.hexdigest()

        # --- sanity: Android 11 GSI signed with the same platform key as our apps ---
        fs = Fs(image, work)
        props = fs.query('cat "/system/build.prop"')
        if "ro.build.version.sdk=30" not in props:
            sys.exit("base image is not Android 11 (ro.build.version.sdk != 30)")
        fr = os.path.join(work, "framework-res.apk")
        sh(["debugfs", "-R", f'dump /system/framework/framework-res.apk "{fr}"', image], capture_output=True)
        image_cert = cert_sha256(fr, a.apksigner)
        key_cert = cert_sha256(a.platform_cert)
        if image_cert != key_cert:
            sys.exit(f"platform certificate mismatch: image {image_cert} != signing key {key_cert}")
        log(f"platform certificate matches ({image_cert[:16]}…)")

        # --- grow and unshare ---
        blocks_before = int(re.search(r"Block count:\s+(\d+)", subprocess.run(["dumpe2fs", "-h", image], capture_output=True, text=True).stdout).group(1))
        with open(image, "r+b") as f:
            f.truncate(os.path.getsize(image) + a.grow_mb * 1024 * 1024)
        sh(["resize2fs", "-f", image], capture_output=True)
        r = subprocess.run(["e2fsck", "-fy", "-E", "unshare_blocks", image], capture_output=True, text=True)
        if r.returncode not in (0, 1):
            sys.exit("e2fsck unshare_blocks failed:\n" + r.stdout[-2000:])
        log("filesystem grown and shared blocks removed")

        # --- remove replaced apps ---
        removed = []
        for path in REPLACED:
            if fs.exists(path):
                fs.remove_tree(path)
                removed.append(path)
        fs.commit()
        log("removed: " + ", ".join(os.path.basename(p) for p in removed))

        # --- apps ---
        installed = {}
        for group, root, ref_dir, ref_apk in (
                (PRIV_APPS, "/system/priv-app", "/system/priv-app/Shell", "/system/priv-app/Shell/Shell.apk"),
                (APPS, "/system/app", "/system/app/BasicDreams", "/system/app/BasicDreams/BasicDreams.apk")):
            for m in group:
                apk = os.path.join(a.apks, m + ".apk")
                if not os.path.isfile(apk):
                    sys.exit(f"missing {apk}")
                if cert_sha256(apk, a.apksigner) != key_cert:
                    sys.exit(f"{m}.apk is not signed with the platform key")
                fs.mkdir(f"{root}/{m}", ref_dir)
                fs.write(apk, f"{root}/{m}/{m}.apk", ref_apk)
                installed[m] = {"path": f"{root}/{m}/{m}.apk", "sha256": hashlib.sha256(open(apk, "rb").read()).hexdigest()}
        # --- overlays ---
        overlay_ref = "/system/product/overlay/framework-res__auto_generated_rro_product.apk"
        overlays = sorted(f for f in os.listdir(a.overlays) if f.endswith(".apk"))
        for f in overlays:
            fs.write(os.path.join(a.overlays, f), f"/system/product/overlay/{f}", overlay_ref)
        # --- permissions and config ---
        perm = os.path.join(VENDOR, "permissions")
        fs.write(os.path.join(perm, "privapp-permissions-experience.xml"),
                 "/system/etc/permissions/privapp-permissions-experience.xml", "/system/etc/permissions", "644")
        fs.write(os.path.join(perm, "sysconfig-experience.xml"),
                 "/system/etc/sysconfig/sysconfig-experience.xml", "/system/etc/sysconfig", "644")
        if not fs.exists("/system/etc/default-permissions"):
            fs.mkdir("/system/etc/default-permissions", "/system/etc/permissions")
        fs.commit()
        fs.write(os.path.join(perm, "default-permissions-experience.xml"),
                 "/system/etc/default-permissions/default-permissions-experience.xml", "/system/etc/permissions", "644")
        # --- boot animation, wallpapers, sounds ---
        boot = "/system/media/bootanimation.zip"
        if fs.exists(boot):
            fs.cmds.append(f'rm "{boot}"')
        fs.write(os.path.join(VENDOR, "bootanimation", "bootanimation.zip"), boot, "/system/media", "644")
        fs.commit()
        if not fs.exists("/system/product/media/experience"):
            fs.mkdir("/system/product/media/experience", "/system/product/media")
            fs.mkdir("/system/product/media/experience/wallpapers", "/system/product/media")
        for w in sorted(os.listdir(os.path.join(VENDOR, "wallpapers"))):
            fs.write(os.path.join(VENDOR, "wallpapers", w), f"/system/product/media/experience/wallpapers/{w}",
                     "/system/product/media/audio/ringtones", "644")
        for kind, name in (("ringtones", "Experience_Horizon.ogg"), ("notifications", "Experience_Bloom.ogg"),
                           ("alarms", "Experience_Daybreak.ogg")):
            fs.write(os.path.join(VENDOR, "sounds", kind, name), f"/system/product/media/audio/{kind}/{name}",
                     f"/system/product/media/audio/{kind}", "644")
        fs.commit()
        # --- properties ---
        build_number = f"EXP1.0.0.{a.build_id}.userdebug"
        display = "Experience UI 1.0"
        base_label = a.base_label or os.path.basename(a.base)
        edit_props(fs, "/system/build.prop", "\n".join([
            "# Experience UI (added by scripts/gsi/repack-gsi.py; later definitions win at boot)",
            "ro.experience.version=1.0.0",
            f"ro.experience.display.version={display}",
            f"ro.experience.build.number={build_number}",
            f"ro.experience.build.id={a.build_id}",
            "ro.experience.release.channel=development",
            f"ro.experience.edition={a.edition}",
            f"ro.experience.base={base_label}",
            "ro.experience.gsi=1",
            "ro.experience.repack=1",
            "ro.experience.updater.uri=https://raw.githubusercontent.com/tahmidgaming700/Experience-Ui-Apps/main/ota/experience-ui-11.json",
            f"ro.build.display.id={display} ({build_number})",
            "ro.product.system.brand=Experience",
            "ro.product.system.manufacturer=Experience",
            "ro.product.system.model=Experience Device",
            "",
        ]))
        edit_props(fs, "/system/product/build.prop", "\n".join([
            "# Experience UI default sounds (added by scripts/gsi/repack-gsi.py)",
            "ro.config.ringtone=Experience_Horizon.ogg",
            "ro.config.notification_sound=Experience_Bloom.ogg",
            "ro.config.alarm_alert=Experience_Daybreak.ogg",
            "",
        ]))
        fs.commit()

        # --- check, shrink, finish ---
        r = subprocess.run(["e2fsck", "-fy", image], capture_output=True, text=True)
        if r.returncode not in (0, 1):
            sys.exit("e2fsck found errors after editing:\n" + r.stdout[-3000:])
        r = subprocess.run(["e2fsck", "-fn", image], capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit("filesystem not clean after repair:\n" + r.stdout[-3000:])
        sh(["resize2fs", "-M", image], capture_output=True)
        info = subprocess.run(["dumpe2fs", "-h", image], capture_output=True, text=True).stdout
        bs = int(re.search(r"Block size:\s+(\d+)", info).group(1))
        blocks = int(re.search(r"Block count:\s+(\d+)", info).group(1)) + a.reserve_mb * 1024 * 1024 // bs
        sh(["resize2fs", image, str(blocks)], capture_output=True)
        with open(image, "r+b") as f:
            f.truncate(blocks * bs)
        sh(["e2fsck", "-fn", image], capture_output=True)
        log(f"system.img: {os.path.getsize(image) / 2**20:.0f} MiB (base filesystem {blocks_before * bs / 2**20:.0f} MiB)")

        manifest = {
            "build_number": build_number,
            "build_id": a.build_id,
            "edition": a.edition,
            "base_image": os.path.basename(a.base),
            "base_image_sha256": base_sha,
            "base_description": base_label,
            "platform_certificate_sha256": image_cert,
            "removed": removed,
            "installed_apps": installed,
            "overlays": overlays,
            "image_bytes": os.path.getsize(image),
        }
        with open(os.path.join(a.out, "repack-manifest.json"), "w") as f:
            json.dump(manifest, f, indent=2)
    finally:
        shutil.rmtree(work, ignore_errors=True)


if __name__ == "__main__":
    main()
