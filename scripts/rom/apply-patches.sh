#!/usr/bin/env bash
#
# Apply the GSI patch stack and the Experience UI patches to a synced source tree.
#
#   apply-patches.sh [--only <project-path>...]
#
# Order (same as AndyCGYan's buildbot_unified.sh "treble" mode, then Experience):
#   1. patches_treble_prerequisite   2. patches_treble_phh   3. patches_platform
#   4. patches_treble                5. rom/patches/experience
#
# rom/patches/upstream-overrides/<group>/<project>/<file> replaces the upstream patch
# of the same name (refreshed for current LineageOS heads); rom/patches/upstream-exclude.txt
# lists upstream patches that are skipped on purpose.
#
# Unlike the upstream helper this never falls back to fuzzy `patch -f`: a patch that
# does not apply exactly stops the build, so nothing is silently half-applied.
#
source "$(dirname "$0")/common.sh"

ONLY=()
if [[ "${1:-}" == "--only" ]]; then shift; ONLY=("$@"); fi

LPU="$EXPERIENCE_TOP/lineage_patches_unified"
[[ -d "$LPU" ]] || die "missing $LPU (run sync.sh first)"
EXCLUDES="$(grep -v '^\s*#' "$EXPERIENCE_REPO/rom/patches/upstream-exclude.txt" | sed '/^\s*$/d' || true)"

declare -A RESET_DONE=()
applied=0

wanted() {
    [[ ${#ONLY[@]} -eq 0 ]] && return 0
    local o; for o in "${ONLY[@]}"; do [[ "$o" == "$1" ]] && return 0; done; return 1
}

apply_group() {  # <group label> <directory containing per-project patch dirs> [override group]
    local label="$1" root="$2" override_group="${3:-}"
    [[ -d "$root" ]] || die "missing patch group $root"
    local project
    for project in $(cd "$root" && ls -d */ 2>/dev/null | sed 's;/$;;' | sort); do
        local path; path="$(patch_project_path "$project")"
        wanted "$path" || continue
        local dir="$EXPERIENCE_TOP/$path"
        [[ -d "$dir/.git" || -f "$dir/.git" ]] || die "$label: project $path is not a git checkout"
        if [[ -z "${RESET_DONE[$path]:-}" ]]; then
            # Always start from the unpatched revision so reruns are idempotent:
            # the experience-base tag (check-patches.sh) or repo's manifest ref m/<branch>.
            local base
            base="$(git -C "$dir" rev-parse -q --verify 'refs/tags/experience-base^{commit}' \
                 || git -C "$dir" rev-parse -q --verify "refs/remotes/m/$LINEAGE_BRANCH^{commit}" \
                 || git -C "$dir" rev-parse -q --verify 'refs/remotes/m/android-11.0^{commit}' \
                 || true)"
            [[ -n "$base" ]] || die "$path: cannot find its unpatched revision (no m/$LINEAGE_BRANCH ref)"
            git -C "$dir" am --abort >/dev/null 2>&1 || true
            git -C "$dir" checkout -q --detach "$base"
            git -C "$dir" reset -q --hard
            git -C "$dir" clean -q -fdx
            RESET_DONE[$path]=1
        fi
        local patch
        for patch in "$root/$project"/*.patch; do
            [[ -e "$patch" ]] || continue
            local name rel; name="$(basename "$patch")"; rel="$label/$project/$name"
            if grep -qxF "$rel" <<<"$EXCLUDES"; then
                log "skip   $rel (rom/patches/upstream-exclude.txt)"; continue
            fi
            if [[ -n "$override_group" && -f "$EXPERIENCE_REPO/rom/patches/upstream-overrides/$override_group/$project/$name" ]]; then
                patch="$EXPERIENCE_REPO/rom/patches/upstream-overrides/$override_group/$project/$name"
                rel="$rel (refreshed)"
            fi
            if ! git -C "$dir" -c user.name="Experience Build" -c user.email="build@experience.invalid" \
                    am -q --committer-date-is-author-date "$patch"; then
                git -C "$dir" am --abort || true
                die "patch does not apply: $rel -> $path"
            fi
            applied=$((applied + 1))
            log "apply  $rel"
        done
    done
}

apply_group patches_treble_prerequisite "$LPU/patches_treble_prerequisite" patches_treble_prerequisite
apply_group patches_treble_phh          "$LPU/patches_treble_phh"          patches_treble_phh
apply_group patches_platform            "$LPU/patches_platform"            patches_platform
apply_group patches_treble              "$LPU/patches_treble"              patches_treble
apply_group experience                  "$EXPERIENCE_REPO/rom/patches/experience"

if [[ ${#ONLY[@]} -eq 0 ]]; then
    # Same finalisation as buildbot_unified.sh finalize_treble.
    rm -f "$EXPERIENCE_TOP"/device/*/sepolicy/common/private/genfs_contexts
    ( cd "$EXPERIENCE_TOP/device/phh/treble" && git clean -q -fdx && bash generate.sh lineage )
fi
log "applied $applied patches"
