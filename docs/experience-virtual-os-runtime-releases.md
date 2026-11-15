# Experience Virtual OS runtime releases

The runtime is distributed separately from the APK as a GitHub Release asset set:

| Asset | Produced by | Purpose |
|---|---|---|
| `base.tar.gz` | `scripts/build-experience-virtual-os-runtime.sh` | `runtime-info.json` + `rootfs/` (Ubuntu 26.04.1 arm64 + QEMU) |
| `SHA256SUMS` | same | SHA-256 of `base.tar.gz` |
| `manifest.json` | `scripts/generate-runtime-manifest.py` | Machine-readable description, generated from the built archive |
| `experience-virtual-os-release*.apk` | Gradle (R8) | The app; signed when signing secrets are configured |

The app reads `https://github.com/tahmidgaming700/Experience-Ui-Apps/releases/latest/download/manifest.json`.
GitHub serves `latest` only for **published, non-draft, non-prerelease** releases, so the app sees
nothing until a maintainer publishes a release (it then reports "No runtime release has been
published yet").

## Pipeline

1. **runtime job** (native ARM64 runner) — downloads `ubuntu-base-26.04.1-base-arm64.tar.gz` (SHA-256 pinned), resolves and downloads QEMU packages with signature-verified apt, installs them with the root's dpkg in a chroot, then
   (SHA-256 pinned), installs QEMU with signature-checked `apk`, verifies ELF architecture and
   library closure, runs QEMU under `qemu-aarch64` (version + VNC handshake), packs a reproducible
   archive and generates `manifest.json`.
2. **android job** — fetches the pinned Termux PRoot, runs unit, Robolectric UI and real-archive
   extraction tests, lint, builds debug + release APKs and inspects them.
3. **release job** (tags `v*` / `runtime-v*` only) — re-checks `SHA256SUMS` and the manifest tag,
   writes release notes with the compatibility statement and publishes the release as **latest**.

## Cutting a release

```bash
# bump scripts/experience-virtual-os-runtime-version and versionCode/versionName if needed
git tag v0.6.0
git push origin v0.6.0
```

Then, before publishing the draft:

1. Install the draft's APK on an ARM64 phone; install the runtime with *Install from files*
   using the draft's `base.tar.gz` + `manifest.json`. The install must pass the on-device self-test.
2. Boot a Linux ARM64 ISO and confirm serial output and the display.
3. Confirm the release notes match what you observed. Then publish.

Configure `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`,
`ANDROID_KEY_PASSWORD` as repository secrets for a signed, updatable release APK. Without them the
release APK is unsigned (not installable); the debug APK from the workflow artifacts is
installable but signed with a throwaway CI key.

## Manifest schema 2

```json
{
  "schemaVersion": 2,
  "kind": "experience-virtual-os-runtime",
  "runtimeVersion": "0.7.0",
  "releaseTag": "v0.7.0",
  "abi": "arm64-v8a",
  "minHostApi": 28,
  "minAppVersionCode": 7,
  "archive": {
    "fileName": "base.tar.gz",
    "url": "https://github.com/<owner>/<repo>/releases/download/<tag>/base.tar.gz",
    "format": "tar+gzip",
    "sizeBytes": 33585717,
    "sha256": "<64 hex>",
    "unpackedSizeBytes": 239831176,
    "entries": 1018
  },
  "components": { "userspace": "ubuntu 26.04.1", "libc": "glibc", "qemu": "1:10.2.1+ds-1ubuntu3.3" },
  "entrypoints": {
    "shell": "rootfs/usr/bin/sh",
    "qemuSystemAarch64": "rootfs/usr/bin/qemu-system-aarch64",
    "qemuImg": "rootfs/usr/bin/qemu-img",
    "uefiFirmware": "rootfs/usr/share/AAVMF/AAVMF_CODE.fd",
    "uefiVars": "rootfs/usr/share/AAVMF/AAVMF_VARS.fd",
    "mke2fs": "rootfs/usr/sbin/mke2fs"
  },
  "requiredPaths": ["…every entrypoint…", "runtime-info.json", "rootfs/usr/lib/ld-linux-aarch64.so.1", "rootfs/etc/os-release"],
  "capabilities": { "linuxUserspace": "supported", "linuxGuestArm64": "experimental", "androidGuest": "experimental-console-only" },
  "notes": "…"
}
```

The app rejects a manifest whose schema, kind, ABI, minimum host API or minimum app version do
not match, whose URL is not HTTPS on `github.com`, whose SHA-256 is malformed, or whose paths are
absolute or contain `..`. The archive is rejected on any size or SHA-256 mismatch *before*
extraction.

## Installation on the device

```
fetch manifest → validate → free-space check → resumable download (.part)
→ size + SHA-256 → safe extraction into runtime/staging/<v>-<time>
→ required paths present → chmod entrypoints → PRoot self-test (qemu --version)
→ rename into runtime/versions/<v> → atomic write of runtime/current.json
→ delete other versions and the archive
```

Any failure deletes the staging directory and leaves `current.json` (and the runtime it points
to) untouched. A crash mid-extraction leaves only `staging/`, which is cleaned on next start.
