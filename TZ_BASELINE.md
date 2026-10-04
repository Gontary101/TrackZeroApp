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
