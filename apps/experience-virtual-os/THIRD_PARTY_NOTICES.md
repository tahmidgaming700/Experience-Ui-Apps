# Third-party software

## In the APK (`lib/arm64-v8a/`)

| Component | Version | Licence | Source |
|---|---|---|---|
| PRoot (Termux build) | 5.1.107.102 | GPL-2.0-or-later | https://github.com/termux/proot, package `proot` from https://packages.termux.dev |
| talloc | 2.5.0 | LGPL-3.0-or-later | https://talloc.samba.org, Termux package `libtalloc` |
| libandroid-shmem | 0.7 | BSD-3-Clause | https://github.com/termux/libandroid-shmem |
| gVisor (`libevos_runsc.so`, `libevos_gvisor_*.so`) | commit `3eadbee7` (branch `go`) + `patches/gvisor` | Apache-2.0 | https://github.com/google/gvisor; built by `scripts/build-experience-gvisor-engine.sh` |

The binaries are the Termux packages unchanged except for ELF dynamic-section edits
(`NEEDED libtalloc.so.2 → libtalloc.so`, `RUNPATH` removed) made by
`scripts/fetch-termux-proot.sh`. Exact `.deb` SHA-256 pins are in that script.

The gVisor files are static arm64 executables named `lib*.so` only so that Android installs them
as executable; this project's modifications are the patches in `patches/gvisor/` (Apache-2.0).

AndroidX, Jetpack Compose, Material Components, Kotlin and kotlinx libraries are Apache-2.0.

## In the runtime archive (`base.tar.gz`)

| Component | Licence |
|---|---|
| Ubuntu 26.04.1 LTS base (glibc, dash, coreutils, apt, …) | per package; see `/usr/share/doc/*/copyright` in the runtime |
| QEMU 10.2 (`qemu-system-aarch64`, `qemu-img`, modules; Ubuntu build) | GPL-2.0 (some parts LGPL-2.1/BSD/MIT) |
| EDK II / AAVMF UEFI firmware (`qemu-efi-aarch64`) | BSD-2-Clause-Patent |
| e2fsprogs | GPL-2.0-or-later AND LGPL-2.0-or-later AND BSD-3-Clause AND MIT |

Exact package versions are recorded in `runtime-info.json` inside each archive. Sources are
available from https://launchpad.net/ubuntu and `apt-get source` against the Ubuntu archive.

## Downloaded by the user at run time

Android system images are downloaded directly from Google (`dl.google.com`) under the Android
Software Development Kit License Agreement, after the user accepts it in the app. They are not
redistributed by this project.
