#!/usr/bin/env bash
# TrackZero smoke test (plan v2 Appendix D), Pixel 7 emulator (1080 x 2400), debug build installed, Puy-de-Dôme map,
# "Aubière - Clermont-Ferrand (6.6km)" imported (CARD_Y: its card in B3). Takes a screenshot at every step into
# <out dir>; look at them, they are the test. Step 13 (unit tests) is run by Gradle, not here.
#
#   tools/trackzero/smoke.sh <out dir> [route.gpx]
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?out dir}
gpx=${2:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=${PKG:-app.trackzero.debug}
card_y=${CARD_Y:-1064}
mkdir -p "$out"
n=0
shot() { n=$((n + 1)); adb exec-out screencap -p > "$out/$(printf %02d $n)_$1.png"; echo "  shot $(printf %02d $n)_$1"; }
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
island() { case $1 in map) tap 148 2200;; routes) tap 338 2200;; ride) tap 540 2200 4;; search) tap 740 2200;; more) tap 930 2200;; esac; }
sheet() { adb shell input tap 540 2290; sleep 2; }
hold_end() { adb shell input swipe 540 2214 541 2214 2600; sleep 4; }
feeder=
feed() { # feed <start m> <offset m>
  [ -n "$feeder" ] && kill "$feeder" 2> /dev/null
  python3 "$here/emu_gps_play.py" "$gpx" 25 "$1" "$2" > "$out/feeder.log" 2>&1 &
  feeder=$!
}
where() { tail -1 "$out/feeder.log" | awk '{print $1}'; }
trap '[ -n "$feeder" ] && kill $feeder 2> /dev/null' EXIT

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
adb emu geo fix "$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")" "$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")" 400 > /dev/null
adb logcat -b crash -c
adb shell input keyevent KEYCODE_WAKEUP

echo "1. cold start"
adb shell am force-stop "$pkg"
adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null
sleep 12; shot B1
echo "2. island"
island routes; shot B3
island search; shot B2; adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1  # keyboard, then the screen
island more; shot B7
island map; shot B1_back
echo "3. route preview"
island routes; tap 540 "$card_y" 3; shot B5
echo "4. start ride"
feed 0 0
tap 540 2160 1; shot R2_1s
sleep 4; shot R2_5s
echo "5. pages"
adb shell input swipe 1000 1300 80 1300 120; sleep 2; shot R4
adb shell input swipe 1000 1300 80 1300 120; sleep 2; shot R5
adb shell input swipe 80 1300 1000 1300 120; sleep 3
adb shell input swipe 80 1300 1000 1300 120; sleep 3; shot R2_back
echo "6. controls sheet"
tap 540 2290; shot R6_tap  # the handle opens the sheet
adb shell input keyevent KEYCODE_BACK; sleep 2; shot R2_closed
adb shell input swipe 540 2230 540 900 400; sleep 2; shot R6_swipe
adb shell input keyevent KEYCODE_BACK; sleep 1
echo "7. pause / resume"
sheet; tap 540 2014 3; shot R7
sheet; tap 540 2014 3; shot R2_resumed
echo "8. off route 80 m and back"
feed "$(where)" 80; sleep 9; shot R3; sleep 8; shot R3_leg
feed "$(where)" 0; sleep 15; shot R2_back_on_route
echo "9. screen off 60 s"
d0=$(where)
adb shell input keyevent KEYCODE_SLEEP; sleep 60
adb shell input keyevent KEYCODE_WAKEUP; sleep 3; shot R2_after_screen_off
echo "   feeder moved $d0 -> $(where) m"
echo "10. end, export, done"
sheet; hold_end; shot R8
tap 283 2214 3; shot share_sheet
adb shell input keyevent KEYCODE_BACK; sleep 2
tap 798 2214; shot B1_after_ride
kill "$feeder" 2> /dev/null; feeder=
echo "11. free ride"
island ride; shot R1
sheet; hold_end; shot R8_free
tap 798 2214
echo "12. search > place (2nd result: a near one) > navigate here > preview > back x2"
island search; adb shell input text puy; sleep 10; shot B2_puy
tap 540 "${RESULT_Y:-795}" 4; shot place_card
tap 540 "${NAVIGATE_Y:-1926}" 6; shot preview_place
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2; shot B1_end
echo "crashes:"
adb logcat -b crash -d | grep -v "^---------" || echo "  none"
