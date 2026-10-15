#!/usr/bin/env bash
# TrackZero Phase 38.2: the same ride with each profile (Road, Gravel, Walk, Hike), measured on R4 (no map: the
# quietest page) for 2 minutes each, plus the router's state; -PtzMeasure build, the smoke test's route (CARD_Y).
#
#   tools/trackzero/profiles_measure.sh > report.md
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
gpx="$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx"
pkg=app.trackzero.debug
card_y=${CARD_Y:-1064}
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
tap_text() {
  local b="" x y
  for _ in 1 2 3 4 5; do
    adb exec-out uiautomator dump /sdcard/tz_ui.xml > /dev/null 2>&1 || true
    b=$(adb shell cat /sdcard/tz_ui.xml 2> /dev/null | grep -io "text=\"$1\"[^>]*bounds=\"[^\"]*\"" \
        | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | head -1 || true)
    [ -n "$b" ] && break
    sleep 2
  done
  [ -z "$b" ] && { echo "(no \"$1\" on screen)" >&2; return 1; }
  read -r x y < <(sed -E 's/\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]/\1 \2 \3 \4/' <<< "$b" \
    | awk '{print int(($1 + $3) / 2), int(($2 + $4) / 2)}')
  tap "$x" "$y" "${2:-2}"
}
echo "# Profiles run $(date '+%F %T'), $pkg"
echo
for p in Road Gravel Walk Hike; do
  adb shell am force-stop "$pkg"; adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null; sleep 14
  tap 338 2200; tap 540 "$card_y" 5
  # The profile pill shows the current one; the sheet lists all four.
  tap 547 214 3
  tap_text "$p" 6
  python3 "$here/emu_gps_play.py" "$gpx" 25 300 0 > /dev/null 2>&1 &
  feeder=$!
  tap 540 2160 15
  adb shell input swipe 980 1300 120 1300 450; sleep 5
  "$here/measure.sh" "R4 $p" 120 "$pkg"
  echo "- Router: $(adb logcat -d | grep -E 'TzRouter: (Path|No path)' | tail -1 | sed 's/.*TzRouter: //')"
  kill "$feeder" 2> /dev/null
  adb shell input swipe 120 1300 980 1300 450; sleep 2
  adb shell input tap 540 2229; sleep 2; adb shell input swipe 540 2151 540 2151 2600; sleep 5; tap 798 2214
  echo
done
