#!/usr/bin/env bash
# TrackZero: the battery saver against a normal ride (Phase 35, TZ_BASELINE.md), Pixel 7 emulator, build installed
# with -PtzMeasure, "Aubière - Clermont-Ferrand (6.6km)" imported (CARD_Y: its card in B3).
#
#   tools/trackzero/saver_measure.sh [route.gpx] > report.md
#
# One route ride driven at 25 km/h by emu_gps_play.py: R2 and screen off (2 min each), then the saver turned on
# (R6 > SAVER > TURN ON): X2 and screen off (2 min each). Location requests are read in each window.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
gpx=${1:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=app.trackzero.debug
win=120
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
# Taps the view whose text is $1 (uiautomator), for buttons whose place depends on the sheet's height.
tap_text() {
  local b=""
  # uiautomator gives up while something animates (a sheet opening): a few tries.
  for _ in 1 2 3 4 5; do
    adb exec-out uiautomator dump /sdcard/tz_ui.xml > /dev/null 2>&1 || true
    b=$(adb shell cat /sdcard/tz_ui.xml 2> /dev/null | grep -io "text=\"$1\"[^>]*bounds=\"[^\"]*\"" \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 || true)
    [ -n "$b" ] && break
    sleep 2
  done
  local x y
  x=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \3/' <<< "$b" | awk '{print int(($1 + $2) / 2)}')
  y=$(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\2 \4/' <<< "$b" | awk '{print int(($1 + $2) / 2)}')
  tap "$x" "$y" "${2:-2}"
}

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
lat=$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")
lon=$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")
adb emu geo fix "$lon" "$lat" 400 > /dev/null
adb shell am force-stop "$pkg"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null
sleep 15
echo "# Saver run $(date '+%F %T'), $(adb shell getprop ro.product.model | tr -d '\r'), $pkg"
echo
python3 "$here/emu_gps_play.py" "$gpx" 25 > /dev/null 2>&1 &
feeder=$!
trap 'kill $feeder 2> /dev/null || true' EXIT
tap 338 2200
tap 540 "${CARD_Y:-1064}" 3
tap 540 2160 5
"$here/measure.sh" "Normal: R2 route ride (map page)" $win "$pkg"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
"$here/measure.sh" "Normal: screen off" $win "$pkg"
adb shell input keyevent KEYCODE_WAKEUP; sleep 3
adb shell input tap 540 "${HANDLE_Y:-2229}"; sleep 2
tap_text "Saver"
tap_text "Turn on" 4
"$here/measure.sh" "Saver: X2 screen on" $win "$pkg"
adb shell input keyevent KEYCODE_SLEEP; sleep 2
"$here/measure.sh" "Saver: screen off" $win "$pkg"
adb shell input keyevent KEYCODE_WAKEUP; sleep 3
# Out of the saver, end and save the ride so the next run starts clean.
tap_text "Exit saver" 3
adb shell input tap 540 "${HANDLE_Y:-2229}"; sleep 2
adb shell input swipe 540 "${END_Y:-2151}" 540 "${END_Y:-2151}" 2600; sleep 4
tap 798 2214
