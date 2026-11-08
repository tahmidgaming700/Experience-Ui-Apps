# Experience Virtual OS runtime releases

Runtime bootstrap assets are published as versioned GitHub Release assets, not as expiring Actions artifacts.

## Build and publish

1. Push the runtime packaging workflow and required source assets to the feature branch.
2. Confirm the GitHub Actions run succeeds and inspect the `experience-virtual-os-runtime` artifact.
3. After the runtime contents have been tested on an Android ARM64 host, create and push a version tag, for example:

```bash
git tag runtime-v0.1.0
git push origin runtime-v0.1.0
```

The tag workflow creates a GitHub Release containing:

- `base.tar.gz` — versioned runtime bootstrap archive.
- `SHA256SUMS` — SHA-256 checksum for the archive.
- `manifest.json` — machine-readable version, architecture, asset URL, exact byte size and checksum.

The release manifest is generated from the actual archive in CI. Its URL points to the matching versioned release asset; no placeholder checksum or guessed file size is published.

## Current archive contents

- `alpine/alpine-full-rootfs.bin` — verified Alpine ARM64 root filesystem archive.
- `kernel/kernel-qemu` — verified ARM64 kernel-qemu file currently bundled by the app.
- `lib/arm64-v8a/libproot.so` and `lib/arm64-v8a/libproot-loader.so` — ARM64 PRoot runtime components.

## Important runtime limitation

This is a bootstrap package, not a complete Android emulator engine. The current app installs QEMU packages inside Alpine with `apk`. The archive does not yet contain a native Android/Bionic ARM64 QEMU executable, a Ranchu/Goldfish Android guest machine implementation verified on Android hosts, or a bootable Android guest image. Do not advertise the archive as a complete Android emulator runtime until those components are built and tested on a physical Android device.

The app's existing first-run path still installs the bundled Alpine asset. A subsequent integration change must make it fetch the release manifest, download the archive, verify SHA-256 and size, safely extract into app-private storage, and use the extracted PRoot/kernel paths before remote bootstrap can be considered active.
