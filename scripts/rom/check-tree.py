#!/usr/bin/env python3
"""Static consistency checks for the Experience UI ROM tree (no Android source needed).

  check-tree.py [--upstream TOP]

Checks:
  * every XML file under rom/ is well-formed; Android.bp files parse (balanced, named modules)
  * version identity agrees across version.mk, experience.prop, the app suite and the OTA manifest
  * every Experience module in PRODUCT_PACKAGES is defined, and the 14 apps agree between
    settings.gradle.kts, collect-app-prebuilts.sh, Android.bp and the image verifier
  * every PRODUCT_COPY_FILES source under vendor/experience exists
  * patches are git format-patch output; excludes/overrides name real upstream patches (with --upstream)
  * with --upstream TOP (a tree containing frameworks/base, packages/apps/Settings, ...):
    every overlaid resource exists in the module it overlays
"""
import argparse
import glob
import json
import os
import re
import sys
import xml.etree.ElementTree as ET

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ROM = os.path.join(REPO, "rom")
VENDOR = os.path.join(ROM, "vendor", "experience")
errors = []


def err(msg):
    errors.append(msg)
    print(f"[FAIL] {msg}")


def ok(msg):
    print(f"[ OK ] {msg}")


def read(path):
    with open(path) as f:
        return f.read()


def check_xml():
    n = 0
    for path in glob.glob(os.path.join(ROM, "**", "*.xml"), recursive=True):
        try:
            ET.parse(path)
            n += 1
        except ET.ParseError as e:
            err(f"{os.path.relpath(path, REPO)}: {e}")
    ok(f"{n} XML files well-formed")


def bp_modules(path):
    text = re.sub(r"//.*", "", read(path))
    if text.count("{") != text.count("}"):
        err(f"{path}: unbalanced braces")
    return re.findall(r'^\s*([a-z_]+)\s*\{[^{}]*?name:\s*"([^"]+)"', text, re.M | re.S)


def check_versions():
    vmk = read(os.path.join(VENDOR, "config", "version.mk"))
    major = re.search(r"EXPERIENCE_VERSION_MAJOR := (\d+)", vmk).group(1)
    minor = re.search(r"EXPERIENCE_VERSION_MINOR := (\d+)", vmk).group(1)
    patch = re.search(r"EXPERIENCE_VERSION_PATCH := (\d+)", vmk).group(1)
    version = f"{major}.{minor}.{patch}"
    display = f"Experience UI {major}.{minor}"
    prop = read(os.path.join(VENDOR, "config", "experience.prop"))
    m = re.search(r"^ro\.experience\.display\.version=(.*)$", prop, re.M)
    if not m or m.group(1) != display:
        err(f"experience.prop display version {m and m.group(1)!r} != {display!r}")
    gp = read(os.path.join(REPO, "apps", "experience-suite", "gradle.properties"))
    gv = re.search(r"^experience\.versionName=(.*)$", gp, re.M).group(1)
    if gv != version:
        err(f"apps versionName {gv} != ROM version {version}")
    ota = json.load(open(os.path.join(REPO, "ota", "experience-ui-11.json")))
    for ch, entry in ota["channels"].items():
        if entry and entry.get("url") and not entry.get("sha256"):
            err(f"OTA channel {ch} publishes a url without sha256")
    if ota.get("sdk") != 30 or ota.get("android_version") != "11":
        err("OTA manifest must describe Android 11 / SDK 30")
    ok(f"version {version} / '{display}' consistent")


