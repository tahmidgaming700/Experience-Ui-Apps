# Experience Virtual OS — guest compatibility and implementation matrix

Last verified: 2026-11-14. Every "verified" entry below was actually executed; the evidence
files are in [`docs/evidence/`](evidence/). Nothing here was tested on a physical phone yet —
see "Not yet verified".

## Terms (they are not interchangeable)

| Term | What it is | Relevance here |
|---|---|---|
| Alpine Linux ARM64 userspace | musl-based Linux root filesystem | The runtime (`base.tar.gz`) |
| PRoot | ptrace-based fake-chroot; runs Linux userspace binaries on an Android kernel without root | Launches everything inside the runtime |
| Android-hosted QEMU | QEMU compiled for Bionic (e.g. Termux) | **Not used** (dependency tree too large) |
| Linux-hosted QEMU | QEMU compiled for glibc/musl Linux | Used: Alpine's musl QEMU, run under PRoot |
| Android Emulator | Google's fork of QEMU ("AEMU") + goldfish/ranchu devices, gfxstream GPU | Only built for Linux/macOS/Windows hosts; **not available on Android** |
| Goldfish / Ranchu | The emulator's virtual board and its paravirtual devices (`goldfish_pipe`, address-space, battery, sync…) | Upstream QEMU implements none of the pipe devices |
| Android emulator system image | `kernel-ranchu`, `ramdisk.img`, `system.img` (super), `vendor.img`, … for the emulator | Downloadable; boots only partially on upstream QEMU (below) |
| Android GSI | Generic System Image for **physical** Treble devices | Needs a device's own vendor/boot; not a VM disk |
| `kernel-qemu` (brillo) | A 3.10 goldfish kernel from 2016 | Removed — cannot boot API 24–37 images |

## Host (Huawei Y9 2019 class)

| Check | How | Result on target (expected) |
|---|---|---|
| ABI | `Build.SUPPORTED_ABIS` | arm64-v8a ✓ |
| Android version | `Build.VERSION.SDK_INT` | 28 ✓ (minSdk 28) |
| KVM | `/dev/kvm` exists + readable + writable | Not present on Kirin 710 / EMUI 9 → TCG |
| W^X for app data | targetSdk 35, but executables are launched only from `nativeLibraryDir` (PRoot) | ✓ Android 10+ safe as well |
| Root / bootloader | never used | ✓ |

## Guest matrix

| Guest | Runtime path | Status | Evidence |
|---|---|---|---|
| Alpine Linux 3.24 aarch64 `virt` ISO (UEFI) | `qemu-system-aarch64 -M virt -cpu cortex-a57`, edk2 pflash, virtio-scsi CD, virtio-blk qcow2, virtio-gpu, USB HID, VNC/QMP on 127.0.0.1 | **Verified** (x86_64 Linux host, TCG, identical argv shape): UEFI → kernel 6.18 → login prompt on both serial and the 1280×720 virtio-gpu framebuffer in < 4 min | `evidence/linux-arm64-uefi-boot-virtio-gpu.png`, `evidence/linux-arm64-uefi-boot-serial.txt` |
| Other ARM64 UEFI ISOs (Debian, Ubuntu Server, Fedora) | same | Expected to work (standard `virt` + UEFI targets); untested | — |
| Android 11 (API 30) AOSP `default` arm64-v8a r2 | `-kernel kernel-ranchu -initrd ramdisk.img`, 6 virtio-mmio disks in emulator order, `androidboot.*` params from `VerifiedBootParams.textproto` | **Experimental, console only — verified partial boot**: kernel 5.4.86 boots; first-stage mounts `/metadata`, `/system`, `/vendor`, `/product`, `/system_ext` from `super`; `/data` mounts with FBE; `zygote` and `surfaceflinger` start. `surfaceflinger` aborts (`eglQueryStringImplementationANDROID(EGL_VERSION) failed`), `hwcomposer.ranchu` aborts, `zygote` restarts ~50× in 12 min. **No display.** | `evidence/android-api30-virt-boot-summary.txt` |
| Android 10, 12–17 AOSP `default` / `google_apis` / Play arm64 | same topology | Experimental, untested — same graphics stack, so console only at best | — |
| Android ≤ 9 (API 21–28) arm64 images | non-dynamic partitions; API ≤ 27 use goldfish `kernel-qemu` | **Not compatible** (listed but not installable) | layout check in app |
| `*_ps16k` (16 KB page) images | needs 16 K-page kernel | **Not compatible** | — |
| Android GSIs | physical-device images | **Not compatible** | — |

