#!/usr/bin/env python3
"""Apply the RMX3941 device layer to the unpacked ColorOS 17 partitions.

  patch_port.py manifest   <donor_my_manifest_dir> <base_MY_MANIFEST_build.prop>
  patch_port.py my_product <donor_my_product_dir>  <base_my_product_dir> <imports_out.txt>

Every change is listed explicitly below and printed as it is made, so the log
of a build is also its change list. A file named here that is missing from
its source fails the build instead of being skipped.
"""
import os
import re
import shutil
import sys

# --- my_manifest -----------------------------------------------------------
# my_manifest/build.prop is loaded last and overrides every partition's
# identity. The donor's says OPPO PEDM00 (Find X3, Snapdragon 870), which
# would make the MediaTek vendor/odm read the wrong device; these keys take
# the RMX3941 values instead. The ro.build.* version keys stay ColorOS 17.
IDENTITY_KEYS = re.compile(
    r"^(ro\.product\..*|ro\.vendor\..*|ro\.odm\..*|ro\.bootimage\..*"
    r"|ro\.build\.product|ro\.build\.oplus_nv_id|ro\.oplus\.image\.my_preload\.type"
    r"|ro\.oplus\.pipeline_key|ro\.mediatek\..*|ro\.build\.version\.svn)$"
)
MANIFEST_SET = {
    "ro.build.version.oplusrom.display": "17.0",
}

# --- my_product ------------------------------------------------------------
# The donor's my_product is the Find X3's: a QHD LTPO panel, Adreno GPU,
# Dolby audio and a Qualcomm camera stack. Everything that describes
# hardware comes from the RMX3941 my_product; the ColorOS 17 app layer the
# donor carries (launcher/systemui configs, apps, overlays for 17) stays.

# Donor files that only make sense on the Find X3 hardware.
PRODUCT_REMOVE = [
    "app/com.oplus.gpudrivers.sm8250.api30",   # Adreno 650 updatable driver; RMX3941 has a Mali GPU
    "app/com.oplus.vulkanLayer",               # Adreno profiler layer
    "app/OplusCamera",                         # Find X3 camera app (QCOM vendor tags); replaced below
    "app/AONService",                          # replaced below with the RMX3941 build
    "app/AudioEffectCenter",                   # Dolby UI; replaced below with the audiox build
    "etc/camera",                              # replaced below
    "etc/bruce",                               # extra props: QCA WLAN module path, LE audio on a QCOM BT stack
    "etc/dolby_vision.cfg",
    "etc/audio_policy_configuration.xml",      # QCOM audio policy; the MTK one lives in /vendor
    "etc/audio_policy_volumes.xml",
    "etc/default_volume_tables.xml",
    "etc/permissions/com.dolby_v3_6.daxservice.xml",
    "etc/permissions/oplus.product.features_dolby_stereo.xml",
    "etc/permissions/oplus.feature.bsp.charge.xml",
    "etc/permissions/oplus.product.display_system_features.xml",
    "etc/permissions/com.oppo.features_allnet_common_20061.xml",
    "vendor/etc",                              # Find X3 panel brightness/apollo tables; replaced below
    "overlay/aon.frameworkres.overlay.display.product.apk",   # Find X3 display/cutout RRO
    "overlay/com.android.SystemUIResOverlay.25021.apk",       # status bar/cutout dimensions of another panel
    "overlay/CarrierConfigOverlay.8250OP.apk",
    "overlay/CarrierConfigOverlay.platform.8850C.apk",
    "lib64/libdolbyeffect_5.0.so",
]

# RMX3941 files copied in, replacing any donor file of the same path.
PRODUCT_FROM_BASE = [
    "app/OplusCamera",
    "app/AONService",
    "app/AudioEffectCenter",
    "etc/camera",
    "vendor/etc",
    "etc/refresh_rate_config.xml",
    "etc/report_rate_config.xml",
    "etc/oplus_vrr_config.json",
    "etc/sys_resolution_switch_config.xml",
    "etc/sar_config.xml",
    "etc/wifisarconfig.xml",
    "etc/rf_feature_config.xml",
    "etc/temperature_profile",
    "etc/HBMNoiseTable",
    "etc/fusionlight_profile",
    "etc/vibrator",
    "etc/vibrator_service_config",
    "etc/sys_touch_preload_config.xml",
    "etc/sys_perf_config.json",
    "etc/oplus_games_fps.json",
    "etc/bsp_memory_app_heap_opt_list.xml",
    "etc/permissions/android.hardware.fingerprint.xml",
    "etc/permissions/android.hardware.sensor.light.xml",
    "etc/permissions/android.combination.key.features.xml",
    "etc/permissions/com.oplus.sensor_config.xml",
    "etc/permissions/com.oppo.features_display.xml",
    "etc/permissions/com.oppo.features_allnet_common.xml",
    "etc/permissions/com.oppo.features_allnet_multimedia_24700.xml",
    "etc/permissions/oplus.product.display_features.xml",
    "etc/permissions/oplus.product.feature_aon.xml",
    "etc/permissions/oplus.product.feature_audio_route.xml",
    "etc/permissions/oplus.product.feature_effect_unique.xml",
    "etc/permissions/oplus.product.feature_multimedia_unique.xml",
    "etc/permissions/realme_product_rom_feature_24700.xml",
    "etc/permissions/realme_product_rom_oplus_feature_24700.xml",
    "etc/extension/realme_product_rom_extend_feature_24700.xml",
    "overlay/android_framework_res_overlay.display.product.24700.apk",
    "overlay/aon_framework_res_overlay.display.product.apk",
    "overlay/SystemUIWmShellResOverlay.24700.apk",
    "overlay/oplus_framework_res_rro_realme.apk",
    "overlay/CarrierConfigOverlay.226ADU.apk",
]

