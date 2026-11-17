# ColorOS 17 port for the realme C75 (RMX3941)

> **Untested on hardware.** This port has been built and its images checked
> offline. It has never been flashed or booted on a phone. Expect the first
> attempts to bootloop or to have broken features, and keep a way back to
> stock before you flash anything.

## What it is

| | Source | Used for |
|---|---|---|
| **Base** | Official realme package `RMX3941_14.1.0.240(EX01)` (realme UI 5.0, Android 14) from `rms01.realme.net` | `vendor`, `odm`, kernel (`boot`, `vendor_boot`, `dtbo`), firmware (`lk`, `md1img`, `scp`, `spmfw`, `sspm`, `tee`, `gz`), `vbmeta*` |
| **Donor** | ColorOS `17.0.0.105` for the OPPO Find X3 (`PEDM00`, Snapdragon 870), a **community port** ("Ported By BT") hosted on SourceForge | `system`, `system_ext`, `product`, `my_product`, `my_stock`, `my_region`, `my_manifest`, `my_heytap`, `my_carrier`, `my_bigball`, `my_engineering` |

The device is MediaTek MT6768 (sold as Helio G92 Max), kernel 5.10, with A/B
slots and virtual A/B. Its vendor is VNDK 31, VINTF target level 5, SELinux
vendor policy 31.0.

The donor is a Qualcomm phone running a port itself. This is a
cross-platform port (Qualcomm donor onto a MediaTek device): that is the
hardest kind and the most likely to break.

## What `port.sh` changes

All changes are listed in `lib/patch_port.py` and printed in the build log.

1. **`my_manifest`**: device identity props (`ro.product.*`, `ro.vendor.*`,
   `ro.odm.*`, `ro.bootimage.*`, first API level, region/NV id) are set to the
   RMX3941 values. Without this, the MediaTek vendor would read a Find X3.
   The ColorOS 17 build/version props stay.
2. **`my_product`**: the donor's app layer stays. Everything that describes
   hardware comes from RMX3941:
   - build props: display density, brightness curves, cutout position, radio,
     MediaTek modem
   - display, refresh-rate and resolution configs
   - hardware feature permission files
   - the display/cutout/SystemUI resource overlays
   - the camera app and its configs
   - AON and audio-effect apps

   Removed because they only fit the Find X3: the Adreno GPU driver app and
   profiler layer, Dolby, and the Qualcomm audio policy.
3. **`system_ext`**: adds `com.android.vndk.v31.apex` from the realme build.
   The donor only ships v30, and the RMX3941 vendor needs v31.
4. **`vbmeta`**: sets the disable-verity and disable-verification flags.
   Every changed partition then mounts without AVB hashes.
5. **Super layout**: `super_empty.img` reproduces realme's metadata (11.59 GB
   `super`, groups `main_a`/`main_b`, 3 slots, virtual A/B). The partitions
   are flashed into it from fastbootd.

Repacked partitions keep each file's SELinux label, owner and mode. They are
read from the original EROFS images by `lib/erofs_meta.py` and checked again
on the rebuilt image. A mismatch fails the build.

## Checks done offline

- All realme images match the MD5 list inside the realme package.
- Every donor partition passes `fsck.erofs`.
- The split SELinux policy compiles: the Android 17 platform policy plus the
  donor's `system_ext`/`product` policy plus the RMX3941 vendor policy.
  Every type the MediaTek vendor policy references resolves. Ubuntu's
  `secilc` 3.5 is older than Android 17's, so the check removes three things
  that version cannot parse:
  - `nlmsg` xperm rules
  - two policy capabilities
  - one `genfscon` line also present in stock. The stock realme
    `precompiled_sepolicy` shows AOSP's compiler accepts it.
- The Android 17 framework still ships `compatibility_matrix.5.xml`, matching
  the vendor's target level.
- The total size fits the `main_a` group.

## Known risks (not testable without the phone)

- **Bootloops** from vendor HALs built for Android 14's framework
  (`odm` oplus HALs, MediaTek AIDL/HIDL versions).
- **Camera.** The RMX3941 camera app now runs on ColorOS 17 frameworks.
- **Qualcomm leftovers** in the donor `system_ext` (WFD, IMS, `qlogd` and
  similar services) will fail to start. That is noisy but usually harmless.
  **IMS/VoLTE is likely broken.**
- **Region.** The donor is a CN build with no Google apps. The realme `my_preload` /
  `my_company` partitions are not included; the vendor fstab marks them
  `nofail`.
- **Anything else** a cross-platform port usually breaks: fingerprint (side
  sensor), refresh rate, AOD and brightness.

## Build

Locally (Ubuntu 24.04, about 30 GB free, run as root so file owners can be set):

```
apt-get install clang libssl-dev zlib1g-dev libfmt-dev erofs-utils android-sdk-libsparse-utils
rom/rmx3941-coloros17/port.sh /path/to/work /path/to/out
```

Or run **Actions → Build RMX3941 ColorOS 17 port** with a `release_tag`. It
builds on a GitHub runner and publishes a **draft** pre-release in parts
under 2 GiB. If the SourceForge mirror is slow, pass a `donor_url` ending in
`?use_mirror=<mirror>` (for example `gigenet` or `freefr`).

`lpmake`/`lpunpack` are built from AOSP `android14-release` sources by
`tools/build-lptools.sh`. No prebuilt binaries are downloaded.

## Flash

Requirements:
- an unlocked bootloader
- Google platform-tools `fastboot` 34 or newer
- stock 14.1.0.240(EX01) or newer installed, with the stock package kept for recovery

Unlocking the C75's bootloader is not covered here. Whether realme's official
Deep Testing app supports this model is unconfirmed.

```
./flash_all.sh        # Linux/macOS, from the unpacked output folder
flash_all.bat         # Windows
```

The script:
1. checks that the bootloader is unlocked
2. sets slot A active
3. flashes the realme kernel and firmware to slot A, with vbmeta verification off
4. reboots to fastbootd
5. runs `wipe-super` with `super_empty.img` and flashes every logical partition
6. wipes data and reboots

**Back to stock:** flash the stock realme images the same way: boot, firmware
and vbmeta from the stock package, then the stock logical partitions in
fastbootd. Or use realme's service flashing tool.

## Distribution

The output contains proprietary OPPO and realme software. The workflow
publishes only a draft. Decide whether publishing it from this repository is
acceptable before you make it public.
