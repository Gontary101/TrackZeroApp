#!/usr/bin/env bash
# TrackZero launch film (Phase 40): records the real app's scenes on the Pixel 7 emulator (1080 x 2400) as
# <raw dir>/<scene>.mp4. Debug build with the engine's debug label off, Puy-de-Dôme map, the Aubière route
# imported (CARD_Y), SystemUI demo mode on for a clean status bar (film.md). Each scene starts from a cold B1.
#
#   tools/trackzero/film/record.sh <raw dir> [scene ...]     scenes: map plan loop ride pages stops saver share end tutorial lightride
set -uo pipefail
here=$(cd "$(dirname "$0")/.." && pwd)
raw=${1:?raw dir}
shift
scenes=${*:-map plan loop ride pages stops saver share end tutorial lightride}
gpx="$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx"
pkg=app.trackzero.debug
card_y=${CARD_Y:-1064}
handle_y=2229
mkdir -p "$raw"
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }
drag() { adb shell input swipe "$1" "$2" "$3" "$4" "${5:-600}"; sleep "${6:-1}"; }
rec_pid=
rec() { # rec <scene> <seconds>: screenrecord in the background
  adb shell rm -f "/sdcard/tz_$1.mp4"
  adb shell screenrecord --bit-rate 20000000 --time-limit "$2" "/sdcard/tz_$1.mp4" &
  rec_pid=$!
  sleep 1
}
stop() { # stop <scene>: end the recording early and pull it
  adb shell pkill -INT screenrecord > /dev/null 2>&1
  wait "$rec_pid" 2> /dev/null
  sleep 2
  adb pull "/sdcard/tz_$1.mp4" "$raw/$1.mp4" > /dev/null && echo "  $1.mp4"
}
feeder=
feed() { # feed <km/h> <start m> <offset m>
  kill_feed
  python3 "$here/emu_gps_play.py" "$gpx" "$1" "$2" "$3" > /dev/null 2>&1 &
  feeder=$!
}
kill_feed() { [ -n "$feeder" ] && kill "$feeder" 2> /dev/null; feeder=; }
trap 'kill_feed; adb shell pkill -INT screenrecord > /dev/null 2>&1' EXIT
cold() {
  adb shell am force-stop "$pkg"
  adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null
  sleep 12
}
start_ride() { # from a cold B1: Routes, the route's card, START RIDE, at speed from <start m>
  tap 338 2200
  tap 540 "$card_y" 4
  feed 22 "$1" 0
  tap 540 2160 1
}
end_ride() {
  adb shell input tap 540 "$handle_y"; sleep 2
  adb shell input swipe 540 2151 540 2151 2600; sleep 4
  tap 798 2214
  kill_feed
}

first=$(grep -m1 -o '<trkpt[^>]*>' "$gpx")
adb emu geo fix "$(sed -E 's/.*lon="([-0-9.]+)".*/\1/' <<< "$first")" "$(sed -E 's/.*lat="([-0-9.]+)".*/\1/' <<< "$first")" 400 > /dev/null
adb shell input keyevent KEYCODE_WAKEUP

for s in $scenes; do
  echo "$s"
  case $s in
  map) # B1: centre on the rider, a slow drag across town
    cold; tap 964 558 3
    rec map 30
    sleep 2; drag 700 1500 300 900 2500 1; drag 300 900 700 1300 2500 2
    stop map ;;
  plan) # long-press the map: P1 with the destination, a point on the way, PREVIEW
    cold; tap 964 558 3
    rec plan 40
    sleep 1; drag 760 700 760 700 900 5
    tap 330 1250 7
    sleep 2; tap 540 2190 5
    stop plan ;;
  loop) # Routes, NEW, Propose a loop, 30 km, PROPOSE LOOPS, the results, the first loop
    cold
    rec loop 120
    tap 338 2200; tap 914 246 2
    tap 540 2156 3
    tap 540 2190 1
    sleep 60
    tap 540 560 6
    stop loop ;;
  ride) # START RIDE, the map page, speed, climb, back, the controls sheet
    cold
    rec ride 70
    start_ride 300
    sleep 45
    stop ride
    rec offroute 40
    feed 22 1500 90; sleep 22
    feed 22 1700 0; sleep 10
    stop offroute
    adb shell input keyevent KEYCODE_BACK; sleep 2
    end_ride ;;
  pages) # the three ride pages: map, speed, climb, and back
    cold; start_ride 900; sleep 12
    rec pages 30
    sleep 1
    drag 980 1300 120 1300 450 4
    drag 980 1300 120 1300 450 4
    drag 120 1300 980 1300 450 2
    drag 120 1300 980 1300 450 3
    stop pages
    end_ride ;;
  stops) # R6 (the handle's tap) › STOPS › S1 › the first stop (S2)
    cold; start_ride 1900; sleep 10
    rec stops 30
    adb shell input tap 540 "$handle_y"; sleep 3
    tap 200 1086 8
    tap 540 700 6
    stop stops
    adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 2
    end_ride ;;
  saver) # R6 › SAVER › TURN ON › X2, then EXIT SAVER
    cold; start_ride 2600; sleep 10
    rec saver 40
    drag 540 "$handle_y" 540 900 400 2
    tap 868 1086 3
    tap 540 2120 6
    tap 798 2170 4
    stop saver
    end_ride ;;
  share) # R6 › SHARE › E1, the other wording
    cold; start_ride 3500; sleep 8
    rec share 25
    drag 540 "$handle_y" 540 900 400 2
    tap 539 1086 5
    tap 784 1316 4
    stop share
    adb shell input keyevent KEYCODE_BACK; sleep 2
    end_ride ;;
  end) # a short ride, HOLD TO END & SAVE, R8
    cold; start_ride 5800; sleep 14
    rec end 25
    adb shell input tap 540 "$handle_y"; sleep 2
    adb shell input swipe 540 2151 540 2151 2600; sleep 12
    stop end
    tap 798 2214; kill_feed ;;
  lightride) # More › Appearance › Light, a ride in Light, then back to Dark
    cold; tap 930 2200; tap 540 837 5
    start_ride 300; sleep 6
    rec lightride 20
    sleep 14
    stop lightride
    end_ride
    tap 930 2200; tap 540 837 5 ;;
  tutorial) # More › Show the tutorial: the four steps
    cold; tap 930 2200
    rec tutorial 40
    tap 540 1728 4
    tap 540 2190 4; tap 540 2190 3
    tap 204 868 1.5; tap 540 868 1.5; tap 872 868 1.5
    tap 540 2190 4
    stop tutorial
    tap 540 2190 2 ;;
  esac
done
echo "crashes:"
adb logcat -b crash -d | grep -v "^---------" || echo "  none"
