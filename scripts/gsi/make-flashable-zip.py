#!/usr/bin/env python3
"""Package an Experience UI system image as an AOSP test-key-signed flashable zip.

This produces a recovery-style installable zip (META-INF/com/google/android/
update-binary + updater-script, with the system image inside), jar-signed with the
public AOSP test key. It is the standard custom-ROM packaging format.

IMPORTANT, and printed in the bundled README:
  * A device's stock local-update / recovery (realme ColorOS/realme UI, Samsung, etc.)
    only installs packages signed with that vendor's private OTA key. This zip is signed
    with the public AOSP test key, so stock recovery/local update rejects it at the
    signature check ("verification failed"). That rejection is expected.
  * On devices that use dynamic partitions (super) — most phones launched with Android 10+,
    including recent realme models — the logical `system` partition cannot be written by a
    recovery updater-script; it is flashed with `fastboot flash system` in fastbootd. The
    update-binary here flashes a real /dev/block/by-name/system when one exists (A-only or
    non-dynamic devices) and otherwise explains the fastbootd path instead of failing silently.

  make-flashable-zip.py --image system.img --out DIR --name NAME
      --pk8 testkey.pk8 --cert testkey.x509.pem --apksigner PATH [--manifest repack-manifest.json]
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import zipfile

UPDATE_BINARY = r"""#!/sbin/sh
#
# Experience UI flashable installer (custom recovery).
# Args: $1 = recovery API version, $2 = command pipe fd, $3 = zip path.
#
OUTFD="$2"
ZIP="$3"

ui_print() { echo "ui_print $1" >> /proc/self/fd/$OUTFD; echo "ui_print" >> /proc/self/fd/$OUTFD; }
abort()    { ui_print "$1"; exit 1; }

ui_print " "
ui_print "============================================"
ui_print " Experience UI 1.0 (Android 11 GSI)"
ui_print "============================================"
ui_print " "

# Locate a writable system block device (A-only / non-dynamic devices).
SLOT="$(getprop ro.boot.slot_suffix 2>/dev/null)"
SYS=""
for p in /dev/block/by-name/system$SLOT /dev/block/by-name/system \
         /dev/block/bootdevice/by-name/system$SLOT /dev/block/bootdevice/by-name/system; do
    [ -e "$p" ] && { SYS="$p"; break; }
done

if [ -z "$SYS" ]; then
    ui_print "No directly writable 'system' partition was found."
    ui_print "This device uses dynamic partitions (super)."
    ui_print " "
    ui_print "A recovery zip cannot resize a logical partition."
    ui_print "Flash the image from fastbootd instead:"
    ui_print "   adb reboot fastboot"
    ui_print "   fastboot flash system system.img"
    ui_print "   fastboot -w && fastboot reboot"
    ui_print " "
    ui_print "The system image is inside this zip as system.img."
    abort "Stopped: use fastbootd for this device."
fi

ui_print "Extracting system image..."
mkdir -p /tmp/experience || abort "cannot write to /tmp"
unzip -o "$ZIP" system.img -d /tmp/experience >/dev/null 2>&1 || abort "failed to extract system.img"
IMG=/tmp/experience/system.img
[ -f "$IMG" ] || abort "system.img missing from package"

ui_print "Writing Experience UI to $SYS ..."
dd if="$IMG" of="$SYS" bs=4M 2>/dev/null || abort "dd failed writing system"
sync
rm -f "$IMG"
ui_print " "
ui_print "Done. Reboot and complete Experience setup."
ui_print "(If it does not boot, disable AVB: fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img)"
exit 0
"""

UPDATER_SCRIPT = (
    'ui_print("Experience UI 1.0 (Android 11 GSI)");\n'
    'ui_print("Installation is handled by update-binary.");\n'
)

README = """Experience UI 1.0 - flashable zip ({name})
======================================================================

Contents: system.img (Experience UI, Android 11 ARM64 A/B GSI) in a recovery-style
installer, signed with the PUBLIC AOSP test key.

