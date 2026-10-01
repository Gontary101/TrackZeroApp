#!/usr/bin/env python3
"""TrackZero dev tool: drives the emulator's GPS along a GPX at a constant speed (1 fix/s), so the map and the
ride recorder both see the same moving rider.

    python3 tools/trackzero/emu_gps_play.py route.gpx [km/h=25] [start metres=0]

Uses `adb emu geo fix` with a speed: this emulator ignores `geo nmea`, and geo fix cannot set the course, so the
heading comes from the movement.
"""
import math, re, subprocess, sys, time
path, kmh = sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 25
start_m = float(sys.argv[3]) if len(sys.argv) > 3 else 0
s = open(path).read()
pts = []
for m in re.finditer(r'<(?:trkpt|rtept)([^>]*)>(.*?)</(?:trkpt|rtept)>', s, re.S):
    a = m.group(1)
    lat = float(re.search(r'lat="([-0-9.]+)"', a).group(1)); lon = float(re.search(r'lon="([-0-9.]+)"', a).group(1))
    e = re.search(r'<ele>([-0-9.]+)</ele>', m.group(2))
    pts.append((lat, lon, float(e.group(1)) if e else 0.0))
R = 6371000
def dist(p, q):
    p1, p2 = math.radians(p[0]), math.radians(q[0]); dp = p2 - p1; dl = math.radians(q[1] - p[1])
    h = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2*R*math.atan2(math.sqrt(h), math.sqrt(1-h))
def bearing(p, q):
    p1, p2 = math.radians(p[0]), math.radians(q[0]); dl = math.radians(q[1] - p[1])
    y = math.sin(dl)*math.cos(p2); x = math.cos(p1)*math.sin(p2) - math.sin(p1)*math.cos(p2)*math.cos(dl)
    return (math.degrees(math.atan2(y, x)) + 360) % 360
cum = [0.0]
for i in range(1, len(pts)): cum.append(cum[-1] + dist(pts[i-1], pts[i]))
def at(d):
    i = 0
    while i < len(cum) - 2 and cum[i+1] < d: i += 1
    seg = cum[i+1] - cum[i]; t = 0 if seg == 0 else min(1, (d - cum[i]) / seg)
    p, q = pts[i], pts[i+1]
    return (p[0]+t*(q[0]-p[0]), p[1]+t*(q[1]-p[1]), p[2]+t*(q[2]-p[2]), bearing(p, q))
v = kmh / 3.6
d = start_m
while True:
    lat, lon, ele, brg = at(min(d, cum[-1]))
    moving = d < cum[-1]
    knots = v * 1.943844 if moving else 0.0
    # This emulator ignores NMEA; geo fix takes altitude, satellites and speed (knots), not the course.
    subprocess.run(['adb', 'emu', 'geo', 'fix', '%.7f' % lon, '%.7f' % lat, '%.1f' % ele, '10', '%.2f' % knots],
                   stdout=subprocess.DEVNULL)
    print('%.0f m / %.0f m' % (min(d, cum[-1]), cum[-1]), flush=True)
    if not moving: break
    d += v
    time.sleep(1)
