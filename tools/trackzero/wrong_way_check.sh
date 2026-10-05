#!/usr/bin/env bash
# TrackZero wrong-way check (plan v2 21.2, board R3W), Pixel 7 emulator, debug build installed, the route imported as
# for smoke.sh. Rides the route forward from FWD_FROM metres for FWD_S seconds, then backwards along it, then forward
# again; a screenshot every 2 s into <out dir>. Expected: R3W ("WRONG WAY") about 10 s after turning, cleared a few
# seconds after turning back.
#
# The emulator's GPS reports a course of 0 (north) on every fix (`geo fix` has no course, `geo nmea` is ignored), so
# the course is only right while the rider really moves north. The defaults ride backwards up the route's one
# southbound stretch (978-1172 m of "Aubière - Clermont-Ferrand"), where it is.
#
#   [FWD_FROM=1200] [FWD_S=12] tools/trackzero/wrong_way_check.sh <out dir> [route.gpx]
set -uo pipefail
here=$(cd "$(dirname "$0")" && pwd)
out=${1:?out dir}
gpx=${2:-$HOME/Downloads/GPS_Routes/Aubière - Clermont-Ferrand (6.6km).gpx}
pkg=${PKG:-app.trackzero.debug}
card_y=${CARD_Y:-1064}
fwd_from=${FWD_FROM:-1200}
fwd_s=${FWD_S:-12}
mkdir -p "$out"
rev=$(mktemp --suffix=.gpx)
# The same points in reverse order: riding it forward from (length - x) is riding the route backwards from x.
python3 - "$gpx" "$rev" << 'EOF'
import re, sys
s = open(sys.argv[1]).read()
pts = re.findall(r'<(?:trkpt|rtept)[^>]*>.*?</(?:trkpt|rtept)>', s, re.S)
open(sys.argv[2], 'w').write('<gpx><trk><trkseg>' + ''.join(reversed(pts)) + '</trkseg></trk></gpx>')
EOF
feeder=
feed() { # feed <gpx> <start m>
  [ -n "$feeder" ] && kill "$feeder" 2> /dev/null
  python3 "$here/emu_gps_play.py" "$1" 25 "$2" > "$out/feeder.log" 2>&1 &
  feeder=$!
}
where() { tail -1 "$out/feeder.log" | awk '{print $1}'; }
total() { tail -1 "$out/feeder.log" | awk '{print $4}'; }
trap '[ -n "$feeder" ] && kill $feeder 2> /dev/null; rm -f "$rev"' EXIT
shots() { # shots <label> <count>: one every 2 s
  for i in $(seq 1 "$2"); do
    adb exec-out screencap -p > "$out/$1_$(printf %02d $((i * 2)))s.png"
    sleep 1.4
  done
}

adb shell am force-stop "$pkg"
adb shell input keyevent KEYCODE_WAKEUP
adb shell am start -n "$pkg/app.organicmaps.SplashActivity" > /dev/null
sleep 12
adb shell input tap 338 2200; sleep 2          # ROUTES
adb shell input tap 540 "$card_y"; sleep 3     # the route's card: B5
feed "$gpx" "$fwd_from"
adb shell input tap 540 2160; sleep "$fwd_s"    # START RIDE, forward
at=$(where); len=$(total)
echo "turning back at $at m of $len m"
feed "$rev" "$(python3 -c "print($len - $at)")"
shots back 18
at=$(python3 -c "print($len - $(where))")
echo "turning forward again at $at m"
feed "$gpx" "$at"
shots forward 5
# End the ride (controls, hold END, DONE on the summary) so the next script starts from B1.
kill "$feeder" 2> /dev/null; feeder=
adb shell input tap 540 "${HANDLE_Y:-2229}"; sleep 2
adb shell input swipe 540 "${END_Y:-2151}" 540 "${END_Y:-2151}" 2600; sleep 4
adb shell input tap 798 2214; sleep 2
echo "crashes:"; adb logcat -b crash -d | tail -5
