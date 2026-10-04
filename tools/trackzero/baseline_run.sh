#!/usr/bin/env bash
# TrackZero: the Phase 16 baseline run, repeatable for Phase 38 (TZ_BASELINE.md). Pixel 7 emulator (1080 x 2400),
# build installed with -PtzMeasure, "Aubière - Clermont-Ferrand (6.6km)" imported (CARD_Y: its card in B3).
#
#   tools/trackzero/baseline_run.sh [route.gpx] > report.md
#
# B1 idle 2 min, then a route ride driven at 25 km/h by emu_gps_play.py: R2, R4, R5, R6 open and screen off, 2 min
# each. Battery stats are reset at the start and read at the end.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
gpx=${1:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=app.trackzero.debug
win=120
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
lat=$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")
lon=$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")
adb emu geo fix "$lon" "$lat" 400 > /dev/null
adb shell am force-stop "$pkg"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null
sleep 15
adb shell dumpsys batterystats --reset > /dev/null
echo "# Baseline run $(date '+%F %T'), $(adb shell getprop ro.product.model | tr -d '\r'), $pkg"
echo
"$here/measure.sh" "B1 idle" $win "$pkg"

# The ride: GPS along the route, ROUTES > first card > START RIDE.
python3 "$here/emu_gps_play.py" "$gpx" 25 > /dev/null 2>&1 &
feeder=$!
trap 'kill $feeder 2> /dev/null || true' EXIT
tap 338 2200
tap 540 "${CARD_Y:-1064}" 3
tap 540 2160 5
"$here/measure.sh" "R2 route ride (map page)" $win "$pkg"
adb shell input swipe 1000 1300 80 1300 120; sleep 2
"$here/measure.sh" "R4 speed page" $win "$pkg"
adb shell input swipe 1000 1300 80 1300 120; sleep 2
"$here/measure.sh" "R5 climb page" $win "$pkg"
adb shell input swipe 80 1300 1000 1300 120; sleep 2
adb shell input swipe 80 1300 1000 1300 120; sleep 2
adb shell input tap 540 2290; sleep 2
"$here/measure.sh" "R6 controls open (over the map page)" $win "$pkg"
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_SLEEP; sleep 2
"$here/measure.sh" "Screen off (ride recording)" $win "$pkg"
adb shell input keyevent KEYCODE_WAKEUP; sleep 2
# End and save the ride (R6 > hold END > R8 > DONE) so the next run starts clean.
adb shell input tap 540 2290; sleep 2
adb shell input swipe 540 2214 541 2214 2600; sleep 4
tap 798 2214

echo "## Battery stats (since the reset)"
echo '```'
adb shell dumpsys batterystats "$pkg" | grep -iE "Estimated power use|Capacity|Computed drain|^ +Uid u0a|GPS|gps|sensor|Wake lock|Foreground|cpu" | awk "NR <= 12"
echo '```'
