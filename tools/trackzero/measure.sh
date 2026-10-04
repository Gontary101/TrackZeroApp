#!/usr/bin/env bash
# TrackZero: measures what the app costs on one screen, on the connected emulator or phone (TZ_BASELINE.md).
#
#   tools/trackzero/measure.sh <label> [seconds=120] [package=app.trackzero.debug] >> report.txt
#
# Over the window: process CPU (utime + stime from /proc/<pid>/stat, and the mean of `top` samples every 5 s), frames
# drawn by the app's views (gfxinfo), and, read in the middle of the window: location requests, sensor connections,
# wake locks and threads. Build with -PtzMeasure (not debuggable) for numbers close to a release build.
set -euo pipefail
label=$1
secs=${2:-120}
pkg=${3:-app.trackzero.debug}

pid=$(adb shell pidof "$pkg" | tr -d '\r')
[ -n "$pid" ] || { echo "$pkg is not running" >&2; exit 1; }
hz=100  # USER_HZ on Android

cpu_ticks() { adb shell cat "/proc/$pid/stat" | awk '{print $14 + $15}'; }
frames() { adb shell dumpsys gfxinfo "$pkg" | awk '/Total frames rendered/ {print $4; exit}'; }

t0=$(cpu_ticks); f0=$(frames); s0=$(date +%s.%N)
samples=$(( secs / 5 ))
tmp=$(mktemp)
adb shell top -b -n "$samples" -d 5 -p "$pid" > "$tmp" &
top_pid=$!
sleep $(( secs / 2 ))
# Live registrations only (the dump also lists their history), and per-provider totals for the app.
loc=$(adb shell dumpsys location | grep -E "^ +[0-9]+/$pkg/[0-9A-F]+ Request\[|$pkg: min/max" | sed 's/^ *//' | head -8 || true)
# The app's sensor connections: TrackZero's own and the SDK's (app.organicmaps.*) listeners.
sens=$(adb shell dumpsys sensorservice | sed -n '/active connections/,/direct connections/p' \
       | grep -A1 -E "app\.(trackzero|organicmaps)" | grep -v "^--" | sed 's/^[[:space:]]*//' | head -8 || true)
wake=$(adb shell dumpsys power | grep -i trackzero | sed 's/^ *//' | head -4 || true)
threads=$(( $(adb shell ps -T -p "$pid" | wc -l) - 1 ))
wait $top_pid
t1=$(cpu_ticks); f1=$(frames); s1=$(date +%s.%N)

top_mean=$(awk -v p="$pid" '$1 == p {for (i = 1; i <= NF; i++) if ($i ~ /^[SRDZT]$/) {s += $(i + 1); n++; break}} END {if (n) printf "%.1f", s / n; else print "?"}' "$tmp")
rm -f "$tmp"
cpu=$(awk -v a="$t0" -v b="$t1" -v s0="$s0" -v s1="$s1" -v hz=$hz 'BEGIN {printf "%.1f", 100 * (b - a) / hz / (s1 - s0)}')
fr=$(( ${f1:-0} - ${f0:-0} ))

echo "### $label ($(awk -v s0="$s0" -v s1="$s1" 'BEGIN {printf "%.0f", s1 - s0}') s)"
echo "- CPU: ${cpu} % of one core (/proc stat), top mean ${top_mean} %"
echo "- Frames drawn (views): $fr"
echo "- Threads: $threads"
echo "- Location requests:"; echo "${loc:-  (none)}" | sed 's/^/    /'
echo "- Sensors:"; echo "${sens:-  (none)}" | sed 's/^/    /'
echo "- Wake locks:"; echo "${wake:-  (none)}" | sed 's/^/    /'
echo
