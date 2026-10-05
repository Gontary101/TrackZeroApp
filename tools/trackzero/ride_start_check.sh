#!/usr/bin/env bash
# TrackZero: BUG-004 ride start check (plan v2 Phase 17.3). Pixel 7 emulator (1080 x 2400), DEBUG build (it traces to
# logcat), Puy-de-Dôme map, "Aubière - Clermont-Ferrand (6.6km)" imported (CARD_Y: its card in B3).
#
#   tools/trackzero/ride_start_check.sh <out dir> [runs per network state=20] [states="data wifi none"]
#
# WINDOW_MS (default 5000) is how long follow mode may take: this emulator draws with SwiftShader, and every queued
# engine command waits seconds behind the tiles (D42), so emulator runs use WINDOW_MS=10000.
#
# For each network state and run: cold start, ROUTES > route > START RIDE 2 s after the preview appears, the GPS
# moving along the route; screenshots at +3 s and at the end of the window, then the trace is checked
# (ride_start_trace.py); then
# the ride is ended and saved. Prints one line per run and a total.
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?out dir}
runs=${2:-20}
states=${3:-data wifi none}
gpx=${GPX:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=app.trackzero.debug
card_y=${CARD_Y:-1064}
mkdir -p "$out"
tap() { adb shell input tap "$1" "$2"; sleep "${3:-2}"; }

network() {
  case $1 in
  data) adb shell svc wifi disable; adb shell svc data enable ;;
  wifi) adb shell svc data disable; adb shell svc wifi enable ;;
  none) adb shell svc data disable; adb shell svc wifi disable ;;
  esac
  sleep 3
}

pass=0
total=0
feeder=
trap '[ -n "$feeder" ] && kill $feeder 2> /dev/null; adb shell svc wifi enable; adb shell svc data enable' EXIT
for state in $states; do
  network "$state"
  for i in $(seq 1 "$runs"); do
    total=$((total + 1))
    name="${state}_$(printf %02d "$i")"
    adb shell am force-stop "$pkg"
    adb logcat -c
    python3 "$here/emu_gps_play.py" "$gpx" 25 > /dev/null 2>&1 &
    feeder=$!
    adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null
    sleep 10
    tap 338 2200 1
    tap 540 "$card_y" 2      # START 2 s after the preview appears
    tap 540 2160 3
    adb exec-out screencap -p > "$out/${name}_3s.png"
    sleep $(( ${WINDOW_MS:-5000} / 1000 - 3 ))
    adb exec-out screencap -p > "$out/${name}_end.png"
    adb logcat -d -s TzTrace > "$out/$name.trace"
    if python3 "$here/ride_start_trace.py" --window "${WINDOW_MS:-5000}" < "$out/$name.trace" > "$out/$name.result"; then
      pass=$((pass + 1))
    fi
    echo "$name $(head -1 "$out/$name.result") $(tail -n +2 "$out/$name.result" | tr '\n' ' ')"
    kill "$feeder" 2> /dev/null; feeder=
    # End and save the ride: the handle opens the sheet, hold END, DONE.
    tap 540 "${HANDLE_Y:-2229}" 2
    adb shell input swipe 540 "${END_Y:-2151}" 540 "${END_Y:-2151}" 2600; sleep 4
    tap 798 2214 1
  done
done
echo "TOTAL $pass / $total"
