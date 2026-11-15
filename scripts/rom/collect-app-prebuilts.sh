#!/usr/bin/env bash
#
# Build the Experience applications (release, unsigned) and install them as
# prebuilts into vendor/experience/prebuilt/apps of the source tree.
#
#   collect-app-prebuilts.sh [destination]   (default: $EXPERIENCE_TOP/vendor/experience/prebuilt/apps)
#
# Set EXPERIENCE_SKIP_GRADLE=1 to only collect already-built APKs (CI builds them in
# a separate job and downloads the artifact into apps/experience-suite/*/build).
#
source "$(dirname "$0")/common.sh"

DEST="${1:-$EXPERIENCE_TOP/vendor/experience/prebuilt/apps}"
SUITE="$EXPERIENCE_REPO/apps/experience-suite"

# Gradle module -> Soong module (must match rom/vendor/experience/prebuilt/apps/Android.bp)
declare -A MODULES=(
    [launcher]=ExperienceLauncher [settings]=ExperienceSettings [setupwizard]=ExperienceSetupWizard
    [dialer]=ExperienceDialer [messages]=ExperienceMessages [contacts]=ExperienceContacts
    [notes]=ExperienceNotes [clock]=ExperienceClock [calendar]=ExperienceCalendar
    [calculator]=ExperienceCalculator [files]=ExperienceFiles [gallery]=ExperienceGallery
    [camera]=ExperienceCamera [converter]=ExperienceConverter
)

if [[ "${EXPERIENCE_SKIP_GRADLE:-0}" != 1 ]]; then
    log "gradle assembleRelease"
    ( cd "$SUITE" && ./gradlew --no-daemon --console=plain assembleRelease )
fi

mkdir -p "$DEST"
missing=0
for m in "${!MODULES[@]}"; do
    soong="${MODULES[$m]}"
    apk="$SUITE/$m/build/outputs/apk/release/$m-release-unsigned.apk"
    [[ -f "$apk" ]] || apk="$SUITE/$m/build/outputs/apk/release/$m-release.apk"
    if [[ ! -f "$apk" ]]; then
        log "MISSING release APK for :$m"; missing=$((missing + 1)); continue
    fi
    mkdir -p "$DEST/$soong"
    cp "$apk" "$DEST/$soong/$soong.apk"
    log "$soong <- $(basename "$apk") ($(du -h "$apk" | cut -f1))"
done
[[ $missing -eq 0 ]] || die "$missing Experience app(s) have no release APK; refusing to continue"
