# Experience UI

Experience UI is an Android 11 (API 30) operating system for ARM64 devices, built from
source as a **generic system image (GSI)**: ARM64, A/B, system-as-root, Treble, Vanilla
edition (no Google apps). It layers a Material 3 Expressive-inspired interface, its own
native applications and its own branding on **LineageOS 18.1** with the
**phh/TrebleDroid** GSI patch set.

| | |
|---|---|
| OS | Experience UI 1.0 (`ro.experience.version=1.0.0`) |
| Platform | Android 11, SDK 30 — base LineageOS 18.1 + TrebleDroid |
| Target | `lineage_experience_arm64_bvN-userdebug` → `system.img` (ARM64 A/B SAR GSI) |
| Manufacturer / model (system partition) | Experience / Experience Device |
| Build display | `Experience UI 1.0 (EXP1.0.0.<UTC build id>.userdebug)` |
| Distribution | GitHub Releases (draft pre-releases until boot-verified) |

> **Status, honestly stated.** The 14 Experience apps build, pass their 220 unit tests
> and lint. The ROM tree, patches and overlays are checked against the real LineageOS
> 18.1 sources on every change. **The full ROM has not been compiled yet** — that needs
> a self-hosted build machine (below) — and **no device has booted Experience UI**. See
> [docs/build-status.md](docs/build-status.md) and [docs/compatibility.md](docs/compatibility.md).

## Repository layout

```
apps/experience-suite/        Native Experience apps (Gradle, Kotlin + Jetpack Compose, min/target SDK 30)
  core-design/                Design system: HCT tonal palettes from the system accent, Expressive
                              shapes/type/motion, shared components
  launcher/ settings/ setupwizard/ dialer/ messages/ contacts/ notes/ clock/
  calendar/ calculator/ files/ gallery/ camera/ converter/
rom/
  manifests/                  Local manifest + pins (LineageOS 18.1, phh repos, patch sets)
  device/experience/gsi/      Product: lineage_experience_arm64_bvN (inherits phh treble + Lineage)
  vendor/experience/
    config/                   experience.mk, version.mk, experience.prop (identity, packages, assets)
    overlay/static/           Framework, SystemUI, Settings, Launcher3 recents overlays
    overlay/accents/          Runtime accent overlays (generated)
    prebuilt/apps/            android_app_import modules for the Experience APKs
    permissions/              privapp allowlist (generated), default permissions, sysconfig
    bootanimation/ wallpapers/ sounds/   Generated assets
  patches/
    experience/               Experience source patches (SystemUI, Settings)
    upstream-overrides/       Upstream patches refreshed for current LineageOS heads
    upstream-exclude.txt      Upstream patches deliberately not applied
scripts/rom/                  sync, apply-patches, check-patches, collect-app-prebuilts, build,
                              verify-system-image, package-release, check-tree, asset generators
tests/rom/                    Tests for the ROM scripts
ota/experience-ui-11.json     Update manifest read by Experience Settings › System updates
docs/                         Compatibility, build status, design
.github/workflows/            experience-ui-apps.yml (hosted), experience-ui-rom.yml (self-hosted)
apps/experience-virtual-os/   Experience Virtual OS (separate product, unchanged)
app/                          Legacy standalone GSI updater app (unchanged, see app/README.md)
```

## Experience applications

All 14 apps are native Kotlin + Jetpack Compose apps (no WebViews), min/target SDK 30,
built on the shared `core-design` system and installed in the ROM as platform-signed
system apps. **220 unit tests**; the whole suite passes `testDebugUnitTest`, `lintDebug`
(no errors) and `assembleRelease`.

