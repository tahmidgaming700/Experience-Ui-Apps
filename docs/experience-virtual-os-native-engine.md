# Native engine (AndVM-style)

Status: **experimental, included in the app since 0.8.0** as the "Native (exp.)" device type.
Last updated 2026-11-22.

## What it is

The QEMU engine emulates a whole machine: every guest instruction is translated in software (TCG),
which is slow on a phone without KVM. The native engine instead runs a Linux userspace **directly
on the phone's CPU**. A user-space kernel, [gVisor](https://gvisor.dev) (Apache-2.0), handles the
guest's system calls. This is the architecture AndVM (`com.lerist.andvm`, closed source) uses. We
studied AndVM's APK only to understand the design. This project uses no AndVM code or binaries;
it builds the same idea from open-source gVisor plus its own patches.

| | QEMU engine ("Linux VM") | Native engine ("Native (exp.)") |
|---|---|---|
| CPU | Emulated (TCG) | Native arm64 instructions |
| Guest kernel | Real Linux kernel booted from disk | gVisor sentry (Linux ABI in Go) |
| Start time | Firmware + kernel boot | Container start only (no boot); not yet measured on a phone |
| Display | virtio-gpu over VNC | None yet (console / command runner) |
| Guests | Any arm64 UEFI ISO, Android images (console) | The runtime's Ubuntu 26.04 userspace |

## How it runs inside an app

Stock gVisor needs things an Android app never has: user namespaces (`CONFIG_USER_NS` is off in
Android kernels), mount namespaces, `chroot`, `pivot_root` and recent syscalls (`openat2`,
`statx`, `pidfd_*`). Android 9 runs Linux 4.9, which lacks several of these.
`patches/gvisor/0001-android-host-mode.patch` adds an `--android-host` mode:

* **No namespaces.** The sandbox and gofer start as plain child processes. No userns, no mount
  namespace, no `chroot`, no `MS_SLAVE` remount, no self bind-mount of the root.
* **Gofer serves host paths directly.** The gofer serves the rootfs and bind mounts by host path
  instead of from inside a chroot. It walks paths one component at a time with `O_NOFOLLOW`, so
  symlinks cannot escape the root. DirectFS is off.
* **Root ID mapping.** The app's uid/gid appear as `0:0` inside the guest. `chown` to other ids
  is accepted and ignored, as in rootless containers, because an unprivileged app cannot change
  file owners.
* **Kernel compatibility (`pkg/hostcompat`).** `statx` falls back to `fstatat`. `openat2` falls
  back to a component-wise `openat` walk with `O_NOFOLLOW`. `pidfd_open`/`pidfd_send_signal`
  fall back to `kill(2)` and `/proc` polling.
* **Android's app seccomp filter.** Zygote starts every app process with a seccomp allowlist for
  its platform release. Any other syscall gets `SECCOMP_RET_TRAP` (SIGSYS), even one that is
  merely newer than the release. On Android 11 (extracted from `libandroid_runtime.so`), the
  trapped set includes `openat2`, `pidfd_send_signal`, `close_range`, `faccessat2`,
  `io_uring_*`, `clone3`, `rseq`, the set*id calls, `mount` and `chroot`: 119 of the 328 named
  arm64 syscalls. gVisor
  probes several of these, and so does the Go standard library: it probes `pidfd_send_signal`
  before starting a process. Go treats SIGSYS as fatal. `pkg/hostcompat` therefore installs a
  small assembly SIGSYS handler on Android hosts. It turns a seccomp-trapped call into an
  `-ENOSYS` return, as on a kernel without that syscall, so the fallbacks above take over.
* **39-bit address spaces.** Many Android kernels give processes a 39-bit virtual address space.
  gVisor's ptrace platform did not configure the arm64 mmap layout for it (systrap does), so
  layouts randomly fell outside the address space (`Invalid MmapLayout`). The patch configures it
  for ptrace too.
* **arm64 seccomp programs.** gVisor's Go-branch checkout ships the systrap/ptrace/KVM stub
  seccomp programs precompiled for amd64 only, which made the sentry panic on arm64.
  `tools/evos_precompile` (in the patch) regenerates them for arm64 at build time.

The app ships the engine as five static arm64 executables in `lib/arm64-v8a/`. Android only lets
an app execute files that the installer placed in `nativeLibraryDir`, so they are named `lib*.so`:

| File | Upstream name | Role |
|---|---|---|
| `libevos_runsc.so` | `runsc` | OCI runtime CLI, gofer |
| `libevos_gvisor_sentry.so` | `gvisor_sentry` | the user-space kernel |
| `libevos_gvisor_prewarmer.so` | `gvisor-sentry-prewarmer` | C sidecar |
| `libevos_gvisor_fdparking.so` | `runsc-fd-parking` | C sidecar |
| `libevos_gvisor_procumounter.so` | `gvisor-proc-umounter` | C sidecar |

