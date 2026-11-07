# Experience Virtual OS

**Status: early application shell — not yet an operating-system emulator.**

Experience Virtual OS is an independently implemented, rootless Android application project inspired by the user experience of projects such as [OneOS](https://github.com/fahadalijaved/OneOS). It is not a fork and does not copy OneOS source code.

## Goals

- Android 9 (API 28) minimum.
- No root, Magisk, KernelSU, or bootloader unlock requirement for the app itself.
- Samsung Galaxy and Huawei device research, including Huawei Y9 2019 (JKM-LX2).
- A separate guest environment, isolated from the host Android installation.
- A modular runtime adapter so a compatible virtual-machine engine can be integrated later.

## Current alpha

The app currently provides:
- A native Android dashboard.
- Rootless-mode and compatibility information.
- A document picker that remembers a selected guest image URI.

**It does not boot an Android guest yet.** Importing an image only stores a document reference; it does not validate or execute that image. No VM engine is bundled in this alpha.

## Why this is not a direct rootless OneOS port

OneOS switches the running Android framework by stopping/replacing privileged host processes and configuring mount namespaces. Those operations require root and device-specific kernel/vendor behaviour. A normal Android application cannot perform them on a locked, unrooted phone.

The rootless direction is therefore a different architecture: an isolated VM or user-space guest runtime. On Android 9, a software-emulated QEMU backend may be possible, but performance, memory, graphics, input, networking and guest boot compatibility must be evaluated. Hardware acceleration and modern Android virtualization APIs cannot be assumed on Android 9 or on Huawei firmware.

## Build

Open this directory in Android Studio with JDK 17 and Android SDK Platform 35 installed, or build with Gradle 8.9:

```sh
gradle assembleDebug
```

The generated debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

## Planned milestones

1. Device capability probe (CPU ABI, RAM, storage, Android API, virtualization flags).
2. Runtime abstraction and isolated file workspace.
3. QEMU software-emulation proof of concept for a small Linux guest.
4. Android guest boot research and compatibility matrix.
5. Optional acceleration backends only where the device and OS explicitly support them.

## Safety and compatibility

The app does not request root and does not write to system partitions. Do not treat an imported image as bootable. Never use an image from an untrusted source, and keep backups before experimenting with any future runtime.

## Project relationship

OneOS is an inspiration for the high-level idea of switching between environments. Experience Virtual OS is a separate rootless project with a different technical design. OneOS is licensed under Apache-2.0; consult its repository for its own source and licence notices.
