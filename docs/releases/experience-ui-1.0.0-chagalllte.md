Experience UI 1.0 for the **Samsung Galaxy Tab S 10.5 (chagalllte / SM-T805)** — a device-specific build (not a GSI), plus a matching reskinned recovery.

> ⚠️ **Pre-release — not boot-tested.** Flash only with a backup. Writes system + boot (not bootloader/modem); if it misbehaves, reflash your LineageOS zip / official TWRP.

### Assets
- **`ExperienceUI-1.0.0-<build>-chagalllte.zip`** — Experience UI added to the unofficial LineageOS 18.1 build `lineage-18.1-20221230-UNOFFICIAL-chagalllte`. Android 11, 32-bit ARM, **Vanilla** (no Google apps). Keeps the Lineage kernel, SM-T805 identity and 2022-12-05 patch level; signed with the build's AOSP test key using the proper recovery whole-file signature, so **LineageOS recovery and TWRP both accept it**. Flash: reboot to recovery → (clean: factory reset) → `adb sideload <zip>`.
- **`ExperienceTWRP-chagalllte-3.7.0_9.img`** / **`.tar.md5`** — official TWRP 3.7.0_9-0 reskinned with an EMUI-style dark palette and the Experience logo (layout unchanged; 7.5 MB). `.img` for TWRP "Install Image"/Heimdall, `.tar.md5` for Odin.

Added apps: the 14 Experience apps; overlays (Material 3 Expressive look, 9 accents); boot animation, wallpapers, sounds. Replaced: Lineage Setup Wizard, Dialer, Contacts, Clock, Calendar, Calculator, Gallery, Camera. Kept: Lineage Messaging (MMS), Jelly, Eleven, Recorder, recents. See each README asset for full instructions and checksums.

---
🤖 Generated with [Claude Code](https://claude.com/claude-code)

https://claude.ai/code/session_01686Rfxjgen1Dx6B3SmgmM5
