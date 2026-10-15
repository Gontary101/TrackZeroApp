# TrackZero — Battery and work baseline

Numbers to compare later phases against (plan v2 Phase 16; repeated in Phase 38). Everything here was measured, on the
conditions written next to it. Emulator numbers compare builds with each other; they say nothing about hours of
battery on a phone (see NEEDS OWNER N16 in `TZ_PROGRESS.md` for the field version).

## Road baseline (Phase 16, commit of this phase)

**Conditions.** Pixel 7 emulator (`trackzero_pixel7`, API 34, x86_64, software GL, 2 GB), debug package built
non-debuggable (`./gradlew :app:assembleFdroidDebug -Px64 -PtzMeasure`, D40), Puy-de-Dôme map, voice on, keep-awake on.
The ride is "Aubière - Clermont-Ferrand (6.6km)" from B5 START RIDE, GPS driven along it at 25 km/h by
`tools/trackzero/emu_gps_play.py` (a fix a second). Each screen is measured for 2 minutes, in this order, in one run:

```
tools/trackzero/baseline_run.sh > report.md      # runs measure.sh for each screen
```

`tools/trackzero/measure.sh <label> 120` measures one window:
- CPU: `utime + stime` of the process from `/proc/<pid>/stat` over the window (exact), and the mean of
  `adb shell top -b -n 24 -d 5 -p <pid>`;
- frames drawn by the app's views: `adb shell dumpsys gfxinfo <pkg>` "Total frames rendered", before minus after
  (the map is a separate GL surface and is not counted here);