### Why Android cannot show a screen on this runtime

The AOSP emulator vendor image ships exactly these graphics components (read from the
API 30 `vendor.img`): `/vendor/lib64/egl/libEGL_emulation.so`, `libGLESv1_CM_emulation.so`,
`libGLESv2_emulation.so`, `/vendor/lib64/hw/hwcomposer.ranchu.so`, `gralloc.default.so`.
All of them forward rendering to the host GPU through `goldfish_pipe`/`goldfish_address_space`.
There is no SwiftShader, minigbm or drm_hwcomposer fallback in the image. Upstream QEMU does not
implement the goldfish pipe, and Google's emulator (which does) has no Android-host build.

Paths that *could* produce a working Android display, none implemented:

1. An Android image built with software rendering for a generic `virt`/virtio-gpu target
   (e.g. AOSP `aosp_cf_arm64` Cuttlefish userspace with SwiftShader + drm_hwcomposer, or a
   community virtio-gpu build). This needs a custom AOSP build and its own kernel; not a Google
   SDK image.
2. Porting the goldfish pipe + an EGL backend into QEMU — a major project.
3. Running the Android Emulator itself — impossible on an Android host today.

Even with (1), TCG on a Kirin 710 would make Android's UI extremely slow (expect tens of minutes
to boot and single-digit frames per second).

## Runtime component verification

| Check | Where | Result |
|---|---|---|
| Alpine minirootfs SHA-256 pinned | `build-experience-virtual-os-runtime.sh` | ✓ |
| apk package signatures | `apk.static` with keys from the verified minirootfs | ✓ |
| Every shipped executable is AArch64, musl interpreter, closed `DT_NEEDED` set | `verify-runtime-rootfs.py` (62 ELF objects) | ✓ |
| QEMU executes (`--version`) and its VNC server completes an RFB 3.8 handshake | qemu-user smoke test in build script | ✓ |
| Reproducible archive (two builds, identical SHA-256) | local | ✓ (given identical package versions) |
| PRoot is Bionic (`/system/bin/linker64`), AArch64, deps resolvable from APK + system | `fetch-termux-proot.sh`, `verify-experience-virtual-os-apk.py` | ✓ |
| Runtime archive extracts with the app's own extractor (UTF-8 names, symlinks, hardlinks) | `TarExtractorTest.extractsRealRuntimeArchive` | ✓ |

## Verified on a real Android ARM64 kernel

Android 11 (API 30, kernel 5.4.86, arm64) booted headless on QEMU, serial shell as the unprivileged
`shell` user (uid 2000), using the APK's PRoot libraries and the runtime rootfs with the exact flags
and environment of `ProotLauncher`:

| Step | Result |
|---|---|
| `libproot.so --version` (Bionic, LD_LIBRARY_PATH = lib dir) | ✓ |
| PRoot → Alpine `/bin/sh` (`/etc/alpine-release` = 3.24.4, `uname -m` = aarch64, fake root) | ✓ exit 0 |
| `qemu-system-aarch64 --version` (the app's install self-test) | ✓ QEMU 11.0.3 |
| `qemu-img create -f qcow2` on a path bound from app storage | ✓ |
| `mke2fs -t ext4 -O quota,verity` | ✓ |
| `qemu-system-aarch64 -M virt` with VNC on 127.0.0.1, alive after 12 s | ✓ |

The debug build also contains `DevInstallMain` (debug source set only), which runs the app's real
installer and VM launch code under `app_process` without the UI — useful on a device via adb:
`CLASSPATH=app-debug.apk app_process /system/bin com.experience.virtualos.DevInstallMain <base.tar.gz> <manifest.json> <dataDir> <nativeLibDir>`.

## Not yet verified (needs a physical ARM64 phone)

* The same on Android 9 / EMUI 9.1 with SELinux enforcing in the app domain (the in-app
  self-test checks exactly this during installation, and *Runtime › Run self-test* shows the raw
  output).
* Guest performance and thermal behaviour on Kirin 710 under TCG.
* EMUI background-process limits with the foreground service.
* VNC display, touch and keyboard on a real screen.
