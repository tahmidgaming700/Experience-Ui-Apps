# Experience UI 1.0 — Android 11 GSI (ARM64, A/B, system-as-root)

A Material 3 Expressive-inspired Android 11 system with 14 native Experience apps and
Experience branding, delivered as a **generic system image (GSI)**.

> ⚠️ **Pre-release — not boot-tested.** This build passes static image validation only
> (format, partition layout, properties, signed app contents). **It has not been booted
> on any device.** Flash it only on a device you can fully recover, and read the
> compatibility notes below first. It is not an official LineageOS or Google build and is
> not affiliated with realme.

## Which file do I want?

| File | What it is | How you use it |
|---|---|---|
| `ExperienceUI-1.0.0-<build>-arm64_bgS-repack.img.xz` | The system image (xz-compressed) | **The normal way in.** `xz -d`, then `fastboot flash system …` from fastbootd on an unlocked device. |
| `ExperienceUI-1.0.0-<build>-arm64_bgS.zip` | The same image wrapped in a recovery-style installer, **signed with the public AOSP test key** | A package to *try* in a recovery / "local update". See the honest caveat below. |
| `SHA256SUMS`, `*.sha256` | Checksums | Verify your download before flashing. |
| `repack-manifest.json`, `verification.json` | Build provenance + the static checks that passed | Audit what went into the image. |

### About the AOSP-signed `.zip` and stock "Local update"

The `.zip` is signed with the **public AOSP test key**, which is the standard way custom
ROMs are packaged. **A phone's stock local-update / recovery (realme ColorOS/realme UI,
Samsung, etc.) will reject it** at the signature check, because stock updaters only accept
packages signed with that vendor's own private OTA key — which is not public and is not
included here. Seeing "verification failed" in a stock updater is the expected outcome.

Also, phones that use **dynamic partitions** (`super` — most devices launched with Android
10 or later) cannot have `system` written by a recovery zip at all; it is flashed with
`fastboot flash system` in fastbootd. The installer inside the zip detects this and points
you to fastbootd instead of failing silently. So on current phones, use the `.img.xz` +
fastboot route; the `.zip` is mainly for A-only / non-dynamic devices and for inspection.

## Installing (unlocked bootloader required)

There is **no verified device**. Before flashing, read
[docs/compatibility.md](https://github.com/tahmidgaming700/Experience-Ui-Apps/blob/main/docs/compatibility.md).

```bash
# Verify the download
sha256sum -c SHA256SUMS

# Decompress and flash (A/B device with dynamic partitions)
xz -d ExperienceUI-1.0.0-<build>-arm64_bgS-repack.img.xz
adb reboot fastboot                         # fastbootd
fastboot flash system ExperienceUI-1.0.0-<build>-arm64_bgS-repack.img
fastboot -w && fastboot reboot

# If it doesn't boot, disable verified boot with your device's stock vbmeta:
fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img
```

**Android-version rule:** a GSI must be **at least** the Android version of the device's
vendor partition. Experience UI 1.0 is **Android 11**, so it will not boot on a phone whose
vendor is Android 12 or newer. For those devices you need a newer base; see "Building on a
different base" in the repo README.

## What's inside

- **Base:** AndyYan's unofficial **LineageOS 18.1** `arm64_bgS` GSI (Android 11, phh/
  TrebleDroid), **GApps edition** (includes Google apps and su). The base's fingerprint,
  Android security patch level and LineageOS version are **unchanged**.
- **14 Experience apps** (native Kotlin + Jetpack Compose, platform-signed): Launcher,
  Settings, Setup Wizard, Dialer, Messages, Contacts, Notes, Clock, Calendar, Calculator,
  Files, Gallery, Camera, Converter. 220 unit tests pass.
- **Experience look:** resource overlays for the framework, SystemUI, Settings and the
  recents screen (large rounded shapes, squircle icons/tiles, tonal surfaces), nine accent
  colours, a generated boot animation, wallpapers and sounds.
- **Replaced** on this base: the Lineage/Google setup wizards, Etar, Google Clock, Google
  Calculator, Camera2. **Kept:** Google Dialer/Messages/Contacts and the Google launcher's
  recents; Google Messages stays the default SMS app because Experience Messages has no MMS.

## Known limitations

- **Not boot-tested.** No device has booted this image.
- **GApps edition**, not the planned Vanilla edition (it is built on the `bgS` base).
- **Experience Messages: SMS only, no MMS** (Google Messages remains for MMS).
- **Launcher:** no home-screen widgets yet.
- The two Experience **source patches** (the Experience version rows in the SystemUI quick
  settings footer and Settings › About) are **not** in this repack — they need recompiled
  system modules. Experience Settings › About shows the same information.
- App UI strings are mostly in code, so the apps are English-only for now.

## How it was built

This image was produced by `scripts/gsi/repack.sh`: it signs the apps with the AOSP test
key (the key the base is signed with, verified before any change), builds the overlays,
grows and de-deduplicates the base ext4 image, installs the apps/overlays/permissions/
assets with `debugfs` (copying SELinux labels, owners and modes from neighbouring files),
removes the replaced apps, adds `ro.experience.*` identity, then runs `e2fsck` and the
static image checks. Fully reproducible from the pinned base; see the repo README.

## Credits & licensing

Built on **LineageOS 18.1**, **phhusson/TrebleDroid** and **AndyYan**'s unofficial GSIs —
all under their own licenses. Experience UI's own code and assets are **Apache-2.0**.
Not affiliated with or endorsed by Google, the LineageOS project, or realme. No device
certification, fingerprint or security-patch spoofing is performed.
