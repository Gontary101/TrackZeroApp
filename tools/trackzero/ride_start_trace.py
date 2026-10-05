#!/usr/bin/env python3
"""TrackZero: checks one ride start from a TzTrace log (BUG-004, plan v2 Phase 17.3).

    adb logcat -d -s TzTrace | python3 tools/trackzero/ride_start_trace.py [--timeline] [--window ms]

Reads the debug build's trace lines ("<elapsed ms> <tag> <text>"), finds the last ride start (ride screen onStart)
and asserts:
  a. every map fix after the start is within 50 m of the ride's GPS fix nearest in time (within 1 s);
  b. the route line is drawn (engine ids) within 500 ms of the ride screen's onStart;
  c. at most two zoom commands in the first 5 s;
  d. follow mode is reached within the window (5 s; --window for a slow emulator, D42) and not dropped afterwards.
Prints PASS or FAIL with the reasons and how long follow took; --timeline prints the events from 2 s before the start
to the end of the window.
Exit code 0 = pass.
"""
import math
import re
import sys

LINE = re.compile(r'TzTrace\s*:\s*(\d+) (\S+) (.*)$')
LL = re.compile(r'(-?\d+\.\d{5}),(-?\d+\.\d{5})')


def dist(a, b):
    r = 6371000
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dp, dl = p2 - p1, math.radians(b[1] - a[1])
    h = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * r * math.asin(math.sqrt(h))


def main():
    events = []
    for raw in sys.stdin:
        m = LINE.search(raw.rstrip())
        if m:
            events.append((int(m.group(1)), m.group(2), m.group(3)))
    starts = [t for t, tag, text in events if tag == 'ridescr' and text.startswith('onStart')]
    if not starts:
        print('FAIL no ride start in the trace')
        return 1
    t0 = starts[-1]
    span = int(sys.argv[sys.argv.index('--window') + 1]) if '--window' in sys.argv else 5000
    window = [e for e in events if t0 <= e[0] <= t0 + span]
    fails = []

    # a. map fixes vs ride fixes
    ride_fixes = [(t, tuple(map(float, LL.search(x).groups()))) for t, tag, x in events
                  if tag == 'gpsfix' and t >= t0 - 2000 and LL.search(x)]
    for t, tag, text in window:
        if tag != 'mapfix':
            continue
        m = LL.search(text)
        near = [f for f in ride_fixes if abs(f[0] - t) <= 1000]
        if not m or not near:
            continue
        p = tuple(map(float, m.groups()))
        d = min(dist(p, f[1]) for f in near)
        if d > 50:
            fails.append('a. map fix %d m from the ride fix at +%d ms: %s' % (d, t - t0, text))

    # b. the line within 500 ms
    drawn = [t for t, tag, text in events if tag == 'line' and ' drawn ' in text and t >= t0 - 5000]
    owned = [t for t, tag, text in events if tag == 'line' and text.startswith('route drawn owner=TzRideScreen')
             and t >= t0]
    handed = [t for t, tag, text in events if tag == 'line' and text.startswith('transfer') and t >= t0 - 2000]
    shown = [t for t in owned + handed if t <= t0 + 500]
    if not shown:
        fails.append('b. no route line for the ride within 500 ms (first ride line at %s)'
                     % ('+%d ms' % (owned[0] - t0) if owned else 'never'))

    # c. zoom commands
    zooms = [(t, x) for t, tag, x in window
             if t <= t0 + 5000 and tag == 'map' and (x.startswith('setZoom') or x.startswith('startRideCamera'))]
    if len(zooms) > 2:
        fails.append('c. %d zoom commands in 5 s: %s' % (len(zooms), '; '.join('+%d %s' % (t - t0, x) for t, x in zooms)))

    # d. follow reached, not dropped
    modes = [(t, x) for t, tag, x in window if tag == 'map' and x.startswith('onModeChanged')]
    reached = [t for t, x in modes if x.split()[1] in ('FOLLOW', 'FOLLOW_AND_ROTATE')]
    if not reached:
        fails.append('d. follow mode not reached in %.1f s' % (span / 1000))
    else:
        dropped = [(t, x) for t, x in modes if t > reached[0] and x.split()[1] == 'NOT_FOLLOW']
        if dropped:
            fails.append('d. follow dropped at +%d ms: %s' % (dropped[0][0] - t0, dropped[0][1]))
    follow_ms = (reached[0] - t0) if reached else None

    if '--timeline' in sys.argv:
        for t, tag, text in events:
            if t0 - 2000 <= t <= t0 + span:
                print('%+6d %-8s %s' % (t - t0, tag, text))
    info = 'follow at +%d ms' % follow_ms if follow_ms is not None else 'no follow'
    print(('PASS ' + info) if not fails else 'FAIL ' + info + '\n  ' + '\n  '.join(fails))
    return 0 if not fails else 1


if __name__ == '__main__':
    sys.exit(main())