- in the middle of the window: `adb shell dumpsys location` (the app's live requests), `adb shell dumpsys
  sensorservice` (its active connections), `adb shell dumpsys power | grep -i trackzero`, `adb shell ps -T -p <pid>`.
- `adb shell dumpsys batterystats --reset` at the start, `adb shell dumpsys batterystats <pkg>` at the end.

**Results** (run of 2026-10-04 23:07; full output in `shots/baseline/phase16_road.md`):

| Screen (2 min) | CPU, % of a core | Frames drawn by views | Threads | Location requests | Sensors |
|---|---|---|---|---|---|
| B1 idle | 64.4 | 77 | 46 | map: fused 1 s | rotation vector (SDK compass) |
| R2 (map page) | 89.5 | 130 | 52 | map: fused 1 s + ride: GPS 1 s | barometer (compass asleep at 25 km/h, D38) |
| R4 | 5.2 | 1265 | 52 | map fused 1 s + ride GPS 1 s | barometer |
| R5 | 1.8 | 128 | 55 | map fused 1 s + ride GPS 1 s | barometer |
| R6 open over R2 | 81.9 | 125 | 58 | map fused 1 s + ride GPS 1 s | barometer |
| Screen off | 0.1 | 0 | 58 | ride: GPS 1 s (voice on: no batching) | barometer |

Wake locks: none held by the app; during the ride the window manager holds `SCREEN_BRIGHT_WAKE_LOCK` for it (keep
screen awake), and nothing with the screen off.

Battery stats (whole run, about 16 min, emulator power profile): app uid **2.50 mAh**, of which GNSS 0.01000 mAh
(5m 59s 869ms); the rest is sensors (the emulator's profile charges the barometer and rotation vector heavily), CPU 0.0044
mAh. GPS on-time 5m 59s 869ms.

**Noise.** The same run 15 minutes earlier gave B1 idle 93.5 % and R2 87.9 %: on this emulator B1 idle has measured 64, 73 and 93 % in three runs
and R2 76 to 90 %: the map's software rendering is noisy. Compare the R4, R5 and screen-off rows (no map) to within a point, and
the map rows only for large changes, or take the mean of several runs.

**What the numbers show** (no change made in Phase 16):
- The map page costs almost all the CPU (software GL on the emulator); R4 and R5 freeze the map (D32).
- **R4 redraws its views about 11 times a second** (1,265 frames in 115 s) while R5 and R2 redraw once a second. That
  is the REC chip's 10-step pulse (D32). CPU stays at 5 %.
- **Two location requests during a ride:** CoMaps' fused request for the map (1 s) and the ride's own GPS request
  (1 s). Phase 17 (H1) addresses the first.
- The compass (rotation vector) is registered on B1 and toggles on and off: the emulator replays its last fix with a
  speed around the 3–4 m/s threshold of the compass sleep (D38). Not seen on a real ride; noted for Phase 38.

## Later runs (same conditions, `tools/trackzero/baseline_run.sh`)

CPU % of a core / frames drawn by views / threads, 2 minutes each. Location requests, sensors and wake locks were the
same in every run: one ride GPS request at 1 s (the map's own request is gone since Phase 17), the barometer, and
`SCREEN_BRIGHT_WAKE_LOCK` held for the app by the window manager.

| Screen | Phase 16 | Phase 21 (chevrons) | Phase 22 (profiles, Road) |
|---|---|---|---|
| B1 idle | 64.4 / 77 / 46 | 87.2 / 73 / 45 | 66.3 / 73 / 45 |
| R2 | 89.5 / 130 / 52 | 82.3 / 125 / 52 | 83.4 / 131 / 52 |
| R4 | 5.2 / 1265 / 52 | 5.9 / 1279 / 52 | 5.6 / 1262 / 52 |
| R5 | 1.8 / 128 / 55 | 2.5 / 130 / 54 | 2.6 / 128 / 54 |
| R6 over R2 | 81.9 / 125 / 58 | 81.8 / 134 / 60 | 86.0 / 132 / 60 |
| Screen off | 0.1 / 0 / 58 | 0.2 / 0 / 60 | 0.2 / 0 / 60 |

Full outputs: `shots/baseline/phase21_chevrons.md`, `shots/baseline/phase22_profiles.md`. The map rows stay inside
the noise band above. R5 and screen-off are within a point.

**Nothing idle (Phase 22.4).** On B1, after a GPX preview and again after the whole smoke test (two rides and a place
preview), `BicyclePath.nativeLoadedProfile()` reads -1 (debug `TzRideDebugActivity`: "router none") and the app has
no `TzRouter` thread (`ps -T`). The router and its road graphs exist only between a path request and the end of the
ride or the preview that asked for it.

## Battery saver (Phase 35, `tools/trackzero/saver_measure.sh`)

Same guided ride (the Aubière route, GPX replayed), -PtzMeasure build, 115 s per window. Full output:
`shots/baseline/phase35_saver.md`.

| Window | CPU % of a core | Frames (views) | GPS request | Barometer | Screen wake lock |
|---|---|---|---|---|---|
| Normal, R2 map page | 103.7 | 117 | 1 s | on | held |
| Normal, screen off | 0.2 | 0 | 1 s, at once (voice on) | on | — |
| Saver, X2 screen on | 0.7 | 12 | 1 s (window ended < 600 m from a turn) | off | none |
| Saver, screen off | 0.2 | 0 | 3 s, batched 9 s | off | none |

- **The saver removes the map:** with the screen on, X2 costs 0.7 % of a core against 103.7 % for the map page, a
  12-frame trickle (one redraw when its text changes) and no wake lock (the screen times out by itself).
- **GPS follows the turns:** with the screen off, more than 600 m from the next turn, fixes come every 3 s and are
  delivered within 9 s. The screen-on window ended near a turn, so its snapshot shows the 1 s request the policy
  asks for there (SaverPolicyTest covers both sides).
- **The barometer is off** in the saver (the sensor list is empty), so the climb comes from the GPS altitude meanwhile (D60).
- The real numbers (battery % per hour, missed turns) need two real 2 h+ rides: NEEDS OWNER N35.

## Phase 38 — the final build against the Phase 16 baseline

Same conditions as Phase 16 (emulator, -PtzMeasure, the Aubière ride at 25 km/h, 2-minute windows). Full outputs:
`shots/baseline/phase38_road.md`, `shots/baseline/phase38_profiles.md`.

| Screen | CPU % of a core, P16 → P38 | Frames by views | Threads | Location requests |
|---|---|---|---|---|
| B1 idle | 64.4 → 66.7 | 77 → 75 | 46 → 45 | 1 → 1 (the map's) |
| R2 (map page) | 89.5 → 84.6 | 130 → 132 | 52 → 53 | 2 → **1** (the ride's GPS, which feeds the map, D42) |
| R4 | 5.2 → 5.4 | 1265 → 1279 | 52 → 53 | 2 → 1 |
| R5 | 1.8 → 2.8 | 128 → 124 | 55 → 54 | 2 → 1 |
| R6 open | 81.9 → 80.6 | 125 → 129 | 58 → 58 | 2 → 1 |
| Screen off | 0.1 → 0.2 | 0 → 0 | 58 → 58 | 1 → 1 |

- **Road vs the single-profile baseline: within noise.** The map rows move inside their known ±15 % band; the rows
  without the map are within a point (R5's +1 point is the climb page's profile and climbs list, Phase 21–22 runs
  measured 2.5–2.6). Same sensors (rotation vector on B1, barometer on the ride), no wake lock of the app's own.
- **Battery stats, whole run** (about 16 min, the emulator's power profile): app 3.86 mAh against 2.50 in Phase 16,
  but Google Play services' share fell from 2.09 to 0.73 mAh: the sensors and GNSS the fused provider held for the
  map in Phase 16 are now the app's own single request. Together 4.58 mAh against 4.59; GNSS time for both 12.2 min
  against 16.3 min.
- **Each profile on the same ride** (R4, 2 min): Road 5.5 %, Gravel 5.5 %, Walk 5.2 %, Hike 5.3 %; 1,274–1,282
  frames, 51–53 threads, one 1 s GPS request each. A ride on a track routes nothing until a leg is needed, so the
  profile costs nothing while riding; it only chooses how legs are routed.
- **Inactive profiles and closed features**: one router at a time (Phase 22), and now no router thread at all 10 s
  after the last request (Phase 38 fix); with a ride running and the planner, loop search, stops, share, tutorial
  and sounds closed, the thread count and CPU are the baseline's.
- **Saver vs normal**: the Phase 35 section above.
- Real-ride figures: NEEDS OWNER N16 (repeat on this build), N35 (saver), N38.
