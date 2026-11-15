# Shared helpers for the Experience UI ROM scripts. Source, do not execute.
set -euo pipefail

EXPERIENCE_REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
# shellcheck source=../../rom/manifests/pins.env
source "$EXPERIENCE_REPO/rom/manifests/pins.env"

: "${EXPERIENCE_TOP:=$HOME/experience-src}"     # Android source tree
: "${EXPERIENCE_OUT:=$EXPERIENCE_REPO/out}"     # dist/, logs/ for this repo

log()  { printf '\033[1;34m[experience]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31m[experience] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# Map a patch directory name (AndyCGYan convention) to a source tree path.
patch_project_path() {
    local p
    p="$(tr _ / <<<"$1" | sed -e 's;^platform/;;')"
    case "$p" in
        build) p=build/make ;;
        vendor/hardware/overlay) p=vendor/hardware_overlay ;;
        vendor/partner/gms) p=vendor/partner_gms ;;
        lineage-sdk) p=lineage-sdk ;;
    esac
    printf '%s\n' "$p"
}

# Fetch one commit of a repository into a directory (shallow, exact commit).
fetch_commit() {
    local url="$1" commit="$2" dir="$3"
    rm -rf "$dir"
    git init -q "$dir"
    git -C "$dir" remote add origin "$url"
    git -C "$dir" fetch -q --depth 1 origin "$commit"
    git -C "$dir" checkout -q --detach FETCH_HEAD
    [[ "$(git -C "$dir" rev-parse HEAD)" == "$commit" ]] || die "$url: expected $commit"
}
