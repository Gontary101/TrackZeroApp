#!/usr/bin/env bash
# TrackZero: profiles one window of the app on the connected emulator or phone (TZ_BASELINE.md, "Profiling").
#
#   tools/trackzero/profile.sh <label> [seconds=60] [package=app.trackzero.debug] >> report.md
#
# Over the window: process CPU (/proc/<pid>/stat), CPU per thread (the 6 busiest, /proc/<pid>/task/*/stat), frames
# presented by the map's surface and by the views (SurfaceFlinger timestats), wakeups (alarms, jobs), and, read in the
# middle: location requests and sensor connections. Build with -PtzMeasure (not debuggable) for release-like numbers.
set -euo pipefail
label=$1
secs=${2:-60}
pkg=${3:-app.trackzero.debug}

pid=$(adb shell pidof "$pkg" | tr -d '\r')
[ -n "$pid" ] || { echo "$pkg is not running" >&2; exit 1; }
hz=100  # USER_HZ on Android

cpu_ticks() { adb shell cat "/proc/$pid/stat" | awk '{print $14 + $15}'; }
# "<tid> <name> <ticks>" for every thread; the name is in parentheses and may hold spaces.
thread_ticks() {
  adb shell "for f in /proc/$pid/task/*/stat; do cat \$f 2>/dev/null; echo; done" \
    | awk 'NF { match($0, /\(.*\)/); name = substr($0, RSTART + 1, RLENGTH - 2); gsub(/ /, "_", name);
                rest = substr($0, RSTART + RLENGTH + 1); split(rest, f, " "); print $1, name, f[12] + f[13] }'
}
alarms() { adb shell dumpsys alarm | grep -c "$pkg" || true; }

adb shell dumpsys SurfaceFlinger --timestats -disable >/dev/null 2>&1 || true
adb shell dumpsys SurfaceFlinger --timestats -clear >/dev/null
adb shell dumpsys SurfaceFlinger --timestats -enable >/dev/null
a0=$(alarms)
tt0=$(mktemp); thread_ticks > "$tt0"
t0=$(cpu_ticks); s0=$(date +%s.%N)
sleep $(( secs / 2 ))
loc=$(adb shell dumpsys location | grep -E "^ +[0-9]+/$pkg/[0-9A-F]+ Request\[" | sed 's/^ *//' | head -6 || true)
sens=$(adb shell dumpsys sensorservice | sed -n '/active connections/,/direct connections/p' \
       | grep -A1 -E "app\.(trackzero|organicmaps)" | grep -v "^--" | sed 's/^[[:space:]]*//' | head -6 || true)
sleep $(( secs - secs / 2 ))
t1=$(cpu_ticks); s1=$(date +%s.%N)
tt1=$(mktemp); thread_ticks > "$tt1"
ts=$(adb shell dumpsys SurfaceFlinger --timestats -dump)
adb shell dumpsys SurfaceFlinger --timestats -disable >/dev/null
a1=$(alarms)

dt=$(awk -v s0="$s0" -v s1="$s1" 'BEGIN {printf "%.1f", s1 - s0}')
cpu=$(awk -v a="$t0" -v b="$t1" -v d="$dt" -v hz=$hz 'BEGIN {printf "%.1f", 100 * (b - a) / hz / d}')
# Frames per layer of the app: "layerName = ..." then "totalFrames = N".
frames=$(echo "$ts" | awk -v p="$pkg" '/layerName = / {name = $0; sub(/.*layerName = /, "", name)}
  /totalFrames = / && index(name, p) {split($0, a, "= "); n = a[2] + 0; if (n > 0) print n, name}' \
  | sort -rn | head -4)

echo "### $label (${dt} s)"
echo "- CPU: ${cpu} % of one core"
echo "- Busiest threads (% of one core):"
join <(sort "$tt0" | awk '{print $1, $2, $3}' | sort -k1,1) <(sort "$tt1" | awk '{print $1, $3}' | sort -k1,1) \
  | awk -v d="$dt" -v hz=$hz '{c = 100 * ($4 - $3) / hz / d; if (c >= 0.5) printf "%.1f %s\n", c, $2}' \
  | sort -rn | head -6 | sed 's/^/    /'
echo "- Frames presented (per layer):"; echo "${frames:-  (none)}" | sed 's/^/    /'
echo "- Alarms listed for the app: $a0 -> $a1"
echo "- Location requests:"; echo "${loc:-  (none)}" | sed 's/^/    /'
echo "- Sensors:"; echo "${sens:-  (none)}" | sed 's/^/    /'
echo
rm -f "$tt0" "$tt1"
