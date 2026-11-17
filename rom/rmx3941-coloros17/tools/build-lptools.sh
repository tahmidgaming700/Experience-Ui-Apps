#!/usr/bin/env bash
# Build lpmake and lpunpack (AOSP logical-partition tools) from source.
#
# Ubuntu has no package for these, so they are compiled from the
# android14-release branch of AOSP with clang. Sources come from
# android.googlesource.com; nothing prebuilt is downloaded.
#
# Usage: build-lptools.sh <work-dir> <bin-dir>
# Needs: clang, libssl-dev, zlib1g-dev, libfmt-dev, curl, tar
set -euo pipefail

WORK=${1:?work dir}
BIN=${2:?bin dir}
BRANCH=android14-release
mkdir -p "$WORK" "$BIN"
WORK=$(cd "$WORK" && pwd)
BIN=$(cd "$BIN" && pwd)
cd "$WORK"

fetch() { # <project> <dest> <paths...>
  local proj=$1 dest=$2; shift 2
  [ -d "$dest" ] && return 0
  mkdir -p "$dest"
  curl -fsSL --retry 4 "https://android.googlesource.com/$proj/+archive/refs/heads/$BRANCH.tar.gz" \
    | tar xz -C "$dest" "$@"
}

fetch platform/system/core    core    fs_mgr/liblp fs_mgr/libstorage_literals libsparse libcutils/include
fetch platform/system/libbase libbase
fetch platform/system/logging logging liblog
fetch platform/system/extras  extras  partition_tools ext4_utils

INC="-Icore/fs_mgr/liblp/include -Icore/fs_mgr/liblp -Icore/libsparse/include -Ilibbase/include
     -Ilogging/liblog/include -Ilogging/liblog -Iextras/ext4_utils/include
     -Icore/fs_mgr/libstorage_literals -Icore/libcutils/include"
CXXFLAGS="-O2 -std=gnu++20 -D_FILE_OFFSET_BITS=64 -w $INC"

SRCS=(
  core/fs_mgr/liblp/{builder,images,partition_opener,property_fetcher,reader,utility,writer,super_layout_builder}.cpp
  libbase/{file,logging,parsebool,properties,stringprintf,strings,threads,posix_strerror_r,mapped_file,errors_unix}.cpp
  core/libsparse/{backed_block,output_file,sparse,sparse_crc32,sparse_err,sparse_read}.cpp
  extras/ext4_utils/{ext4_utils,ext4_sb}.cpp
  logging/liblog/{logger_write,logger_name,properties,log_time,logprint}.cpp
)

mkdir -p obj
OBJS=()
for f in "${SRCS[@]}"; do
  o=obj/$(echo "$f" | tr / _).o
  [ -f "$o" ] || clang++ $CXXFLAGS -c "$f" -o "$o"
  OBJS+=("$o")
done
for tool in lpmake lpunpack; do
  clang++ $CXXFLAGS "extras/partition_tools/$tool.cc" "${OBJS[@]}" -o "$BIN/$tool" -lcrypto -lz -lfmt
done
"$BIN/lpmake" 2>&1 | head -1
