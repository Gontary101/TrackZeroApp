#!/usr/bin/env bash
# TrackZero: screenshots of the v2 screens the smoke test does not reach (Phase 36: one run per appearance), Pixel 7
# emulator (1080 x 2400), debug build, Puy-de-Dôme map, "Aubière - Clermont-Ferrand (6.6km)" imported (CARD_Y: its
# card in B3). Saves <ID>.png into <out dir>: N1 L1 L2 P1 P2 N2 M1 R6 S1 S2 E1 X1 X2. Ends the ride it starts.
#
#   tools/trackzero/screens_v2.sh <out dir> [route.gpx]
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?out dir}
gpx=${2:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=${PKG:-app.trackzero.debug}
card_y=${CARD_Y:-1064}
handle_y=${HANDLE_Y:-2229}
end_y=${END_Y:-2151}
mkdir -p "$out"
shot() { adb exec-out screencap -p > "$out/$1.png"; echo "  shot $1"; }
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
back() { adb shell input keyevent KEYCODE_BACK; sleep "${1:-2}"; }
# The bounds "[l,t][r,b]" of the first view whose $1 attribute matches $2 (case-insensitive), from uiautomator;
# $3 tries (default 5: uiautomator gives up while something animates).
bounds() {
  local b=""
  for _ in $(seq 1 "${3:-5}"); do
    adb exec-out uiautomator dump /sdcard/tz_ui.xml > /dev/null 2>&1 || true
    b=$(adb shell cat /sdcard/tz_ui.xml 2> /dev/null | grep -io "$1=\"$2\"[^>]*bounds=\"[^\"]*\"" \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 || true)
    [ -n "$b" ] && break
    sleep 2
  done
  echo "$b"
}
# Taps the middle of the view whose text is $1 (allCaps styles: matched case-insensitively).
tap_text() {
  local b x y
  b=$(bounds text "$1")
  [ -z "$b" ] && { echo "  (no \"$1\" on screen)"; return 1; }
  read -r x y < <(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' <<< "$b" \
    | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}')
  tap "$x" "$y" "${2:-2}"
}
feeder=
trap '[ -n "$feeder" ] && kill $feeder 2> /dev/null' EXIT

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
adb emu geo fix "$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")" "$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")" 400 > /dev/null
adb shell input keyevent KEYCODE_WAKEUP
# Each part starts from a cold B1: Back presses out of the planner and sheets do not land in the same place.
restart() { adb shell am force-stop "$pkg"; adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 12; }
restart

echo "new route, loop, planner"
tap 338 2200
tap_text "New" 2; shot N1
tap_text "Propose a loop" 3; shot L1
tap_text "Propose loops" 2
# About 45 s on the emulator (D54); uiautomator cannot read the screen while the button counts.
sleep 60; shot L2
restart; tap 338 2200
tap_text "New" 2; tap_text "Plan on the map" 3; shot P1
tap 540 900 8; tap 300 1300 8; shot P2
echo "profile, sounds"
restart; tap 338 2200; tap 540 "$card_y" 4
tap_text "Road" 2; shot N2
restart; tap 930 2200; tap_text "Sounds" 2; shot M1
echo "ride: controls, stops, share, saver"
restart; tap 338 2200; tap 540 "$card_y" 3
python3 "$here/emu_gps_play.py" "$gpx" 25 > /dev/null 2>&1 &
feeder=$!
tap 540 2160 8
adb shell input swipe 540 "$handle_y" 540 900 400; sleep 2; shot R6
tap_text "Stops" 8; shot S1
b=$(bounds resource-id "$pkg:id/tz_stops_rows")
if [ -n "$b" ]; then
  read -r x y < <(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' <<< "$b" | awk '{print int(($1 + $3) / 2), $2 + 100}')
  tap "$x" "$y" 5; shot S2; back
fi
back
adb shell input swipe 540 "$handle_y" 540 900 400; sleep 2
tap_text "Share" 4; shot E1; back 3
adb shell input swipe 540 "$handle_y" 540 900 400; sleep 2
tap_text "Saver" 3; shot X1
tap_text "Turn on" 4; shot X2
tap_text "Exit saver" 4
kill "$feeder" 2> /dev/null; feeder=
adb shell input tap 540 "$handle_y"; sleep 2
adb shell input swipe 540 "$end_y" 540 "$end_y" 2600; sleep 4
tap 798 2214
echo "crashes:"
adb logcat -b crash -d | grep -v "^---------" || echo "  none"
