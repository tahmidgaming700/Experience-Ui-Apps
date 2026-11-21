# Native engine prototype (AndVM-style): feasibility report

Status: **experimental research prototype — not used by the app.** Last updated 2026-11-21.

## Why a second engine

The current engine runs QEMU (TCG) under PRoot. Every guest instruction is translated in
software, and Google's Android system images cannot show a screen because their graphics stack
needs the Android Emulator's goldfish pipe (see `experience-virtual-os-guest-compatibility.md`).

AndVM (`com.lerist.andvm`, closed source, Android 10+) shows a different approach works on phones:
run the Android userspace **natively on the host CPU** under a user-space kernel, with graphics
forwarded to the host GPU. We studied its APK only to understand the architecture; no AndVM code or
binaries are used, and none may be — this project builds the same idea from open-source parts.

## What AndVM is built from (APK inspection, 0.0.8 BETA)

| Function | Evidence in the APK | Open-source equivalent |
|---|---|---|
| User-space kernel | `libandvm_sc.so` contains `gvisor.dev/gvisor/pkg/sentry/...`; logs say `isolation=sentry` | gVisor (Apache-2.0) |
| Guest boot/init | `libandvm_guest_init.so` (`start zygote`, SELinux, VINTF, apexd handling) | AOSP init + custom glue |
| GPU | `libgfxstream_backend.so`, `libandvm_kumquat.so`, `libandvm_angle_*` | gfxstream (Apache-2.0), rutabaga/kumquat (BSD), ANGLE (BSD) |
| Guest graphics HALs | `composer_ranchu`, `guest_gles*`, `guest_vulkan_ranchu`, kumquat allocator/mapper | AOSP Cuttlefish/emulator HALs |
| Disk images | `mkfs_erofs`, `erofs_reader`, `ext4_reader` | erofs-utils, e2fsprogs |
| Fast resume | `andvm_kumquat_snapshot/restore`, `libandvm_snapshot_storage.so` | gVisor checkpoint/restore |
| Guest OS | GSI userspace (`gsi-service-manager-policy`, `hwservicemanager`) | AOSP GSI |

## Feasibility results (real tests)

Host: Android 11 arm64 (kernel 5.4.86), unprivileged `shell` user, gVisor `runsc` built from
commit `3eadbee7` as a static linux/arm64 Go binary (`scripts/build-experience-gvisor-engine.sh`).

| Step | Result |
|---|---|
| Build `runsc` for arm64, no libc dependency | ✓ 12 MB static binary |
| Stock rootless modes (`--rootless`, systrap/ptrace, `run`/`do`) | ✗ `fork/exec /proc/self/exe: invalid argument` — Android kernels are built without `CONFIG_USER_NS` (`/proc/self/uid_map` does not exist) |
| `--TESTONLY-unsafe-nonroot` | ✗ needed `/proc/self/uid_map` → **fixed** by `patches/gvisor/0001-identity-id-mapping-without-user-namespaces.patch` |
| After the patch | ✗ `cannot create gofer process: unable to run a rootless container without userns` |

Conclusion so far: gVisor's **sentry** can be compiled for Android hosts, but `runsc`'s container
model (separate gofer and sandbox processes isolated with namespaces) cannot start on Android. A
dedicated Android launcher is required that starts the sentry directly, without namespaces, with
direct host file access instead of a gofer. AndVM's `isolation=sentry` strongly suggests it did
exactly that.

Not yet tested: Android's per-app seccomp filter (the `shell` user is not subject to it), the
systrap platform's stub processes under that filter, and Android 9 kernels (4.9).

## Roadmap (each milestone is independently testable)

1. **Sentry launcher for Android** — boot the sentry in-process with `directfs`, no gofer, no
   namespaces; run `/bin/sh` from an Alpine/Ubuntu rootfs natively. *Go/no-go milestone.*
2. **Inside an app process** — repeat under the app seccomp filter on Android 10+ and Android 9.
3. **Android userspace** — binder/binderfs, ashmem/memfd, property service, SELinux handling in
   the sentry; boot an AOSP GSI to `zygote`.
4. **Graphics** — gfxstream + kumquat host server, ANGLE on the host GPU, ranchu composer/gralloc
   in the guest; render to an Android `Surface` in the app.
5. **Fast start** — checkpoint a booted guest and restore it (seconds instead of minutes).
6. **Productisation** — device manager integration, storage (EROFS system, ext4 data), input,
   audio, networking.

Realistic effort: several engineer-months; milestones 1–2 decide whether it is viable at all.
