#!/usr/bin/env python3
"""TrackZero dev tool: a rider standing still, as a phone's GPS reports it: one fix a second around a point, a few
metres of random jitter, speed 0 (the emulator otherwise repeats one exact fix, which no real GPS does).

    python3 tools/trackzero/emu_gps_jitter.py lat lon [jitter metres=3] [seconds=forever]
"""
import math, random, subprocess, sys, time

lat0, lon0 = float(sys.argv[1]), float(sys.argv[2])
jitter = float(sys.argv[3]) if len(sys.argv) > 3 else 3
secs = float(sys.argv[4]) if len(sys.argv) > 4 else float('inf')
random.seed(1)
m_per_deg_lat = 111320.0
m_per_deg_lon = m_per_deg_lat * math.cos(math.radians(lat0))
# A slow random walk inside the jitter radius, like a GPS position wandering around the true one.
x = y = 0.0
t0 = time.time()
while time.time() - t0 < secs:
    x = max(-jitter, min(jitter, x + random.uniform(-1, 1) * jitter / 2))
    y = max(-jitter, min(jitter, y + random.uniform(-1, 1) * jitter / 2))
    lat = lat0 + y / m_per_deg_lat
    lon = lon0 + x / m_per_deg_lon
    subprocess.run(['adb', 'emu', 'geo', 'fix', '%.7f' % lon, '%.7f' % lat, '400', '8', '0'],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(1)
