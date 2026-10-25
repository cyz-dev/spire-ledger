#!/usr/bin/env bash
# Fetch a checksum-verified Temurin 8 Linux JRE for tools/headless.sh (ModTheSpire relaunches unless it runs on Java 8).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
d=${STS1_DATA:-$here/../../data}/cache/jre8-linux-x64
if ls "$d"/*/bin/java >/dev/null 2>&1; then echo "already present: $d (remove it to refetch)"; exit 0; fi
w=$(mktemp -d); trap 'rm -rf "$w"' EXIT
curl -fsSL -o "$w/meta.json" "https://api.adoptium.net/v3/assets/latest/8/hotspot?architecture=x64&image_type=jre&os=linux&vendor=eclipse"
url=$(python3 -I -c 'import json,sys;print(json.load(open(sys.argv[1]))[0]["binary"]["package"]["link"])' "$w/meta.json")
sum=$(python3 -I -c 'import json,sys;print(json.load(open(sys.argv[1]))[0]["binary"]["package"]["checksum"])' "$w/meta.json")
curl -fsSL -o "$w/jre.tar.gz" "$url"
echo "$sum  $w/jre.tar.gz" | sha256sum -c
mkdir -p "$d"; tar xzf "$w/jre.tar.gz" -C "$d"
printf 'source: %s\nsha256: %s\npurpose: headless Spire Ledger native probes (WSL/Xvfb)\n' "$url" "$sum" > "$d/SOURCE.txt"
"$d"/*/bin/java -version
