# Experience Virtual OS

An independently implemented, rootless Android guest-environment project inspired by the OneOS product concept. It is not a direct port of OneOS's privileged hot-switch engine.

## Target

- Android 9 (API 28) and newer host devices.
- ARM64-first, with Huawei Y9 2019 (JKM-LX2 / Kirin 710 / EMUI 9.1) as a compatibility research target.
- No host root, Magisk, KernelSU, bootloader unlock or host partition modifications.
- Optional guest-root preference is guest-only and image-dependent.

## Current alpha implementation

- Native first-run setup for language, Android guest API, RAM profile and guest-root preference.
- Rootless host dashboard and Android ARM64 image library.
- Download manager integration for supplied Google ARM64 SDK image packages (API 28–33).
- Import flow for user-provided guest image files.
- **Alpine Linux ARM64 bootstrap:** CI downloads the official Alpine minirootfs from Alpine's latest-stable ARM64 release directory, verifies its SHA-256 against Alpine's published SHA256SUMS, and bundles it into the APK. The app extracts it into private app storage without root.
- **GitHub update centre:** checks published releases in this repository, shows release notes, downloads a release APK, and opens Android's package installer after the user grants the app permission to request package installs.
- Custom geometric launcher icon and dark, native Android interface.

## Runtime status — read carefully

The Alpine root filesystem can be installed into app-private storage. **This milestone does not yet execute an Alpine shell.** The next required component is a compatible ARM64 PRoot/native process runtime and terminal session. An Android app cannot simply chroot into Alpine without root, and the rootfs installer does not pretend otherwise.

**The Android guest VM is not integrated yet.** A working Android guest still requires a compatible QEMU ARM64 TCG build for Android, a validated Android guest machine/kernel/ramdisk/system/vendor layout, storage and lifecycle management, and display/input/network integration. The Google SDK ZIPs are system-image packages, not standalone emulator applications. No Android guest boot is claimed until that complete path is implemented and tested on target hardware.

## App updates

Push a version tag such as v0.2.0-alpha01 to build the APK. To publish an installable release, configure the repository Actions secrets ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD with the same persistent signing key used for every release. The workflow deliberately skips release publishing if these secrets are absent: debug APKs signed with temporary CI keys cannot update an installed copy. The in-app updater reads published releases and selects the first APK asset. Android's package installer always asks the user to approve installation; the app does not silently update itself.

## Build

JDK 17, Android SDK Platform 35 and Gradle 8.9:

    gradle --no-daemon assembleDebug

Output: app/build/outputs/apk/debug/app-debug.apk.

The GitHub Actions workflow fetches and verifies the Alpine ARM64 root filesystem before building. The APK is uploaded as a workflow artifact. Tag pushes beginning with v publish a GitHub Release containing a consistently signed APK only when the four signing secrets are configured.

## OneOS relationship

The public OneOS repository contains app code and patch/build tooling, not a ready-to-download guest image archive. Its original switcher uses privileged operations that conflict with this project's unrooted-host requirement. This project is an independent implementation; it does not copy or invoke the privileged switcher.

## Licences and image provenance

Alpine minirootfs is obtained from the official Alpine Linux mirror at build time and checksum-verified against Alpine's published checksum file. Google SDK images remain Google's materials and are downloaded by the user from Google rather than redistributed by this project. OneOS is Apache-2.0; see https://github.com/fahadalijaved/OneOS for upstream notices and credits.
