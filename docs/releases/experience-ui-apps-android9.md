The 14 Experience apps, built for **Android 9 (API 28)** and newer — `minSdk 28`, `targetSdk 30`.

> These apps are designed to run as **system apps inside Experience UI (Android 11)**. On a
> plain Android 9 phone they install and open, but anything that needs Android 10/11 APIs or
> Experience UI's system integration is disabled or limited. They are **not boot/▪run-tested on
> Android 9** — your device is the first. Signed with a throwaway test key (not a store key), so
> to install a newer build you must uninstall the old one first.

### How well each works on a plain Android 9 phone

| App | On stock Android 9 |
|---|---|
| Calculator, Converter, Notes, Clock | **Full** — no system dependencies |
| Calendar, Contacts | **Works** via the system Calendar/Contacts providers |
| Camera | **Works** (CameraX); thumbnails use a pre-Android-10 path |
| Gallery | **Mostly** — viewing works with storage permission; the Android-11 delete/favourite dialogs are skipped |
| Launcher | **Works** as a home app (set it in Settings → Home) |
| Dialer, Messages | **Limited** — calls/SMS work after granting permissions, but *becoming the default phone/SMS app* uses the Android 10+ RoleManager, so that part is skipped on Android 9 |
| Files | **Limited** — its all-files access uses the Android 11 `MANAGE_EXTERNAL_STORAGE` flow, which doesn't exist on Android 9 |
| Settings | **Limited** — pages open, but accent overlays, secure-settings toggles and default-app roles need Experience UI / system permissions |
| Setup Wizard | Use the **"Setup preview"** icon (read-only walkthrough); the real first-boot flow needs system permissions |

### Install
On the phone: open each APK and allow "Install unknown apps". Or with a computer:
`adb install -r ExperienceCalculator.apk` (one app), or `for f in *.apk; do adb install -r "$f"; done` (all).
Android may warn that an app was built for an older version — that's just a notice (targetSdk 30).

Reproducible with `./gradlew -Pexperience.minSdk=28 assembleRelease` in `apps/experience-suite`.
