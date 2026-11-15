# Experience UI 1.0 — GSI compatibility

Experience UI 1.0 is built as a **generic system image (GSI)**: one `system.img` for
ARM64 devices that use the A/B partition scheme with a system-as-root layout and
implement Project Treble. This document says what that means, what has actually been
verified, and what is only expected to work.

## Verification status

| Level | Status | Evidence |
|---|---|---|
| Experience apps compile, unit tests, lint | **Verified** on every change | `experience-ui-apps.yml` → `apps` job |
| GSI + Experience patch stack applies to LineageOS 18.1 sources | **Verified** for frameworks/base, packages/apps/Settings, packages/apps/Trebuchet | `rom-static` job (`check-patches.sh`) |
| Overlaid resources exist in the modules they overlay | **Verified** (framework, SystemUI, Settings, Launcher3 quickstep) | `check-tree.py --upstream` |
| Image verifier logic | **Verified** against synthetic raw and sparse ext4 images | `tests/rom/` |
| Full ROM compilation | **Not yet run.** Needs the self-hosted runner described in the README | `experience-ui-rom.yml` |
| Static validation of a built `system.img` | **Not yet run** (no image exists yet) | `verify-system-image.py` report attached to each build |
| Boot on an emulator | **Not possible for this target** — see below | — |
| Boot on real hardware | **No device verified** | — |

There is **no verified device** for Experience UI 1.0. Static image validation, when it
runs, checks the image's format, layout, properties and contents. It does not and cannot
show that the image boots.

### Why no emulator boot test

The Android Emulator boots system images built *for the emulator* (goldfish/ranchu
vendor, emulator kernel). A Treble GSI for real ARM64 devices expects a vendor partition,
kernel and HALs from a real device; on the emulator's `x86_64` hosts it cannot run at all,
and the emulator's ARM64 images use their own vendor image that this GSI is not built
for. A meaningful boot test therefore needs real hardware. The ROM workflow is designed
so a device-backed boot test can be added on a runner with an attached test device; until
such a run is recorded here, every published build stays an **unverified draft
pre-release**.

## Target requirements (theoretical compatibility)

A device is *expected* to be able to boot Experience UI if **all** of the following hold.
"Expected" means: this is what the GSI is built for, not that it has been observed.

| Requirement | Why | How to check (adb shell / fastboot) |
|---|---|---|
| ARM64 (`arm64-v8a`) CPU | image contains ARM64 + 32-bit ARM userspace | `getprop ro.product.cpu.abi` |
| Project Treble | GSIs rely on a separated vendor interface | `getprop ro.treble.enabled` → `true` |
| A/B partitions **or** dynamic partitions with system-as-root | image is built as an A/B SAR (`_ab`, `bvN`) GSI | `getprop ro.build.ab_update`, `getprop ro.boot.dynamic_partitions` |
| Vendor (VNDK) version 27–30 | phh's Android 11 GSIs target Android 8.1–11 vendors; older vendors are untested upstream | `getprop ro.vndk.version` |
| Unlockable bootloader | flashing system requires it | OEM-specific |
| AVB/dm-verity can be disabled | the image is signed with the AOSP test key | `fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img` |
| `system` (or `super`) large enough | image size = contents + 32 MiB reserve | compare image size with partition size |

Devices that launched with Android 10 or later normally use **dynamic partitions**: flash
from **fastbootd** (`adb reboot fastboot`), not the bootloader's fastboot, and be prepared
to delete the `product` partition if `super` is too small (`fastboot delete-logical-partition product_a`).

## Known limitations of this GSI design

- **Vendor identity is kept.** On real hardware Android 11 resolves `ro.product.model`,
  `ro.product.brand` etc. from the vendor/odm/product partitions before system. Settings
  therefore shows the device's own model, while Experience UI identity appears as
  "Experience UI version" and in Experience Settings › About. Experience UI never spoofs
  device certification, fingerprints or security patch levels.
- **Security patch level** is whatever LineageOS 18.1's last security backports provide
  (reported from the built image, e.g. `ro.build.version.security_patch`). LineageOS
  18.1 is end-of-life upstream; Experience UI 1.0 does not add newer platform security fixes.
- **Hardware quirks** (fingerprint, camera, NFC, audio routing, brightness, IMS/VoLTE)
  depend on phh/TrebleDroid device workarounds and differ per device; none have been
  verified for Experience UI.
- **MMS**: Experience Messages sends and receives SMS but does not download or send MMS;
  AOSP Messaging remains installed for MMS.
- **A-only and VNDK-lite devices** are not built (the upstream project derives those from
  A/B images with separate tooling).

## Previously named research targets

Earlier Experience repositories listed these devices. Their status for Experience UI 1.0:

| Device | Status | Notes |
|---|---|---|
| Huawei Y9 2019 (JKM-LX2) | Not verified | Huawei no longer issues bootloader unlock codes; Treble vendor is EMUI 9.1 (Pie, VNDK 28). Only possible on an already-unlocked unit. |
| Samsung Galaxy Tab S 10.5 (SM-T805 / chagalllte) | **Not compatible** | Exynos 5420 tablet from 2014, no Project Treble, not A/B, Android 6 vendor. A GSI cannot boot on it. |

## Reporting a device

If you test Experience UI on a device, record in an issue: model and variant, original
firmware and vendor version (`getprop ro.vndk.version`, `ro.vendor.build.fingerprint`),
exact flashing commands, the Experience build number, whether it reached the setup wizard,
and a `logcat` / `dmesg` capture. A device is listed as **verified** only with that
evidence.
