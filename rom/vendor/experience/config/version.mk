#
# Experience UI release identity.
#
# EXPERIENCE_BUILD_ID may be provided by the caller (CI passes the UTC build
# timestamp, e.g. 202611151530) so that every build number is unique and the
# build is reproducible from its metadata. Local builds fall back to "now".
#
EXPERIENCE_VERSION_MAJOR := 1
EXPERIENCE_VERSION_MINOR := 0
EXPERIENCE_VERSION_PATCH := 0
EXPERIENCE_VERSION := $(EXPERIENCE_VERSION_MAJOR).$(EXPERIENCE_VERSION_MINOR).$(EXPERIENCE_VERSION_PATCH)
EXPERIENCE_DISPLAY_VERSION := Experience UI $(EXPERIENCE_VERSION_MAJOR).$(EXPERIENCE_VERSION_MINOR)

EXPERIENCE_EDITION ?= Vanilla
EXPERIENCE_RELEASE_CHANNEL ?= development

ifeq ($(strip $(EXPERIENCE_BUILD_ID)),)
EXPERIENCE_BUILD_ID := $(shell date -u +%Y%m%d%H%M)
endif

# e.g. EXP1.0.0.202611151530.userdebug — sorts lexically by version, then time.
EXPERIENCE_BUILD_NUMBER := EXP$(EXPERIENCE_VERSION).$(EXPERIENCE_BUILD_ID).$(TARGET_BUILD_VARIANT)
