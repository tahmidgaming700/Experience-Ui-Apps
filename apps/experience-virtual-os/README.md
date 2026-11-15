# Experience Virtual OS

A rootless virtual-machine manager for ARM64 Android phones (Android 9+). It runs
QEMU inside a private Alpine Linux userspace under PRoot — no root, no bootloader
unlock, no system modification.

**What works today, honestly stated** (details and evidence in
[`docs/experience-virtual-os-guest-compatibility.md`](../../docs/experience-virtual-os-guest-compatibility.md)):

| Capability | Status |
|---|---|
| Runtime download, SHA-256 verification, atomic install, repair, update, offline import | Implemented, unit-tested |
| Virtual-device database (create, edit, duplicate, delete, import/export) | Implemented, unit-tested |
| Linux ARM64 guests (UEFI ISO, virtio disk/GPU/net, touch + keyboard over local VNC) | Experimental — QEMU configuration verified booting Alpine 3.24 to a login prompt; not yet run on a phone |
| Android system images (Google AOSP ARM64) | Experimental, **console only** — kernel, init and native services start; no display is possible without the Android Emulator's goldfish pipe |
| Google Play / Android app compatibility | Not provided |

## Architecture

```
APK (Kotlin, Compose Material 3)                      GitHub Release (separate)
├─ UI: setup wizard, dashboard, devices, images,      ├─ base.tar.gz     Alpine 3.24 aarch64 rootfs
│      runtime, settings/diagnostics                  │                  + qemu-system-aarch64, qemu-img,
├─ core/runtime  manifest → download → SHA-256 →      │                    virtio-gpu modules, e2fsprogs
│                safe tar extract → self-test →       ├─ SHA256SUMS
│                atomic activation                    └─ manifest.json   schema 2, generated from the archive
├─ core/proot    starts guest processes via
│                lib/arm64-v8a/libproot.so (Bionic)   App-private storage (files/)
├─ core/vm       device DB, QEMU argv builder,        ├─ runtime/current.json + versions/<v>/rootfs
│                VmController, foreground service     ├─ devices/devices.json + <id>/ (disks, logs)
├─ core/images   Google sys-img catalogue + install   ├─ images/<id>/ (Android system images)
└─ GuestDisplayActivity + RfbClient (127.0.0.1 VNC)   └─ logs/activity.json
```

Host-side and guest-side binaries are kept strictly apart:

* **Android/Bionic (in the APK):** `libproot.so`, `libproot-loader.so`, `libtalloc.so`,
  `libandroid-shmem.so` — Termux's PRoot build, fetched and SHA-256 pinned by
  `scripts/fetch-termux-proot.sh`, with `NEEDED`/`RUNPATH` rewritten for `nativeLibraryDir`.
* **Linux/musl (in the runtime archive):** everything QEMU needs. These binaries only run
  inside the Alpine root under PRoot; they are never executed directly on Bionic.

QEMU's VNC server and QMP monitor listen on `127.0.0.1` only. Guest disks, logs and the
launch command for each device live in `files/devices/<id>/`.

### Key decisions

* **Runtime outside the APK.** The APK is ~5 MB; the 34 MB runtime is versioned and verified
  independently, can be repaired or updated without reinstalling the app, and the previous
  runtime keeps working until a new one has passed extraction, required-path and on-device
  self-test checks.
* **Own tar extractor.** Android 9's toybox has no reliable `tar`; the Kotlin extractor handles
  symlinks, hard links (with copy fallback, since apps are often denied `link()`), GNU long names
  and pax headers, and refuses traversal or writes through symlinks.
* **Alpine QEMU instead of Termux QEMU.** Termux's `qemu-system-aarch64` drags in PulseAudio,
  JACK, SPICE and ~40 other Termux libraries with a hard-coded `/data/data/com.termux` prefix.
  Alpine's musl build under PRoot is self-contained and signature-verified by `apk`.
* **No `kernel-qemu`.** The previously bundled brillo `kernel-qemu` is a 3.10 goldfish kernel that
  cannot boot any API 24–37 image; Android images bring their own `kernel-ranchu`.
* **Material 3.** Stable Compose Material3 1.4 keeps `MaterialExpressiveTheme` internal, so
  Expressive tokens (shape scale, emphasised type, spring motion) are applied through `MaterialTheme`
  rather than depending on an alpha library.
* **One running device at a time.** TCG guests are CPU- and RAM-heavy; phones like the Huawei Y9 2019
  have 3–4 GB of RAM.

## Building

