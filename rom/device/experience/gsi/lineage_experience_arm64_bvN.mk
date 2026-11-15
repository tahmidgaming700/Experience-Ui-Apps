#
# Experience UI 1.0 — ARM64, A/B, system-as-root generic system image (GSI).
# Edition: Vanilla (no Google apps), no su.
#
# This mirrors what device/phh/treble/generate.sh emits for "treble_arm64_bvN"
# (after AndyCGYan's "treble: Lineage-ify" patch) and layers Experience UI on top.
#

# Experience static overlays must take precedence over the phh and LineageOS
# overlays, which also set e.g. config_dialogCornerRadius. A product's own
# PRODUCT_PACKAGE_OVERLAYS entries come before every inherited entry, and the
# first overlay directory wins, so they are declared here and not in an
# inherited makefile.
PRODUCT_PACKAGE_OVERLAYS += vendor/experience/overlay/static
PRODUCT_ENFORCE_RRO_EXCLUDED_OVERLAYS += vendor/experience/overlay/static

# Experience default sounds. Duplicate product properties keep the first
# definition (uniq-pairs-by-first-component), so these must precede the
# LineageOS/AOSP defaults that the inherited makefiles add.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.config.ringtone=Experience_Horizon.ogg \
    ro.config.notification_sound=Experience_Bloom.ogg \
    ro.config.alarm_alert=Experience_Daybreak.ogg

TARGET_GAPPS_ARCH := arm64
$(call inherit-product, device/phh/treble/base-pre.mk)
include build/make/target/product/aosp_arm64_ab.mk
$(call inherit-product, device/phh/treble/base.mk)
$(call inherit-product, device/phh/treble/lineage.mk)

# Experience UI packages, identity properties and assets.
$(call inherit-product, vendor/experience/config/experience.mk)

PRODUCT_NAME := lineage_experience_arm64_bvN
PRODUCT_DEVICE := phhgsi_arm64_ab

# System-partition identity. On real hardware Android 11 resolves
# ro.product.{brand,model,...} from product/odm/vendor before system
# (ro.product.property_source_order), so the device keeps reporting its own
# vendor identity; Experience UI never spoofs device certification.
PRODUCT_BRAND := Experience
PRODUCT_SYSTEM_BRAND := Experience
PRODUCT_MANUFACTURER := Experience
PRODUCT_SYSTEM_MANUFACTURER := Experience
PRODUCT_MODEL := Experience Device
PRODUCT_SYSTEM_MODEL := Experience Device
PRODUCT_SYSTEM_NAME := experience_arm64_bvN
PRODUCT_SYSTEM_DEVICE := experience_gsi_arm64

# Overwrite the inherited "emulator" characteristics.
PRODUCT_CHARACTERISTICS := device