| App | Package | Partition | Highlights | Not implemented |
|---|---|---|---|---|
| Launcher | `com.experience.launcher` | priv-app, HOME | Paged home grid + dock, searchable drawer over `LauncherApps` (work profile badge/pause), app shortcuts, adaptive icon rendering | Widgets, drag-and-drop; recents stay in Launcher3/Trebuchet |
| Settings | `com.experience.settings` | priv-app | Searchable hub, 15 pages of real controls, Experience Style (seed / wallpaper → palette + nearest accent overlay, icon shapes), About Experience UI, System updates (manifest, download, SHA-256, flashing steps; never flashes), tile in AOSP Settings | Mobile data/airplane are links; update download is not a foreground service |
| Setup Wizard | `com.experience.setupwizard` | priv-app, replaces Provision/LineageSetupWizard | Welcome, language, region, accessibility, Wi-Fi connect, date & time, privacy, contacts import, accounts, navigation mode, done; marks device provisioned, sets Experience Launcher as HOME, reports failed steps | Cloud restore (no Google services) |
| Dialer | `com.experience.dialer` | priv-app, DIALER role | Keypad with DTMF and T9, recents, favourites, InCallService call UI (routes, hold, DTMF, call waiting, conferences, reply with SMS), call notifications | Visual voicemail, video calls, RTT, recording |
| Messages | `com.experience.messages` | app, SMS role | Conversations, threads, multipart SMS with delivery status, receive, direct reply, multi-SIM | **MMS download/send** (AOSP Messaging stays installed) |
| Contacts | `com.experience.contacts` | app, replaces Contacts | List/detail/editor, photos, vCard 2.1/3.0 import/export, INSERT/EDIT/PICK intents | Linking/joining, groups |
| Notes | `com.experience.notes` | app | Notes and checklists, colours, pin/archive/trash, search, autosave, share, pinned shortcuts | — |
| Clock | `com.experience.clock` | app, replaces DeskClock | Alarms (DST-safe, direct boot), world clocks, multiple timers, stopwatch, AlarmClock intents | Silencing alarms during calls |
| Calendar | `com.experience.calendar` | app, replaces Etar | Month/agenda/day views, recurring events (RRULE), reminders and notifications, local calendar | Editing a single occurrence, attendees |
| Calculator | `com.experience.calculator` | app, replaces ExactCalculator | Own BigDecimal engine (exact trig at standard angles), history, scientific panel | — |
| Files | `com.experience.files` | app | Volumes, categories, search, copy/move with progress, zip extraction, GET_CONTENT picker | — (DocumentsUI stays as the platform picker) |
| Gallery | `com.experience.gallery` | app | Timeline, albums, zoom viewer, video, MediaStore delete/favourite requests, pickers | Animated GIF/WebP, in-app editing |
| Camera | `com.experience.camera` | app | CameraX photo/video, flash, timer, grid, focus, zoom, capture intents for other apps | Lock-screen camera, HDR/night extensions |
| Converter | `com.experience.converter` | app | 13 unit categories with exact factors, number bases | Currency (needs a rate source) |

Known gap across apps: UI strings are mostly in Kotlin rather than `strings.xml`, so
translation needs them extracted first.

ROM integration data (privileged permissions, default runtime grants, roles,
overrides) lives in `rom/vendor/experience/prebuilt/apps/Android.bp` and
`rom/vendor/experience/permissions/`; the privileged allowlist is generated from the APKs.


## How Experience UI changes Android

Experience UI modifies the platform in three ways, chosen per component:

1. **Source patches** to Android modules (`rom/patches/experience/`), applied after the
   GSI patch stack:
   - **SystemUI** — the quick-settings footer always shows the Experience UI release
     (plus the Android build ID when developer options are on).
   - **Settings** — About phone › Android version gains an *Experience UI version* row
     that opens Experience Settings › About.
2. **Static resource overlays** (`rom/vendor/experience/overlay/static/`, compiled into
   the target modules and placed ahead of the phh and LineageOS overlays):
   - **Framework**: 28 dp dialog/sheet radius (Android 11 SystemUI uses it for the
     notification cards, the QS panel, media player, dialogs and the power menu), 20 dp
     buttons, pill progress bars, squircle adaptive-icon mask, Experience Indigo accent,
     Experience Dialer/Messages as default dialer/SMS roles, default wallpaper.
   - **SystemUI**: squircle quick-settings tile backgrounds, larger tiles, thicker rounded
     brightness slider, inset notification cards, rounder controls and chips.
   - **Settings**: pill search bar. **Launcher3/Trebuchet recents**: roomier overview.