WILL IT INSTALL FROM realme's "Local update" / stock recovery?  NO.
  Stock updaters only accept packages signed with realme's own private OTA key. This
  zip is AOSP test-key signed, so the stock updater rejects it at verification
  ("verification failed" / "installation failed"). That is expected and unavoidable
  without realme's private key, which is not public.

Even past verification, the RMX3941 (and most phones from Android 10 on) uses dynamic
partitions: the 'system' partition lives inside 'super' and must be flashed with
'fastboot flash system' in fastbootd, not written by a recovery zip. The installer in
this zip detects that and tells you to use fastbootd instead of failing silently.

HOW TO ACTUALLY INSTALL EXPERIENCE UI (needs an unlocked bootloader):
  1. Unlock the bootloader (realme Deep Testing app, if available for your model).
  2. unzip {name}.zip system.img
  3. adb reboot fastboot            # fastbootd
  4. fastboot flash system system.img
  5. fastboot -w && fastboot reboot
  If it does not boot, flash your stock vbmeta with verity/verification disabled first:
     fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img
  NOTE: Experience UI is Android 11. A GSI must be >= your vendor's Android version to
  boot. If your phone shipped on Android 13/14/15, this Android 11 image will not boot;
  you need a GSI matching that version.

This build has not been booted on any device.
"""


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--image", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--name", required=True)
    ap.add_argument("--keystore", required=True, help="PKCS12 keystore holding the signing key")
    ap.add_argument("--storepass", required=True)
    ap.add_argument("--alias", required=True)
    ap.add_argument("--jarsigner", default="jarsigner")
    ap.add_argument("--manifest")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    unsigned = os.path.join(a.out, a.name + ".unsigned.zip")
    signed = os.path.join(a.out, a.name + ".zip")

    print(f"[zip] packaging {a.image} ({os.path.getsize(a.image) / 2**20:.0f} MiB)")
    with zipfile.ZipFile(unsigned, "w", zipfile.ZIP_DEFLATED, allowZip64=True, compresslevel=6) as z:
        zi = zipfile.ZipInfo("META-INF/com/google/android/update-binary")
        zi.external_attr = 0o755 << 16
        z.writestr(zi, UPDATE_BINARY)
        z.writestr("META-INF/com/google/android/updater-script", UPDATER_SCRIPT)
        z.writestr("experience-README.txt", README.format(name=a.name))
        if a.manifest and os.path.isfile(a.manifest):
            z.write(a.manifest, "experience-build-info.json")
        # The payload last, stored at a predictable name for the installer and fastboot.
        z.write(a.image, "system.img")

    print("[zip] signing with the AOSP test key (jarsigner, v1/JAR signature)")
    shutil.copyfile(unsigned, signed)
    subprocess.run([a.jarsigner, "-keystore", a.keystore, "-storetype", "PKCS12",
                    "-storepass", a.storepass, "-sigalg", "SHA256withRSA", "-digestalg", "SHA-256",
                    signed, a.alias], check=True)
    subprocess.run([a.jarsigner, "-verify", "-keystore", a.keystore, "-storetype", "PKCS12",
                    "-storepass", a.storepass, signed], check=True, stdout=subprocess.DEVNULL)
    os.remove(unsigned)

    sha = hashlib.sha256()
    with open(signed, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 22), b""):
            sha.update(chunk)
    digest = sha.hexdigest()
    with open(signed + ".sha256", "w") as f:
        f.write(f"{digest}  {a.name}.zip\n")
    info = {"zip": a.name + ".zip", "sha256": digest, "bytes": os.path.getsize(signed),
            "signed_with": "AOSP public test key (testkey), JAR/v1 signature",
            "installable_via_stock_local_update": False,
            "reason": "stock updater requires the vendor's private OTA key; this is AOSP test-key signed",
            "requires": "unlocked bootloader + fastbootd (dynamic partitions); GSI Android version >= vendor"}
    with open(os.path.join(a.out, a.name + ".info.json"), "w") as f:
        json.dump(info, f, indent=2)
    print(f"[zip] {signed} ({os.path.getsize(signed) / 2**20:.0f} MiB), sha256 {digest}")


if __name__ == "__main__":
    main()
