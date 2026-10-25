#!/usr/bin/env bash
# Unpack xrandr without sudo for tools/headless.sh: LWJGL2 shells out to it for display modes under Xvfb.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
d=${STS1_DATA:-$here/../../data}/cache/xrandr-wsl
if [ -x "$d/usr/bin/xrandr" ]; then echo "already present: $d (remove it to refetch)"; exit 0; fi
w=$(mktemp -d); trap 'rm -rf "$w"' EXIT
(cd "$w" && apt-get download x11-xserver-utils >/dev/null)
deb=$(ls "$w"/x11-xserver-utils_*.deb)
sum=$(apt-cache show --no-all-versions x11-xserver-utils | awk '/^SHA256:/&&!s{s=$2} END{print s}')
echo "$sum  $deb" | sha256sum -c
mkdir -p "$d"; dpkg -x "$deb" "$d"
printf 'source: apt-get download x11-xserver-utils (%s)\nsha256: %s\npurpose: xrandr for headless LWJGL2 probes\n' "$(basename "$deb")" "$sum" > "$d/SOURCE.txt"
"$d/usr/bin/xrandr" --version
! ldd "$d/usr/bin/xrandr" | grep "not found"
