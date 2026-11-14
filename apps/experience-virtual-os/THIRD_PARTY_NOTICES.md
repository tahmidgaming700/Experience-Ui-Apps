# Third-party software

## In the APK (`lib/arm64-v8a/`)

| Component | Version | Licence | Source |
|---|---|---|---|
| PRoot (Termux build) | 5.1.107.102 | GPL-2.0-or-later | https://github.com/termux/proot, package `proot` from https://packages.termux.dev |
| talloc | 2.5.0 | LGPL-3.0-or-later | https://talloc.samba.org, Termux package `libtalloc` |
| libandroid-shmem | 0.7 | BSD-3-Clause | https://github.com/termux/libandroid-shmem |

The binaries are the Termux packages unchanged except for ELF dynamic-section edits
(`NEEDED libtalloc.so.2 → libtalloc.so`, `RUNPATH` removed) made by
`scripts/fetch-termux-proot.sh`. Exact `.deb` SHA-256 pins are in that script.

AndroidX, Jetpack Compose, Material Components, Kotlin and kotlinx libraries are Apache-2.0.

## In the runtime archive (`base.tar.gz`)

| Component | Licence |
|---|---|
| Alpine Linux 3.24 minirootfs (busybox, musl, apk-tools, …) | busybox GPL-2.0-only, musl MIT, others per package |
| QEMU 11.0 (`qemu-system-aarch64`, `qemu-img`, modules) | GPL-2.0-only AND LGPL-2.1-only (Alpine package metadata) |
| EDK II firmware shipped with QEMU (`edk2-aarch64-code.fd`) | BSD-2-Clause-Patent |
| e2fsprogs | GPL-2.0-or-later AND LGPL-2.0-or-later AND BSD-3-Clause AND MIT |

Exact package versions are recorded in `runtime-info.json` inside each archive. Sources are
available from https://pkgs.alpinelinux.org and https://gitlab.alpinelinux.org/alpine/aports.

## Downloaded by the user at run time

Android system images are downloaded directly from Google (`dl.google.com`) under the Android
Software Development Kit License Agreement, after the user accepts it in the app. They are not
redistributed by this project.