3. **Runtime overlays and apps**: nine Experience accent overlays (category
   `android.theme.customization.accent_color`), AOSP icon-shape overlays and navigation
   mode overlays, switched by Experience Settings and the Setup Wizard.

Every overlaid resource name is checked to exist in the upstream module
(`scripts/rom/check-tree.py --upstream`), and every patch is checked to apply to the
real LineageOS 18.1 sources (`scripts/rom/check-patches.sh`) in CI.

### Material 3 Expressive on Android 11

Android 11 has no system dynamic-colour palette and Material 3 Expressive APIs postdate
it, so Experience UI implements the direction with what Android 11 provides:

- **Dynamic colour**: one seed colour (the accent chosen in Experience Settings, or
  extracted from the wallpaper with `WallpaperManager.getWallpaperColors`) drives an HCT
  tonal scheme in every Experience app (material-color-utilities, the algorithm Android
  12+ uses), and the nearest accent overlay is enabled for the framework and SystemUI.
  Apps observe the setting and re-theme live with animated colour transitions.
- **Shape**: large-radius shape scale, grouped "segmented" lists, squircle icons and tiles.
- **Type**: emphasised headline weights on the system sans-serif.
- **Motion**: spring-based (damping/stiffness) spatial and effect animations.
- **Accessibility**: text colour roles are unit-tested for WCAG AA (4.5:1) across seeds,
  styles and light/dark; accent overlays are checked for 4.5:1 against their surfaces.
- **Adaptive layouts**: compact / medium / expanded width classes (rails, two panes,
  more grid columns on tablets).

## Building

### 1. Experience apps (any machine, ~10 minutes)

Requirements: JDK 17+, Android SDK (platform 35, build-tools 35).

```bash
cd apps/experience-suite
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest lintDebug assembleRelease
# APKs: <module>/build/outputs/apk/release/<module>-release-unsigned.apk
```

Release APKs are unsigned on purpose: the ROM build re-signs them with the platform key.
For standalone installs, set `EXPERIENCE_KEYSTORE`, `EXPERIENCE_KEYSTORE_PASSWORD`,
`EXPERIENCE_KEY_ALIAS`, `EXPERIENCE_KEY_PASSWORD` to sign release builds, or use
`assembleDebug`. Outside Experience UI the apps run, but system-level features
(overlays, secure settings, roles) fall back to public intents.

### 2. The ROM (self-hosted build machine)

Requirements: **x86_64 Linux** (Ubuntu 20.04/22.04), **≥ 8 cores, ≥ 32 GB RAM+swap,
≥ 350 GB free disk**. A full build takes several hours on 16 cores.
`scripts/rom/preflight.sh` refuses to start on smaller machines.

```bash
scripts/rom/setup-build-host.sh                 # once: packages, repo, git identity, ccache
export EXPERIENCE_TOP=/srv/experience-src        # Android source tree location

scripts/rom/preflight.sh                         # disk / RAM / CPU / tools
scripts/rom/sync.sh                              # LineageOS 18.1 + pinned phh repos + patch sets,
                                                 # installs device/ and vendor/experience
scripts/rom/collect-app-prebuilts.sh             # builds the apps, installs them as prebuilts
scripts/rom/apply-patches.sh                     # GSI patch stack + Experience patches (strict)
python3 scripts/rom/check-tree.py --upstream "$EXPERIENCE_TOP"
EXPERIENCE_BUILD_ID=$(date -u +%Y%m%d%H%M) scripts/rom/build.sh   # -> out/dist/system.img
python3 scripts/rom/verify-system-image.py out/dist/system.img --report out/dist/verification.json
scripts/rom/package-release.sh                   # -> out/release/*.img.xz, SHA256SUMS, build-info.json
```

Equivalent manual steps after `sync.sh` and `apply-patches.sh`:

```bash
cd "$EXPERIENCE_TOP" && source build/envsetup.sh
lunch lineage_experience_arm64_bvN-userdebug
m -j"$(nproc)" systemimage
```

What each input is pinned to: `rom/manifests/pins.env` (LineageOS branch, AndyCGYan
patch-set commits, lunch target) and `rom/manifests/experience.xml` (phh repositories by
commit). Each sync writes the exact revision of every project to
`$EXPERIENCE_TOP/experience-pinned-manifest.xml`, which is shipped in the build logs.

#### Patches

`apply-patches.sh` applies, in order: `patches_treble_prerequisite`, `patches_treble_phh`,
`patches_platform`, `patches_treble` (AndyCGYan's LineageOS 18.1 GSI sets, which carry
phh's Treble patches), then `rom/patches/experience`. It resets each project to its
synced revision first, never applies patches fuzzily, and stops at the first patch that
does not apply. Two documented deviations from upstream:

- `upstream-overrides/…/0001-Squashed-revert-of-LOS-FOD-implementation.patch` — refreshed:
  LineageOS added a security backport to `KeyguardUpdateMonitor.java` after the upstream
  patch set was last updated, so the original no longer applied.
- `upstream-exclude.txt` — phh's *app signature spoofing* patch is not applied: the
  Vanilla edition has no microG, and the patch weakens package-signature guarantees.

#### Privileged permissions

LineageOS enforces `ro.control_privapp_permissions`, so an incomplete allowlist stops
`system_server` at boot. `privapp-permissions-experience.xml` is **generated** from the
APKs by `scripts/rom/gen-privapp-permissions.py`; CI fails if it is out of date.

### 3. CI and releases

- `.github/workflows/experience-ui-apps.yml` (GitHub-hosted, every change): app tests,
  lint and release APKs; privapp allowlist check; ROM script tests; patch stack applied
  to real LineageOS 18.1 projects; tree/overlay consistency.
- `.github/workflows/experience-ui-rom.yml` (self-hosted runner labelled
  `experience-rom`, manual or `experience-v*` tag): apps → preflight → sync → prebuilts
  → patches → build → static image verification → packaging → artifacts. With
  *publish* (or a tag) it creates a **draft pre-release** whose notes state that the build
  is not boot-tested. Nothing is published as stable automatically.

## Installing (only on a device you can recover)

There is no verified device yet; read [docs/compatibility.md](docs/compatibility.md) first.
On an unlocked A/B device with dynamic partitions:

```bash
xz -d ExperienceUI-1.0.0-<build>-arm64_bvN-userdebug.img.xz
sha256sum -c ExperienceUI-1.0.0-<build>-arm64_bvN-userdebug.img.sha256
adb reboot bootloader
fastboot --disable-verity --disable-verification flash vbmeta vbmeta.img   # vbmeta from your stock firmware
fastboot reboot fastboot                       # fastbootd, for dynamic partitions
fastboot flash system ExperienceUI-1.0.0-<build>-arm64_bvN-userdebug.img
fastboot -w                                    # wipes userdata
fastboot reboot
```

## Identity and honesty rules

- `ro.build.fingerprint`, `ro.build.version.*` and the Android security patch level are
  exactly what the platform build produces; Experience UI adds `ro.experience.*` and the
  build display ID only. The LineageOS identity (`ro.lineage.*`) stays, because the
  build *is* LineageOS-based; it is never presented as an official LineageOS build.
- Device identity on hardware comes from the vendor partition; no certification or
  fingerprint spoofing is included.
- Releases are draft pre-releases until a boot test on hardware is recorded.

## Other projects in this repository

- **Experience Virtual OS** (`apps/experience-virtual-os/`, workflow
  `build-experience-virtual-os.yml`) — a rootless QEMU virtual-machine manager app. Unchanged.
- **Legacy GSI updater** (`app/`) — earlier standalone updater app. Unchanged; see `app/README.md`.

## Licence

Experience UI's own code and assets are Apache 2.0. LineageOS, AOSP and phh/TrebleDroid
components keep their own licences.