# Donor my_product props kept on top of the RMX3941 ones: they describe the
# ColorOS 17 software, not hardware.
PRODUCT_KEEP_DONOR_KEYS = re.compile(
    r"^(ro\.build\.version\.oplusrom.*|ro\.config\.(alarm_alert|ringtone|ringtone_sim2|notification_sound"
    r"|notification_sms|notification_sim2)|persist\.sys\.oplus\.dex2oat_.*)$"
)
PRODUCT_DROP_BASE_KEYS = re.compile(r"^ro\.build\.version\.realmeui$")


def read_props(path):
    out = []
    with open(path, encoding="utf-8", errors="surrogateescape") as f:
        for line in f.read().splitlines():
            s = line.strip()
            if s and not s.startswith("#") and "=" in s and not s.startswith("import "):
                k, v = s.split("=", 1)
                out.append((k.strip(), v))
            else:
                out.append((None, line))
    return out


def write_props(path, items):
    with open(path, "w", encoding="utf-8", errors="surrogateescape") as f:
        for k, v in items:
            f.write(f"{k}={v}\n" if k is not None else f"{v}\n")


def patch_manifest(donor_dir, base_prop):
    path = os.path.join(donor_dir, "build.prop")
    donor = read_props(path)
    base = {k: v for k, v in read_props(base_prop) if k is not None}
    wanted = {k: v for k, v in base.items() if IDENTITY_KEYS.match(k)}
    wanted.update(MANIFEST_SET)
    out, seen = [], set()
    for k, v in donor:
        if k is not None and IDENTITY_KEYS.match(k) and k not in wanted:
            print(f"manifest: drop {k}={v}")
            continue
        if k in wanted:
            if wanted[k] != v:
                print(f"manifest: {k}: {v} -> {wanted[k]}")
            v = wanted[k]
            seen.add(k)
        out.append((k, v))
    for k, v in wanted.items():
        if k not in seen:
            print(f"manifest: add {k}={v}")
            out.append((k, v))
    write_props(path, out)


def _rm(path):
    if os.path.isdir(path) and not os.path.islink(path):
        shutil.rmtree(path)
    else:
        os.remove(path)


def patch_my_product(donor_dir, base_dir, imports_out):
    imported = []
    for rel in PRODUCT_REMOVE:
        p = os.path.join(donor_dir, rel)
        if not os.path.lexists(p):
            raise SystemExit(f"my_product: expected donor path missing: {rel}")
        _rm(p)
        print(f"my_product: remove {rel}")
    for rel in PRODUCT_FROM_BASE:
        src, dst = os.path.join(base_dir, rel), os.path.join(donor_dir, rel)
        if not os.path.lexists(src):
            raise SystemExit(f"my_product: expected RMX3941 path missing: {rel}")
        if os.path.lexists(dst):
            _rm(dst)
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if os.path.isdir(src) and not os.path.islink(src):
            shutil.copytree(src, dst, symlinks=True)
        else:
            shutil.copy2(src, dst, follow_symlinks=False)
        imported.append("/" + rel)
        print(f"my_product: from RMX3941 {rel}")
    # Camera JNI libraries the RMX3941 camera app loads from /my_product/lib64.
    # Names the donor already has stay the donor's: its newer Gallery uses them.
    for sub in ("lib", "lib64"):
        bdir = os.path.join(base_dir, sub)
        for name in sorted(os.listdir(bdir)):
            dst = os.path.join(donor_dir, sub, name)
            if not os.path.lexists(dst):
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                shutil.copy2(os.path.join(bdir, name), dst, follow_symlinks=False)
                imported.append(f"/{sub}/{name}")
                print(f"my_product: from RMX3941 {sub}/{name}")
    # Labels/owners of these paths come from the RMX3941 image (see repack.py).
    with open(imports_out, "w", encoding="utf-8") as f:
        f.write("\n".join(imported) + "\n")

    # build.prop: the RMX3941 hardware props, plus the donor's software ones.
    path = os.path.join(donor_dir, "build.prop")
    donor = read_props(path)
    base = read_props(os.path.join(base_dir, "build.prop"))
    keep = {k: v for k, v in donor if k is not None and PRODUCT_KEEP_DONOR_KEYS.match(k)}
    out, seen = [], set()
    for k, v in base:
        if k is not None and PRODUCT_DROP_BASE_KEYS.match(k):
            print(f"my_product prop: drop {k}={v}")
            continue
        if k in keep:
            if keep[k] != v:
                print(f"my_product prop: {k}: {v} -> {keep[k]}")
            v = keep[k]
            seen.add(k)
        out.append((k, v))
    for k, v in keep.items():
        if k not in seen:
            print(f"my_product prop: add {k}={v}")
            out.append((k, v))
    write_props(path, out)


if __name__ == "__main__":
    if sys.argv[1:2] == ["manifest"] and len(sys.argv) == 4:
        patch_manifest(sys.argv[2], sys.argv[3])
    elif sys.argv[1:2] == ["my_product"] and len(sys.argv) == 5:
        patch_my_product(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        raise SystemExit(__doc__)