Requirements: JDK 17, Android SDK platform 36 + build-tools 36.0.0, Linux host with
`patchelf` and `binutils` (for the PRoot fetch). The Gradle wrapper (8.14.3) is checksum-pinned.

```sh
# Native PRoot libraries (not committed; written to app/src/main/jniLibs/)
bash scripts/fetch-termux-proot.sh apps/experience-virtual-os/app/src/main/jniLibs/arm64-v8a

cd apps/experience-virtual-os
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
python3 ../../scripts/verify-experience-virtual-os-apk.py app/build/outputs/apk/debug/app-debug.apk
```

Building the runtime archive (Linux, as root because `apk` writes a package database):

```sh
sudo bash scripts/build-experience-virtual-os-runtime.sh runtime-dist
python3 scripts/generate-runtime-manifest.py --dist runtime-dist \
  --repo tahmidgaming700/Experience-Ui-Apps --tag runtime-v0.6.0 --min-app-version-code 6
```

To ship the runtime inside the APK (offline install, ~38 MB APK), add
`-PbundleRuntime=/path/to/runtime-dist`; the build checks `base.tar.gz` against `SHA256SUMS` first.

Set `EVOS_RUNTIME_ARCHIVE=$PWD/runtime-dist/base.tar.gz` when running the unit tests to also
extract the real archive with the app's extractor.

### Tests

* `TarExtractorTest` — links, modes, long names, pax, traversal/absolute/symlink-escape rejection,
  corrupt and truncated archives, real runtime archive.
* `RuntimeInstallerTest` — install, checksum mismatch keeps the old runtime, incomplete archives,
  failed self-test, resumed downloads, update/repair, storage checks, manifest validation.
* `DeviceAndQemuTest` — persistence, duplicate/import/export, validation, QEMU argv for Linux,
  KVM and Android profiles, Google catalogue parsing, GPT partition detection.
* `UiSmokeTest` (Robolectric, API 28) — walks the setup wizard and main tabs.

## CI and releases

`.github/workflows/build-experience-virtual-os.yml` builds the runtime, the APKs and all tests on
every push. Tags `v*` / `runtime-v*` create a **draft** GitHub Release with the APK, `base.tar.gz`,
`SHA256SUMS`, `manifest.json` and generated notes. A maintainer publishes it after testing on a
device; only then does `releases/latest/download/manifest.json` resolve for the app. See
[`docs/experience-virtual-os-runtime-releases.md`](../../docs/experience-virtual-os-runtime-releases.md).

## Testing on a Huawei Y9 2019 (JKM-LX2, Kirin 710, EMUI 9.1)

1. Download `experience-virtual-os-debug.apk` (or the release APK) and `experience-virtual-os-runtime`
   from the workflow run's artifacts, or from a draft release.
2. Install the APK (Settings › Security › allow installs from your file manager), open it and go
   through setup. The "Checking this phone" step should show ARM64, Android 9 (API 28) and
   *Software emulation (TCG)*; no `/dev/kvm` is expected on this phone.
3. EMUI kills background apps aggressively: Settings › Battery › App launch › Experience Virtual OS
   › *Manage manually*, enable all three toggles.
4. Install the runtime: APKs built with `-PbundleRuntime=<runtime-dist>` include it, so tap
   **Install included runtime** (no network needed). Otherwise tap **Download and install** (needs a
   published release) or **Install from files** and pick `base.tar.gz` then `manifest.json`. The
   install ends with an on-device self-test (`qemu-system-aarch64 --version` through PRoot). If that
   self-test fails, enable *Settings › PRoot compatibility mode* and retry, then share the
   diagnostics report.
5. Create a **Linux ARM64** device (1024 MB, 2 vCPU, 8 GB disk), open it, **Attach installer ISO**
   (e.g. `alpine-virt-3.24.4-aarch64.iso`, 70 MB) and **Start**. Watch the *Serial console* tab:
   UEFI, then the Linux kernel, then a login prompt. Expect several minutes on a Kirin 710.
   **Display** opens the guest framebuffer; touch acts as an absolute pointer and the *Keyboard*
   button opens the on-screen keyboard. Log in as `root`.
6. **Shut down** sends an ACPI power-down through QMP and falls back to termination.
7. Optional, experimental: install the *Android 11 · API 30 · default* image (548 MB) from Images,
   create an Android device (≥ 2048 MB) and start it. Expect kernel and `init` output in the serial
   console and repeated `surfaceflinger`/`zygote` restarts — there will be no Android screen.

Please report results (Settings › Diagnostics › Share report, plus the Serial and QEMU logs).

## Third-party software

See [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
