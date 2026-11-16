# Build status — Experience UI 1.0

Last updated with the commit that added this file. "Verified" means the check was run
and passed; anything else says what is missing.

| Stage | Status | How it was checked |
|---|---|---|
| Repository audit | Done | Existing `app/` (legacy updater) and `apps/experience-virtual-os/` kept unchanged; sibling repos' app stubs inspected and reimplemented |
| Source base selection | Done | LineageOS 18.1 + phh/TrebleDroid `android-11.0` + AndyCGYan lineage-18.1 patch sets, pinned (`rom/manifests/`) |
| Experience apps (14) — compile | **Verified** | `./gradlew assembleRelease` (14 release APKs, 53 MB total) |
| Experience apps — unit tests | **Verified** | 220 tests, 0 failures (`testDebugUnitTest`) |
| Experience apps — lint | **Verified** | `lintDebug`, 0 errors (remaining warnings: icon folder `-v26`, no monochrome icon layer) |
| GSI + Experience patch stack | **Verified** on frameworks/base, Settings, Trebuchet | `scripts/rom/check-patches.sh` — 49 patches apply to current lineage-18.1 heads |
| Overlay resources exist upstream | **Verified** | `check-tree.py --upstream` — 29 resources |
| ROM tree consistency | **Verified** | `check-tree.py` (XML, versions, module lists, copy-file sources) |
| Privileged permission allowlist | **Generated** from the release APKs | `gen-privapp-permissions.py` (CI re-checks it) |
| Image verifier | **Verified** on synthetic images | `tests/rom` (raw/sparse ext4, good and broken) |
| Boot animation, wallpapers, sounds | Generated | `scripts/rom/generate-assets.py` |
| Patch stack on the *full* tree (all ~20 patched projects) | **Not run** | needs a full sync; runs in `experience-ui-rom.yml` |
| Full ROM compilation (`m systemimage`) | **Not run** | needs ≥ 350 GB disk / 32 GB RAM; the development container had 31 GB / 15 GB. Run on a self-hosted runner. |
| Static validation of a real `system.img` | **Not run** | no image yet |
| Emulator boot | Not applicable | a device GSI does not boot on the Android Emulator (see compatibility.md) |
| Hardware boot | **Not run** | no verified device |
| Image from prebuilt base (`scripts/gsi/repack.sh`) | **Built and statically verified** | base `lineage-18.1-20240121-UNOFFICIAL-arm64_bgS.img.xz` (SHA-256 `7bb7c4eb…415a`) → 3.1 GiB system.img, 61/61 image checks; audit: 14 platform-signed apps, labels/modes match neighbours, privapp allowlist covers every privileged permission requested (checked against the image's own framework-res) |
| Release | **None published** | releases are draft pre-releases created only by the ROM workflow |

## Expected first-build risks

Things that cannot be checked without the full tree and are the most likely to need a
fix on the first real build:

1. Patches for projects not covered by `check-patches.sh` (bionic, system/core, vold,
   av, telephony, …) may have drifted like the FOD revert did. `apply-patches.sh`
   stops at the first failure and names the patch; refresh it into
   `rom/patches/upstream-overrides/`.
2. `android_app_import` of R8-minified Compose APKs is expected to work (no native
   libraries), but dexpreopt problems would show up only in the build.
3. Privileged permissions defined outside frameworks/base and lineage-sdk would be
   caught by the workflow's allowlist check against the synced tree.
