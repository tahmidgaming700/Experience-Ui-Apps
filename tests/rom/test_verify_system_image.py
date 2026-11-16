"""Self-test for scripts/rom/verify-system-image.py using synthetic ext4 images.

Builds a miniature system-as-root tree with mke2fs -d, as raw and Android-sparse
images, and checks that a complete tree passes and a broken one fails.
Run: python3 -m unittest discover -s tests/rom
"""
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "..", "..", "scripts", "rom", "verify-system-image.py")

PROPS_DEFAULT = """ro.experience.version=1.0.0
ro.experience.build.number=EXP1.0.0.202611151530.userdebug
ro.experience.edition=Vanilla
"""
PROPS_BUILD = """ro.build.version.release=11
ro.build.version.sdk=30
ro.build.version.security_patch=2024-01-05
ro.build.display.id=Experience UI 1.0 (EXP1.0.0.202611151530.userdebug)
ro.product.cpu.abilist=arm64-v8a,armeabi-v7a,armeabi
ro.product.system.model=Experience Device
ro.experience.display.version=Experience UI 1.0
ro.experience.base=LineageOS 18.1 + TrebleDroid (Android 11)
"""
PROPS_PRODUCT = """ro.config.ringtone=Experience_Horizon.ogg
ro.config.notification_sound=Experience_Bloom.ogg
ro.config.alarm_alert=Experience_Daybreak.ogg
"""
PRIV = ["ExperienceLauncher", "ExperienceSettings", "ExperienceSetupWizard", "ExperienceDialer"]
APPS = ["ExperienceMessages", "ExperienceFiles", "ExperienceContacts", "ExperienceNotes", "ExperienceClock", "ExperienceCalendar",
        "ExperienceCalculator", "ExperienceGallery", "ExperienceCamera", "ExperienceConverter"]
ACCENTS = ["Indigo", "Azure", "Teal", "Verdant", "Amber", "Coral", "Rose", "Violet", "Graphite"]


def touch(root, rel, content=b"x"):
    p = os.path.join(root, rel.lstrip("/"))
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "wb") as f:
        f.write(content if isinstance(content, bytes) else content.encode())


def build_tree(root, broken=False):
    touch(root, "/init", b"#!init")
    touch(root, "/system/etc/prop.default", PROPS_DEFAULT)
    touch(root, "/system/build.prop", PROPS_BUILD.replace("sdk=30", "sdk=29") if broken else PROPS_BUILD)
    touch(root, "/system/product/build.prop", PROPS_PRODUCT)
    for m in PRIV:
        touch(root, f"/system/priv-app/{m}/{m}.apk")
    for m in APPS[:-1] if broken else APPS:
        touch(root, f"/system/app/{m}/{m}.apk")
    touch(root, "/system/system_ext/priv-app/TrebuchetQuickStep/TrebuchetQuickStep.apk")
    if broken:
        touch(root, "/system/priv-app/LineageSetupWizard/LineageSetupWizard.apk")
    for p in ("/system/etc/permissions/privapp-permissions-experience.xml",
              "/system/etc/default-permissions/default-permissions-experience.xml",
              "/system/etc/sysconfig/sysconfig-experience.xml", "/system/media/bootanimation.zip",
              "/system/product/media/audio/ringtones/Experience_Horizon.ogg",
              "/system/product/media/audio/notifications/Experience_Bloom.ogg",
              "/system/product/media/audio/alarms/Experience_Daybreak.ogg",
              "/system/product/media/experience/wallpapers/experience_bloom.png"):
        touch(root, p)
    for a in ACCENTS:
        touch(root, f"/system/product/overlay/ExperienceAccent{a}/ExperienceAccent{a}.apk")


def mkimg(tree, image):
    subprocess.run(["mke2fs", "-q", "-t", "ext4", "-b", "4096", "-d", tree, image, "8M"], check=True)


def to_sparse(raw, sparse, blk=4096):
    """Minimal sparse writer: zero blocks become DONT_CARE chunks, the rest RAW."""
    with open(raw, "rb") as fh:
        data = fh.read()
    nblocks = len(data) // blk
    chunks, i = [], 0
    while i < nblocks:
        zero = data[i * blk:(i + 1) * blk] == b"\0" * blk
        j = i
        while j < nblocks and (data[j * blk:(j + 1) * blk] == b"\0" * blk) == zero:
            j += 1
        chunks.append((zero, i, j - i))
        i = j
    with open(sparse, "wb") as f:
        f.write(struct.pack("<IHHHHIIII", 0xED26FF3A, 1, 0, 28, 12, blk, nblocks, len(chunks), 0))
        for zero, start, n in chunks:
            if zero:
                f.write(struct.pack("<HHII", 0xCAC3, 0, n, 12))
            else:
                f.write(struct.pack("<HHII", 0xCAC1, 0, n, 12 + n * blk))
                f.write(data[start * blk:(start + n) * blk])


def run(image):
    return subprocess.run([sys.executable, "-I", SCRIPT, image], capture_output=True, text=True)


@unittest.skipIf(shutil.which("mke2fs") is None or shutil.which("debugfs") is None, "e2fsprogs missing")
class VerifySystemImageTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def image(self, broken=False, sparse=False):
        tree = os.path.join(self.tmp, "tree")
        build_tree(tree, broken)
        raw = os.path.join(self.tmp, "system.img")
        mkimg(tree, raw)
        if sparse:
            out = os.path.join(self.tmp, "system.sparse.img")
            to_sparse(raw, out)
            return out
        return raw

    def test_complete_raw_image_passes(self):
        r = run(self.image())
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)

    def test_complete_sparse_image_passes(self):
        r = run(self.image(sparse=True))
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("sparse image converts to raw", r.stdout)

    def test_broken_image_fails(self):
        r = run(self.image(broken=True))
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("[FAIL] SDK 30", r.stdout)
        self.assertIn("[FAIL] app ExperienceConverter", r.stdout)
        self.assertIn("[FAIL] replaced app LineageSetupWizard not installed", r.stdout)


if __name__ == "__main__":
    unittest.main()
