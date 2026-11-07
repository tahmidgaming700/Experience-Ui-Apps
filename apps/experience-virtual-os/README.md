# Experience Virtual OS

An independently implemented, rootless Android guest-environment manager inspired by the product concept and navigation of OneOS. This is not a direct port of OneOS's privileged hot-switch engine.

## Target

- Android 9 (API 28) and newer host devices.
- ARM64-first, with Huawei Y9 2019 (JKM-LX2 / Kirin 710 / EMUI 9.1) as an explicit compatibility research target.
- No host root, Magisk, KernelSU, bootloader unlock or host partition modifications.
- Guest configuration includes an optional **guest-root request**. This is a setting for the guest image only; it never grants root to the host. Whether root is available depends on the guest image and runtime.

## Current implementation

- First-run setup wizard: language, guest Android API, RAM profile and guest-root preference.
- Dashboard and Android system-image library.
- Download manager integration for the six Google ARM64 SDK image package URLs supplied for API 28–33.
- Import of user-provided guest image packages using Android's Storage Access Framework.
- Persistent setup and guest configuration.
- Custom geometric launcher icon.

## Important runtime status

**The Android VM runtime is not integrated yet.** The current alpha can configure the app and download/import image packages, but it does not boot an Android guest. The Google SDK ZIP files are emulator system-image packages, not complete standalone VM applications. A working Android guest also needs a compatible QEMU build, kernel, ramdisk, vendor/system layout, machine configuration, graphics/display, input, storage and networking integration.

The supplied SDK packages are official Google download URLs. The app downloads them into its own external app-specific downloads directory. They are not bundled into the APK.

## OneOS images

The upstream OneOS repository does not publish guest system images or firmware archives. Its README says guest images are built separately from OEM firmware and patched during its build process; the repository contains app code, scripts and patch tooling. Do not assume an image is available from that repository. If you have a separate OneOS Google Drive folder or a permitted image mirror, provide its share URL and we can add it as an optional image source after checking its contents and redistribution terms.

## Why the original OneOS switcher is not used

OneOS's original switcher replaces parts of the running Android framework using privileged operations. That is incompatible with the requirement to run on an unrooted, locked Huawei device. Experience Virtual OS keeps the host Android system intact and is being developed around a separate virtual-machine backend instead.

## Build

JDK 17, Android SDK Platform 35 and Gradle 8.9:

```sh
gradle --no-daemon assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

## Roadmap

1. Integrate a QEMU ARM64 runtime build suitable for Android 9 hosts.
2. Validate the API 28–33 image package layouts and required emulator components.
3. Implement per-guest disk storage, lifecycle management, display, input, audio and networking.
4. Test performance and stability on the Huawei JKM-LX2 before advertising it as supported.
5. Add guest-root support only for images where the guest-side mechanism is compatible and can be safely isolated from the host.

## Licence and attribution

This is a separate implementation. OneOS is Apache-2.0; see https://github.com/fahadalijaved/OneOS for upstream notices and credits. Google SDK images remain Google's materials and are downloaded from Google rather than redistributed in this project.
