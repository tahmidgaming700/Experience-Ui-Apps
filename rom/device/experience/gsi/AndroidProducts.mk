#
# Experience UI generic system images.
#
# The product name keeps the "lineage_" prefix on purpose: LineageOS 18.1's
# envsetup/check_product only enables the Lineage build configuration
# (BoardConfigLineage, sepolicy, version properties) for lineage_* products.
#
PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/lineage_experience_arm64_bvN.mk

COMMON_LUNCH_CHOICES := \
    lineage_experience_arm64_bvN-userdebug