`NativeEngine.kt` symlinks the sidecars under their upstream names in `files/engine/bin` and
points `GVISOR_SIDECAR_BINARIES_DIR` at that directory. It writes an OCI bundle whose root is the
installed runtime's Ubuntu rootfs. The rootfs is shared and never modified: writes go to gVisor's
overlay. `/root` is a persistent per-device bind mount. The app then runs
`runsc --android-host --network=host --platform=systrap|ptrace run --detach`.

Build: `scripts/build-experience-gvisor-engine.sh <out>` (CI job `engine`, native ARM64 runner).

## Test results

### Android 11 arm64 (kernel 5.4), unprivileged `shell` user, no user namespaces

| Test | systrap | ptrace |
|---|---|---|
| Alpine `/bin/sh`: `uname`, file I/O, 300k-iteration shell loop, persistent bind mount | ✓ | ✓ |

No app seccomp filter applies to `adb shell`, so the next test adds it.

### Under Android's real app seccomp filter (Android 11 arm64 guest)

An app process differs from `adb shell` in its seccomp filter and its uid. The arm64 app filter
was extracted from the guest's own `libandroid_runtime.so`. A small launcher installs it exactly
as zygote does (`PR_SET_NO_NEW_PRIVS` + seccomp; `/proc/self/status` shows `Seccomp: 2`). The
engine then ran as the unprivileged `shell` user, with the flags, environment, sidecar links and
OCI bundle that `NativeEngine.kt` generates. The rootfs was the real **Ubuntu 26.04.1 runtime**
(`base.tar.gz` 0.7.1).

| Test | Result |
|---|---|
| Negative control: previous build without SIGSYS trap emulation | ✗ killed by SIGSYS (as expected; shows the filter is faithful) |
| systrap: `run` self-test (`/bin/sh` as guest root, write to persistent `/root`) | ✓ |
| systrap: `run --detach`, `state` = running, `exec` (prints `Ubuntu 26.04.1 LTS`, 162 dpkg packages, reads `/root`), `kill`, `delete` | ✓ |
| ptrace: `run` self-test, 3 of 3 runs (after the 39-bit address-space fix described above) | ✓ |

Timings in this setup say nothing about phones: the whole Android system ran under TCG
emulation on an x86_64 server. Even so, once files were cached, a detached start took about 2 s
and a complete self-test run about 6 s there.

Tools: `scripts/engine-test/extract-app-seccomp-filter.py` extracts the filter from a device's
`libandroid_runtime.so`, and `scripts/engine-test/applock.c` is the launcher.

The debug APK also contains `EngineProbe` (`am instrument -w -e rootfs <dir>
com.experience.virtualos/.debug.EngineProbe`). On a real phone or emulator it runs the same
lifecycle inside the actual app process, including its SELinux domain. The test system above
cannot run it, because its framework never finishes booting without a GPU.

### x86_64 Linux with a simulated Android 9 kernel

Verified on Linux with a seccomp wrapper that makes the kernel behave like Android 9 (`EINVAL`
for `CLONE_NEWUSER`, `ENOSYS` for `statx`, `openat2`, `pidfd_*`):

* run, `--detach`, `exec`, `kill` and `delete` work;
* detached start takes about 75 ms;
* a bind-mounted `/root` persists;
* the app's bundle layout works, including the `libevos_*.so` names and symlinked sidecars.

The test machines ran under TCG emulation, so their timings say nothing about phone speed.

## Limitations (honest list)

* **Not yet run on a physical phone.** The Huawei Y9 2019 target runs EMUI 9.1 on kernel 4.9.
  The Android 9 code paths were exercised only in the simulator described above.
* **SELinux.** The Android test system was permissive. On a phone, `untrusted_app` may deny
  some of the engine's operations. The app reports such failures; it never fakes a start.
* **No Android guests yet.** Booting an Android GSI under the sentry needs binder/binderfs,
  ashmem, the property service and SELinux handling in gVisor. Graphics would need gfxstream,
  kumquat and ANGLE as in AndVM. Snapshots for instant start would need checkpoint/restore.
  None of this exists yet.
* **Networking** uses gVisor's host network passthrough (`--network=host`), which is limited to
  what an Android app can do: no raw sockets, no listening on privileged ports.

## Roadmap

1. ✓ Sentry runs without namespaces on Android kernels (`--android-host`).
2. ✓ Packaged in the app: "Native (exp.)" devices, runtime self-test, command runner.
3. Device testing on Android 9/10/12+ phones, SELinux fixes as found.
4. Interactive terminal (PTY) and a guest display (Wayland/X server rendered to a `Surface`).
5. Android userspace: binder, ashmem/memfd, property service; boot an AOSP GSI to `zygote`.
6. Graphics (gfxstream + kumquat + ANGLE) and checkpoint/restore for instant start.
