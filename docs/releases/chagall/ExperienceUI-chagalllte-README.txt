Experience UI 1.0 for Samsung Galaxy Tab S 10.5 (chagalllte / SM-T805)
=====================================================================
File: ExperienceUI-1.0.0-202611230505-chagalllte.zip
Build: EXP1.0.0.202611230505.userdebug   Edition: Vanilla (no Google apps)
Android 11 (SDK 30), 32-bit ARM. NOT boot-tested by me - your tablet is the first.

What this is
------------
Your own unofficial LineageOS 18.1 build for chagalllte (lineage-18.1-20221230),
with Experience UI added: the 14 Experience apps, Experience overlays (rounded
Material 3 Expressive look, 9 accent colours), boot animation, wallpapers and
sounds. It is signed with the SAME AOSP test key as that LineageOS build, using the
proper recovery whole-file signature, so LineageOS recovery and TWRP both accept it.
The device identity stays SM-T805; the security patch level (2022-12-05) and the
LineageOS version are unchanged.

Replaced by Experience apps: LineageOS Setup Wizard, Dialer, Contacts, Clock,
Calendar, Calculator, Gallery, Camera. Kept: LineageOS Messaging (it has MMS;
Experience Messages is SMS-only and is also installed), Jelly browser, Eleven music,
Recorder, and Trebuchet (recent-apps/gestures). Experience Launcher is added - pick it
as Home in the Setup Wizard or in Settings > Apps > Default apps.

Install (bootloader already unlocked; you flashed LineageOS, so you have a recovery)
-----------------------------------------------------------------------------------
This is a clean flash of system+boot. BACK UP first.
  1. Verify the download:   sha256sum -c ExperienceUI-1.0.0-202611230505-chagalllte.zip.sha256
  2. Reboot to recovery (LineageOS recovery or TWRP).
  3. Recommended clean install:
       - Wipe > Factory reset (data). (Dirty-flashing over stock Lineage may work but
         clean is safest for a first try.)
  4. Install / Apply update > from this zip (adb sideload or from internal/SD):
       adb sideload ExperienceUI-1.0.0-202611230505-chagalllte.zip
  5. Reboot. First boot takes a few minutes; the Experience Setup Wizard runs.

Notes / honesty
---------------
- Not boot-tested. If it bootloops, flash your LineageOS zip again the same way - this
  does not touch the Samsung bootloader/modem, only system and boot.
- It writes the same system partition and keeps the Lineage kernel (boot.img from your
  build), so hardware support is exactly your LineageOS build's.
- Report back: does it boot, does the Setup Wizard appear, any app that crashes
  (adb logcat). That's what turns this from "should work" into "verified".
