#!/usr/bin/env python3
"""TrackZero dev tool: a phone held in the hand, as its compass sees it: the magnetic field wandering by a degree or
two around north, about 10 times a second (the emulator's field is otherwise perfectly still).

    python3 tools/trackzero/emu_compass_noise.py [degrees=2] [seconds=forever]
"""
import math, random, subprocess, sys, time

deg = float(sys.argv[1]) if len(sys.argv) > 1 else 2
secs = float(sys.argv[2]) if len(sys.argv) > 2 else float('inf')
random.seed(2)
field = 45.0  # µT, horizontal part of a mid-latitude field
a = 0.0
t0 = time.time()
while time.time() - t0 < secs:
    # A slow random wobble, as a hand makes.
    a = max(-deg, min(deg, a + random.uniform(-1, 1) * deg / 3))
    r = math.radians(a)
    x, y, z = field * math.sin(r), field * math.cos(r), -30.0
    subprocess.run(['adb', 'emu', 'sensor', 'set', 'magnetic-field', '%.2f:%.2f:%.2f' % (x, y, z)],
                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    time.sleep(0.1)
