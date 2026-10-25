#!/usr/bin/env bash
# Run a prepared probe instance under Xvfb with software GL and no audio device: no window, no sound, no desktop needed.
# Usage: headless.sh --check | headless.sh <instance> <mods> [java -D args...]
set -u
here=$(cd "$(dirname "$0")" && pwd)
data=${STS1_DATA:-$here/../../data}
jre=$(ls -d "$data"/cache/jre8-linux-x64/*/ 2>/dev/null | head -1); jre=${jre%/}
xrandr=$data/cache/xrandr-wsl/usr/bin
miss=
[ -n "$jre" ] && [ -x "$jre/bin/java" ] || miss+="  Linux JRE 8 in $data/cache/jre8-linux-x64: run tools/fetch-jre8.sh\n"
[ -x "$xrandr/xrandr" ] || miss+="  xrandr in $xrandr: run tools/fetch-xrandr.sh\n"
command -v xvfb-run >/dev/null || miss+="  xvfb-run: sudo apt-get install xvfb\n"
[ -z "$miss" ] || { printf 'headless: missing dependencies\n%b' "$miss" >&2; exit 3; }
[ "${1:-}" = --check ] && { echo "headless: ok ($jre)"; exit 0; }
[ $# -ge 2 ] || { echo 'usage: headless.sh <instance> <mods> [java args...]' >&2; exit 2; }
inst=$1 mods=$2; shift 2
cd "$inst" || exit 2
# MTS SteamSearch.findJRE tries ./jre/bin/java first and NPEs in Loader.main without it.
[ -e jre ] || ln -s "$jre" jre || cp -r "$jre" jre
# LWJGL2 shells out to xrandr for display modes.
export PATH=$xrandr:$PATH LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null
# WSLg forwards PulseAudio to the Windows speakers; drop it so nothing can play.
unset PULSE_SERVER
exec xvfb-run -a -s "-screen 0 1024x768x24" "$jre/bin/java" "$@" -jar ModTheSpire.jar --skip-launcher --skip-intro --mods "$mods"
