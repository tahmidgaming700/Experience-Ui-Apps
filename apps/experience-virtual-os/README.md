# Experience Virtual OS

A rootless Android virtual-device application inspired by the convenience of a desktop AVD manager, designed around a mobile-first setup experience.

## Product experience

The user-facing flow is an operating-system installer: welcome, language, Android guest version, memory, private environment preparation, then the virtual-device home. Internal Linux components and command execution are implementation details and are not exposed as a user terminal.

## Host requirements and safety

- Android 9 (API 28) and newer.
- ARM64-first; Huawei Y9 2019 (JKM-LX2 / Kirin 710 / EMUI 9.1) is a target for compatibility testing.
- No host root, bootloader unlock, Magisk, KernelSU or host partition modification.
- Any future guest-root option applies only inside a compatible guest image.

## Android guest targets

The setup catalogue includes Android 9 through Android 17 (API 28–37). Android 17 is API 37. Selecting a guest API level is configuration only: the corresponding ARM64 system image must be discovered and downloaded from Google's SDK package repository before that guest can be launched.

Google's SDK Manager downloads SDK platforms, tools and system-image packages. It does not itself provide a mobile-ready emulator runtime. The standard Android Emulator package is a separate host executable and cannot be assumed to run on an ARM64 Android phone. The intended phone runtime is a separately built ARM64 QEMU system emulator using TCG software emulation, with a compatible Android guest machine profile.

## Current alpha implementation

- Native first-run installer-style setup for language, Android guest API level, RAM and guest-root preference.
- Private base-environment bootstrap: CI fetches the latest stable Alpine ARM64 minirootfs from the official Alpine mirror, verifies its SHA-256 checksum and bundles it into the APK. The app installs it into app-private storage during first-run preparation.
- Base-environment preparation screen with a progress indicator. Internal Linux implementation details are not exposed as a user terminal.
- Existing Google ARM64 SDK image package downloads for the supplied API 28–33 archive URLs, plus image import.
- GitHub Releases update centre: checks releases, displays notes, downloads APK assets and invokes Android's package installer with user approval.
- Release workflow only publishes an installable APK when a persistent signing keystore is configured through Actions secrets.

## Runtime status — important

The base Alpine filesystem is installed as data. **It is not yet launched as a Linux process environment.** A compatible Android ARM64 PRoot/process layer, a Java runtime for SDK command-line tools, SDK package discovery/installation, and secure lifecycle management are still required.

**The Android virtual machine is not yet implemented.** The remaining runtime work includes a QEMU ARM64 TCG build suitable for Android, Android guest image validation (kernel, ramdisk, system/vendor and userdata layout), virtual hardware configuration, storage, networking, display rendering, touch/keyboard input, audio and VM lifecycle. No Android guest boot or live VNC display is claimed until it is built and tested on the target phone.

The intended guest display is an in-app surface. A local VNC server is one possible display transport, but it must be integrated and secured inside the app; it is not currently included.

## App updates

Push a version tag such as `v0.2.0-alpha01` to build an APK. To publish an installable release, configure the repository Actions secrets `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` and `ANDROID_KEY_PASSWORD` with the same persistent signing key used for every release. If these secrets are absent, the workflow skips release publishing. Android's package installer always asks the user to approve an update.

## Build

JDK 17, Android SDK Platform 35 and Gradle 8.9:

```sh
gradle --no-daemon assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

The GitHub Actions workflow fetches and verifies the Alpine ARM64 root filesystem before building. The APK is uploaded as a workflow artifact. Version tags beginning with `v` publish a GitHub Release only when release signing secrets are configured.

## OneOS relationship

This is an independent implementation inspired by the OneOS product concept. The original OneOS switcher uses privileged operations that conflict with the unrooted-host requirement. This app does not copy or invoke that privileged switcher.

## Image provenance

Alpine minirootfs is obtained from the official Alpine Linux mirror and checksum-verified at build time. Google SDK images are Google's materials and are downloaded from Google's SDK repositories by the app/runtime rather than redistributed in this repository.


## Runtime feasibility and current implementation boundary

The app now performs read-only host checks for ARM64 support, available memory,
CPU count, and the presence/read/write permissions of `/dev/kvm`. These checks
are diagnostic only: an accessible KVM node does not prove that a usable Android
guest machine profile or graphics path exists.

The five runtime milestones remain distinct engineering tasks:

1. **Private Linux execution:** the bundled Alpine root filesystem is currently
   extracted as private app data. It is not yet started. A rootless process
   supervisor such as PRoot must be built for Android/Bionic, bundled with its
   dependencies, and validated under the target device's SELinux and ptrace rules.
2. **SDK tooling:** Google's command-line SDK tools require a compatible Java
   runtime. Installing Alpine packages alone does not make `sdkmanager` runnable
   inside an Android app; the Java runtime, SDK tools, package licences, storage,
   and process execution all need integration.
3. **Guest images:** SDK system-image packages are designed for Google's Android
   Emulator/AVD runtime. Android 17 GSI downloads are a separate distribution
   intended for supported physical-device GSI testing/flashing; they are not
   turnkey QEMU virtual-machine disks.
4. **Virtual-machine engine:** upstream QEMU can emulate ARM guests with TCG, but
   Google's emulator images rely on the Android Emulator's own machine/device
   model. A generic upstream QEMU ARM board is not a drop-in replacement for the
   emulator's ranchu/goldfish devices. The app needs a compatible guest kernel,
   machine model, devices, and boot configuration.
5. **Interactive display:** only after a guest reaches a stable boot can the app
   attach a local display/input transport (for example, a loopback-only VNC
   bridge or an in-process renderer). No network listener should be exposed to
   the LAN, and no terminal UI is part of the product.

The current release therefore does not claim to boot Android. The host capability
panel is intended to establish whether hardware acceleration is even available
on the target phone before selecting and building the correct engine. TCG-only
emulation may be too slow for a usable Android desktop on a mid-range phone.
