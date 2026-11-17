#!/usr/bin/env bash
# Flash the ColorOS 17 port to a realme C75 (RMX3941) with an UNLOCKED bootloader.
#
# Start with the phone in bootloader fastboot mode (adb reboot bootloader).
# This ERASES ALL DATA on the phone and writes to slot A.
#
# Before you start:
#   - Be on stock realme UI 14.1.0.240(EX01) or newer, and keep that stock
#     package: it is your way back if the port does not boot.
#   - Use the platform-tools fastboot from Google (34 or newer), not a
#     vendor or distro copy that lacks `wipe-super`.
set -euo pipefail
cd "$(dirname "$0")"

FASTBOOT=${FASTBOOT:-fastboot}
read -r -a FIRMWARE < firmware_partitions.txt
read -r -a LOGICAL < logical_partitions.txt

echo "Checking images against SHA256SUMS..."
sha256sum -c --quiet SHA256SUMS

product=$($FASTBOOT getvar product 2>&1 | awk -F': ' '/^product:/ {print $2}')
unlocked=$($FASTBOOT getvar unlocked 2>&1 | awk -F': ' '/^unlocked:/ {print $2}')
echo "fastboot reports product='$product' unlocked='$unlocked'"
if [ "$unlocked" != "yes" ]; then
  echo "The bootloader is not unlocked. Stop." >&2
  exit 1
fi
case "$product" in
  *RMX3941*|*RE607CL1*|*k69v1_64*) ;;
  *) read -r -p "Product '$product' is not a known RMX3941 name. Type RMX3941 to continue anyway: " a
     [ "$a" = RMX3941 ] || exit 1 ;;
esac

read -r -p "This wipes the phone and flashes an untested port. Type YES to continue: " a
[ "$a" = YES ] || exit 1

$FASTBOOT --set-active=a
for p in "${FIRMWARE[@]}"; do
  case "$p" in
    vbmeta) $FASTBOOT --disable-verity --disable-verification flash vbmeta_a vbmeta.img ;;
    *)      $FASTBOOT flash "${p}_a" "$p.img" ;;
  esac
done

echo "Rebooting to fastbootd (userspace fastboot) for the logical partitions..."
$FASTBOOT reboot fastboot
$FASTBOOT snapshot-update cancel || true
$FASTBOOT wipe-super super_empty.img
for p in "${LOGICAL[@]}"; do
  $FASTBOOT flash "$p" "$p.img"
done

$FASTBOOT -w
$FASTBOOT reboot
echo "Done. The first boot can take 5-10 minutes."