def check_modules():
    mk = read(os.path.join(VENDOR, "config", "experience.mk"))
    pkgs = set()
    for block in re.findall(r"PRODUCT_PACKAGES \+= \\\n((?:\s+\S+ \\\n)*\s+\S+\n)", mk):
        pkgs.update(block.replace("\\", " ").split())
    defined = {}
    for bp in glob.glob(os.path.join(VENDOR, "**", "Android.bp"), recursive=True):
        for kind, name in bp_modules(bp):
            defined[name] = kind
    aosp = {p for p in pkgs if p.startswith(("IconShape", "NavigationBarMode"))}
    for p in sorted(pkgs - aosp):
        if p not in defined:
            err(f"PRODUCT_PACKAGES {p} has no Android.bp module in vendor/experience")
    apps_bp = {n for n, k in defined.items() if k == "android_app_import"}
    settings = read(os.path.join(REPO, "apps", "experience-suite", "settings.gradle.kts"))
    gradle = set(re.search(r"val experienceApps = listOf\((.*?)\)", settings, re.S).group(1).replace('"', "").replace(",", " ").split())
    collect = read(os.path.join(REPO, "scripts", "rom", "collect-app-prebuilts.sh"))
    mapping = dict(re.findall(r"\[(\w+)\]=(Experience\w+)", collect))
    verify = read(os.path.join(REPO, "scripts", "rom", "verify-system-image.py"))
    verified = set(re.findall(r'"(Experience[A-Z]\w+)"', verify.split("REPLACED")[0]))
    if set(mapping) != gradle:
        err(f"collect-app-prebuilts modules {sorted(set(mapping) ^ gradle)} differ from settings.gradle.kts")
    if set(mapping.values()) != apps_bp:
        err(f"Android.bp apps {sorted(set(mapping.values()) ^ apps_bp)} differ from collect-app-prebuilts")
    if verified != apps_bp:
        err(f"verify-system-image apps {sorted(verified ^ apps_bp)} differ from Android.bp")
    app_pkgs = {p for p in pkgs if p in apps_bp}
    if app_pkgs != apps_bp:
        err(f"apps missing from PRODUCT_PACKAGES: {sorted(apps_bp - app_pkgs)}")
    ok(f"{len(apps_bp)} Experience apps consistent across Gradle, collect script, Android.bp, verifier, product")


def check_copy_files():
    mk = read(os.path.join(VENDOR, "config", "experience.mk"))
    n = 0
    for src in re.findall(r"(vendor/experience/[^\s:]+):", mk):
        n += 1
        if not os.path.exists(os.path.join(ROM, src)):
            err(f"PRODUCT_COPY_FILES source missing: {src}")
    for d in re.findall(r"find-copy-subdir-files,[^,]+,(vendor/experience/[^,]+),", mk):
        if not os.listdir(os.path.join(ROM, d)):
            err(f"empty copy directory {d}")
    boot = os.path.join(VENDOR, "bootanimation", "bootanimation.zip")
    if not os.path.isfile(boot):
        err("bootanimation.zip missing (run scripts/rom/generate-assets.py)")
    ok(f"{n} copy-file sources present")


def check_patches(upstream):
    pdir = os.path.join(ROM, "patches")
    for p in glob.glob(os.path.join(pdir, "**", "*.patch"), recursive=True):
        head = read(p)[:4000]
        if not head.startswith("From ") or "\nSubject: " not in head:
            err(f"{os.path.relpath(p, REPO)} is not git format-patch output")
    if upstream:
        lpu = os.path.join(upstream, "lineage_patches_unified")
        refs = [l.strip() for l in read(os.path.join(pdir, "upstream-exclude.txt")).splitlines()
                if l.strip() and not l.startswith("#")]
        refs += [os.path.relpath(p, os.path.join(pdir, "upstream-overrides"))
                 for p in glob.glob(os.path.join(pdir, "upstream-overrides", "**", "*.patch"), recursive=True)]
        for r in refs:
            if not os.path.isfile(os.path.join(lpu, r)):
                err(f"upstream patch referenced by Experience does not exist: {r}")
    ok("patch files well-formed")


def resource_names(res_dir):
    names = set()
    for path in glob.glob(os.path.join(res_dir, "values*", "*.xml")):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for el in root:
            if el.get("name"):
                names.add((el.tag if el.tag != "item" else el.get("type"), el.get("name")))
    for path in glob.glob(os.path.join(res_dir, "drawable*", "*")):
        names.add(("drawable", os.path.splitext(os.path.basename(path))[0]))
    return names


def check_overlays(upstream):
    base = os.path.join(VENDOR, "overlay", "static")
    n = 0
    for res in glob.glob(os.path.join(base, "**", "res"), recursive=True):
        rel = os.path.relpath(res, base)
        target = os.path.join(upstream, rel)
        if not os.path.isdir(target):
            err(f"overlay target {rel} not found in upstream tree")
            continue
        available = resource_names(target)
        for (kind, name) in resource_names(res):
            n += 1
            if (kind, name) not in available:
                err(f"overlay {rel}: {kind}/{name} does not exist upstream")
    ok(f"{n} overlaid resources exist upstream")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--upstream", help="source tree (or partial clone) to check overlays/patch refs against")
    a = ap.parse_args()
    check_xml()
    for bp in glob.glob(os.path.join(ROM, "**", "Android.bp"), recursive=True):
        bp_modules(bp)
    check_versions()
    check_modules()
    check_copy_files()
    check_patches(a.upstream)
    if a.upstream:
        check_overlays(a.upstream)
    if errors:
        print(f"\n{len(errors)} problem(s)")
        sys.exit(1)
    print("\nROM tree OK")


if __name__ == "__main__":
    main()
