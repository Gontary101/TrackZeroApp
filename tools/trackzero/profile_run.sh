#!/usr/bin/env bash
# TrackZero: profiles the app across everyday scenarios (TZ_BASELINE.md, "Profiling"). Pixel 7 emulator (1080 x 2400),
# build installed with -PtzMeasure (release-like Java and engine), "Aubière - Clermont-Ferrand (6.6km)" in B3
# (CARD_Y: its card).
#
#   tools/trackzero/profile_run.sh [seconds=60] [route.gpx] > report.md
#
# Cold start; B1 standing still (GPS jitter), B1 walking (no ride), the app in the background (no ride); then a ride
# at 25 km/h: R2, R4, R5 and the screen off. tools/trackzero/profile.sh measures each window.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
win=${1:-60}
gpx=${2:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=app.trackzero.debug
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
feeder=
stop_feeder() { [ -n "$feeder" ] && kill "$feeder" 2> /dev/null || true; feeder=; sleep 1; }
trap stop_feeder EXIT

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
lat=$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")
lon=$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")
adb emu geo fix "$lon" "$lat" 400 > /dev/null
adb shell input keyevent KEYCODE_WAKEUP

echo "# Profile run $(date '+%F %T'), $(adb shell getprop ro.product.model | tr -d '\r'), $pkg, ${win} s windows"
echo
# Cold start: the activity's own figure, best of three.
best=
for i in 1 2 3; do
  adb shell am force-stop "$pkg"; sleep 2
  t=$(adb shell am start -W -n "$pkg/app.organicmaps.SplashActivity" | awk '/TotalTime/ {print $2}' | tr -d '\r')
  [ -z "$best" ] || [ "$t" -lt "$best" ] && best=$t
  sleep 6
done
echo "### Cold start"
echo "- am start -W TotalTime, best of 3: ${best} ms"
echo
sleep 10

python3 "$here/emu_gps_jitter.py" "$lat" "$lon" 3 > /dev/null 2>&1 & feeder=$!
sleep 5
"$here/profile.sh" "B1 standing still (GPS jitter 3 m)" "$win" "$pkg"
stop_feeder

python3 "$here/emu_gps_play.py" "$gpx" 5 > /dev/null 2>&1 & feeder=$!
sleep 5
"$here/profile.sh" "B1 walking 5 km/h (no ride)" "$win" "$pkg"
stop_feeder

python3 "$here/emu_gps_jitter.py" "$lat" "$lon" 3 > /dev/null 2>&1 & feeder=$!
adb shell input keyevent KEYCODE_HOME; sleep 5
"$here/profile.sh" "Background, no ride" "$win" "$pkg"
stop_feeder
adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 6

# The ride: ROUTES > the route's card > START RIDE.
python3 "$here/emu_gps_play.py" "$gpx" 25 > /dev/null 2>&1 & feeder=$!
tap 338 2200
tap 540 "${CARD_Y:-1064}" 3
tap 540 2160 8
"$here/profile.sh" "R2 ride, map page (25 km/h)" "$win" "$pkg"
# To R4. A swipe right after a window is sometimes not taken: swipe again while the map's surface still draws.
map_drawing() {
  adb shell dumpsys SurfaceFlinger --timestats -clear > /dev/null; adb shell dumpsys SurfaceFlinger --timestats -enable > /dev/null
  sleep 2
  local n; n=$(adb shell dumpsys SurfaceFlinger --timestats -dump | awk -v p="$pkg" '/layerName = / {s = index($0, "SurfaceView") && index($0, p)}
    /totalFrames = / && s {split($0, a, "= "); t += a[2]} END {print t + 0}')
  adb shell dumpsys SurfaceFlinger --timestats -disable > /dev/null
  [ "$n" -gt 0 ]
}
sleep 3
for i in 1 2 3; do
  adb shell input swipe 880 1300 200 1300 300; sleep 3
  map_drawing || break
done
"$here/profile.sh" "R4 speed page" "$win" "$pkg"
adb shell input swipe 880 1300 200 1300 300; sleep 3
"$here/profile.sh" "R5 climb page" "$win" "$pkg"
adb shell input swipe 200 1300 880 1300 300; sleep 2
adb shell input swipe 200 1300 880 1300 300; sleep 2
adb shell input keyevent KEYCODE_SLEEP; sleep 3
"$here/profile.sh" "Screen off (ride recording)" "$win" "$pkg"
adb shell input keyevent KEYCODE_WAKEUP; sleep 3
adb shell wm dismiss-keyguard > /dev/null 2>&1 || true; sleep 2
# End and save the ride (R6 > hold END > R8 > DONE) so the next run starts clean.
adb shell input tap 540 "${HANDLE_Y:-2229}"; sleep 2
adb shell input swipe 540 "${END_Y:-2151}" 540 "${END_Y:-2151}" 2600; sleep 4
tap 798 2214
stop_feeder
