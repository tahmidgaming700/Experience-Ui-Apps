#!/usr/bin/env bash
#
# Prepare an Ubuntu 20.04/22.04 x86_64 machine (e.g. a self-hosted GitHub runner)
# to build Experience UI. Run once with sudo rights.
#
# Hardware needed for LineageOS 18.1 (Android 11): >= 8 cores, >= 32 GB RAM (or
# 16 GB RAM + 16 GB swap at reduced -j), >= 350 GB free disk on the build volume.
#
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update
sudo apt-get install -y --no-install-recommends \
    bc bison build-essential ccache curl flex g++-multilib gcc-multilib git git-lfs gnupg gperf \
    imagemagick lib32readline-dev lib32z1-dev libelf-dev liblz4-tool libncurses5 libncurses5-dev \
    libsdl1.2-dev libssl-dev libxml2 libxml2-utils lzop pngcrush rsync schedtool squashfs-tools \
    xsltproc zip unzip zlib1g-dev python3 python-is-python3 python3-pil python3-numpy \
    e2fsprogs xz-utils ffmpeg fonts-inter openjdk-17-jdk-headless
if ! command -v repo >/dev/null; then
    mkdir -p "$HOME/.local/bin"
    curl -fsSL https://storage.googleapis.com/git-repo-downloads/repo -o "$HOME/.local/bin/repo"
    chmod +x "$HOME/.local/bin/repo"
    echo "repo installed to ~/.local/bin (add it to PATH)"
fi
git config --global user.name >/dev/null || git config --global user.name "Experience Build"
git config --global user.email >/dev/null || git config --global user.email "build@experience.invalid"
ccache -M 50G || true
echo "Build host ready."
