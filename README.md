# Experience UI OTA Updater & GSI Integration

Experience UI OTA Updater is a secure system update client designed to verify, inspect, and stage Generic System Images (GSIs) from the official Experience UI GitHub repository.

---

## 1. Official GitHub OTA Manifest

The application uses the official GitHub raw manifest as its **single source of truth**:

```text
https://raw.githubusercontent.com/tahmidgaming700/Experience-UI/main/ota/manifest.json
```

The updater fetches this exact URL over HTTPS. It does not invent versions, URLs, or checksums, and halts the installation workflow whenever a release package URL or SHA-256 hash has not yet been published in the manifest.

### Central Configuration
Defined centrally in:
```kotlin
com.example.data.config.ExperienceOtaConfig.OFFICIAL_MANIFEST_URL
```

---

## 2. Manifest Schema

The manifest JSON defines versioning, security patch levels, release notes, release asset URLs, and GSI image checksums per release channel:

```json
{
  "schema_version": 1,
  "project": "Experience UI",
  "stable": {
    "version": "Experience UI v1.0 — Android 12 ARM64 GSI",
    "security_patch": "2026-10-05",
    "release_notes": "Official Experience UI Android 12 GSI release.\nIncludes Experience Launcher, Settings, SetupWizard, Dialer, Messages, Camera, Files, and Boot Animation.",
    "package_url": "https://github.com/tahmidgaming700/Experience-UI/releases/download/v1.0/ExperienceUI-Android12-arm64.zip",
    "sha256": "58d1e17ffe5109a7ae296caafcadfdbe6a7d176f0bc4ab01e12a689b0499d8bd",
    "android_base": "12.0",
    "architecture": "arm64-v8a",
    "target_partition": "system",
    "download_size": 1500000000
  },
  "early_access": {
    "version": "Experience UI 2 — Android 13 — not released",
    "security_patch": "Not published",
    "release_notes": "Early-access placeholder. Do not install until a device-compatible build, signing, and recovery instructions are published.",
    "package_url": "",
    "sha256": ""
  }
}
```

### Field Definitions
| Field | Type | Description |
|---|---|---|
| `schema_version` | integer | Manifest schema revision (>= 1). |
| `project` | string | Target project identity ("Experience UI"). |
| `stable` / `early_access` | object | Release channel blocks. |
| `version` | string | User-facing build version name. |
| `security_patch` | string | Android Security Bulletin date (e.g. `2026-10-05`). |
| `release_notes` | string | Formatted changelog highlights and precautions. |
| `package_url` | string | Permanent HTTPS URL to the release ZIP asset on GitHub Releases. |
| `sha256` | string | **Official cryptographic SHA-256 of the extracted GSI image** (under `images/`). Never use the outer ZIP checksum. |
| `android_base` | string (optional) | Android release version (e.g. `12.0`). |
| `architecture` | string (optional) | CPU architecture target (e.g. `arm64-v8a`). |
| `target_partition` | string (optional) | Target flash volume (default: `system`). |
| `download_size` | number (optional) | Total package download size in bytes. |

---

## 3. Release Asset Structure & Naming

GitHub release packages are distributed as unified ZIP archives following the naming scheme:
`ExperienceUI-Android12-arm64-<version>.zip`

### Archive Contents
```text
ExperienceUI-Android12-arm64.zip
├── images/
│   └── ExperienceUI-Android12-arm64-experimental.img  <-- Extracted & verified GSI
├── apks/
│   ├── Experience-launcher.apk
│   ├── Experience-settings.apk
│   ├── Experience-setupwizard.apk
│   ├── Experience-dialer.apk
│   ├── Experience-messages.apk
│   ├── Experience-camera.apk
│   └── Experience-files.apk
├── bootanimation/
│   └── bootanimation.zip
└── metadata/
    ├── BUILD_INFO.txt
    └── ExperienceUI-image.sha256
```

---

## 4. Checksum Generation & Publishing Process

1. **Build & Package GSI**:
   Run the repository build script (`termux/build-gsi.sh` or host ROM integration script) to produce the output image under `image/` or `images/`.

2. **Compute Image SHA-256**:
   Compute the cryptographic hash **directly on the uncompressed `.img` file**:
   ```bash
   sha256sum images/ExperienceUI-Android12-arm64-experimental.img
   ```
   *CRITICAL*: Do not compute the hash on the ZIP file. The manifest's `sha256` field must strictly match the extracted system image.

3. **Publish GitHub Release**:
   Upload the release ZIP to GitHub Releases under `tahmidgaming700/Experience-UI/releases`.

4. **Update `ota/manifest.json`**:
   Copy the permanent release asset URL and the calculated image SHA-256 into `ota/manifest.json`.

---

## 5. DD Flasher Integration & Privileged Requirements

The updater integrates with the project's DD Flasher module:

### Flashing Safety Principles
1. **Never Automatically Flash**: Downloading and verifying an image does not initiate flashing. A dedicated confirmation screen reviews target partition details, file sizes, and hardware compatibility.
2. **SELinux & Root Boundaries**: Standard Android applications run in the `untrusted_app` SELinux domain without direct access to raw block devices (`/dev/block/by-name/system`).
3. **Privilege Detection**:
   - If root (`su`) is present: DD Flasher executes `su -c dd if='<path>' of='<partition>' bs=4M status=progress` and monitors process exit codes.
   - If root is absent: Explains the permission constraint clearly, prevents destructive errors, and generates the exact commands for **Termux (rooted)** and **PC Fastboot**.

### Recovery Precautions & Supported Devices
- **Huawei Y9 2019 (JKM-LX2)**: Research-only target. Bootloader unlock and EMUI 9.1 Treble partition compatibility must be established before attempting installation.
- **Samsung Galaxy Tab S 10.5 (SM-T805 / chagalllte)**: Research-only legacy target. Requires compatible vendor/kernel blobs.
- **Fastbootd Recovery**: For devices with Dynamic Partitions (Android 10+):
  ```bash
  adb reboot fastboot
  fastboot flash system system.img
  fastboot -w
  fastboot reboot
  ```
