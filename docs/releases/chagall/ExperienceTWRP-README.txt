Experience TWRP for Samsung Galaxy Tab S 10.5 (chagalllte / SM-T805)
===================================================================
Files:
  ExperienceTWRP-chagalllte-3.7.0_9.img   recovery image (7.5 MB) - flash via TWRP or Heimdall
  ExperienceTWRP-chagalllte.tar.md5        same image, for Samsung Odin (Windows)
  ExperienceTWRP-chagalllte.sha256         checksums

What it is
----------
Official TWRP 3.7.0_9-0 for chagalllte, reskinned: EMUI-style dark palette (dark
surfaces, indigo-blue accent) and the Experience logo on the splash/boot screen.
Nothing else is changed - same TWRP 3.7.0, same kernel, same features. The layout
is the stock TWRP layout (only colours and logo images were changed), which keeps
the recovery reliable.

Honest notes
------------
- NOT built from source and NOT a full EMUI redesign of the TWRP interface - that
  would mean rewriting TWRP's whole GUI blind, which risks an unusable recovery. This
  is a safe recolour + logo swap of real TWRP.
- NOT boot-tested by me. The repack was verified byte-for-byte (identical kernel,
  identical ramdisk file tree, permissions and symlinks; only theme files differ),
  but I cannot boot it. If it ever misbehaves, just flash official TWRP again.
- It fits your 8 MB limit (7.5 MB) because this TWRP build is unusually small.
- Base: official twrp-3.7.0_9-0-chagalllte.img
  (sha256 be3cf1fa236f3e00067a70b8852c13a3bdde356cbe83d18f9a09f0ffe34b1538).

How to flash (pick one)
-----------------------
A) From your current TWRP (easiest):
     Install > Install Image (bottom-right) > pick ExperienceTWRP-chagalllte-3.7.0_9.img
     > choose "Recovery" > swipe. Reboot to recovery to see it.
B) Heimdall (Linux/Mac), device in Download mode:
     heimdall flash --RECOVERY ExperienceTWRP-chagalllte-3.7.0_9.img --no-reboot
C) Odin (Windows), device in Download mode:
     Put ExperienceTWRP-chagalllte.tar.md5 in the "AP" slot, leave Auto-Reboot OFF,
     Start. Then manually boot straight to recovery (hold Power+Home+Vol-Up) so stock
     firmware doesn't restore its own recovery.

Verify first:  sha256sum -c ExperienceTWRP-chagalllte.sha256
