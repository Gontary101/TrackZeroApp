# TrackZero — Progress log

## NEEDS OWNER (checks on the real phone)

Collected from the v2 phases; each says what to do, what to look at and what counts as a pass. Report back per item.

- **N16 · Battery baseline, real ride (Phase 16).** With the build already on the phone (2026.10.02-5, before v2;
  git tag `phase-16` builds the same ride code), ride the same 60-minute route you know, screen on, a route loaded, voice on. Before: fully charge,
  then *Settings › Battery › Battery usage* and reset if the phone allows it. After: note TrackZero's share and the
  phone's total % used. If a computer is at hand: `adb shell dumpsys batterystats --reset` before,
  `adb shell dumpsys batterystats app.trackzero > ride.txt` after, and send `ride.txt`. Repeat the same ride on the
  final build (Phase 38). Pass: the two numbers are recorded for both builds.

## Phase 0 — Recon and baseline build

- Cloned CoMaps `v2026.08.31-14` into `trackzero/`, branch `trackzero`; submodules initialised (`--depth 1`).
- Ran `./configure.sh` (strings, drules, symbols). Symbol generation needed `optipng` + Qt6 Svg/Positioning
  (see TZ_DECISIONS D2); `configure.sh` reported success while symbols were missing, so they were regenerated
  by `tools/unix/generate_symbols.sh` and verified (24/24 files).
- Installed NDK `28.2.13676358` (pinned by the project); `android/local.properties` via `tools/android/set_up_android.py`.
- Recon written to `TZ_RECON.md` (BASE_PKG `app.organicmaps.sdk`, build task `:app:assembleFdroidDebug`).
- Files created: `TZ_RECON.md`, `TZ_PROGRESS.md`, `TZ_DECISIONS.md`.
- Baseline: `./gradlew :app:assembleFdroidDebug -Px64 -Pnjobs=14` → BUILD SUCCESSFUL (3 min). Installed on the
  `trackzero_pixel7` emulator (API 34, x86_64; `-gpu host` segfaults the emulator here, default GPU mode works).
  First run downloads the World overview + Puy-de-Dôme maps, map renders. Debug builds draw an FPS/zoom label.

## Phase 1 — Fork identity

- `android/app/build.gradle`: `appId` → `app.trackzero`, `appName` → `TrackZero`; removed the per-flavor
  `applicationIdSuffix` (`.google/.fdroid/.huawei`) so every flavor is `app.trackzero` (+ build-type suffix:
  debug → `app.trackzero.debug`, beta → `app.trackzero.test`). `app_name` is `TrackZero` for every build type.
- Placeholder launcher icon: adaptive `mipmap-anydpi-v26/ic_launcher.xml` (black background, mint disc
  `drawable/ic_tz_launcher_foreground.xml`, same mark as monochrome) + legacy PNGs regenerated for API < 26.
  Removed the debug/beta adaptive-icon overrides so all variants share the placeholder.
- `AndroidManifest.xml`: `MwmActivity` locked to `portrait`.
- Verified: `aapt2 dump badging` label `TrackZero`, package `app.trackzero.debug` installs next to
  `app.comaps.fdroid.debug`; first-run download + map render work.

## Phase 2 — Tokens, fonts, icons

- Fonts: full Barlow (Medium/SemiBold/Bold) and Barlow Condensed (SemiBold/Bold) from google/fonts; Doto as a
  static wght-900 instance (D3) → `res/font/`. OFL texts → `assets/licenses/`.
- `res/values/tz_colors.xml`, `tz_dimens.xml`, `tz_type.xml` extracted verbatim from the plan (Dot styles
  without `textStyle=bold`, D3).
- 39 icons from Appendix A (+ `flag`, D5) and 7 `_bold` maneuver variants. Worker-generated, then verified
  byte-for-byte against the appendix by script.
- `app.trackzero.ui.TzTextSize.applyCapped` (font-scale cap 1.3×) + `TzTextSizeTest`.
- Debug-only `app.trackzero.TzGalleryActivity` (src/debug) shows every text style and icon; checked on the
  emulator against the Tokens/Icons boards (Doto glyphs identical to the board).
- Shape drawables (2.5) are created with the components that use them (Phase 4).

## Phase 3 — Base chrome removed, dark map, overlay host

Approach changed: TrackZero host activity instead of hiding MwmActivity views (TZ_DECISIONS D6).
- New: `app/trackzero/TzConfig.java` (flags + all §X4 constants), `TzNavigator.java` (attach + insets for now),
  `base/TzActivity.java`, `base/TzMap.java`, `res/layout/tz_activity.xml` (MapView + `tz_root`),
  `res/values/tz_theme.xml`, `data/styles/trackzero/dark/{colors,drape_colors}.mapcss`,
  SDK asset symlink `drules_proto_cycling_dark.bin`.
- Changed (base): `AndroidManifest.xml` (TzActivity, portrait, singleTask), `DownloadResourcesLegacyActivity`,
  `DownloaderAdapter` (open `TzActivity.mapActivityClass()`), `util/ThemeSwitcher` (dark-only, D8),
  SDK `Framework.java/.cpp` (`nativeShowRect`, D9), `data/styles/cycling/dark/style.mapcss` (palette imports, D7),
  `data/styles/default/dark/symbols/current-position.svg` (mint rider dot).
- Edge-to-edge with transparent bars; insets stored in TzNavigator.
- TzMap: all plan methods implemented with base APIs (no no-ops); `setTouchEnabled` not needed (D9).
- Problems hit: `MapMode.set` before engine creation crashes (moved to rendering-initialised); cycling style
  not packaged on Android (added); stock ThemeSwitcher could persist the light cycling style (D8).
- Verified on emulator: launches to the black cycling map, no base buttons/menus/place page, transparent status
  bar over the map, mint rider dot, location in FOLLOW. Debug builds show the engine's FPS/zoom label (debug only).

## Phase 4 — Components

- `app.trackzero.ui`: TzBottomIsland, TzMapControls, TzChip (GPS + REC, pulse), TzStatsIsland, TzTurnCard
  (normal / imminent / off-route), TzManeuvers (glyph + phrase mapping), TzRouteCard, TzSilhouetteView,
  TzHoldButton, TzToggleRow (+ TzSwitch), TzSegmented, TzCategoryChip, TzListRow, TzPageHandle,
  TzElevationProfileView, TzMetricView, TzMotion. Buttons = MaterialButton styles.
- `app.trackzero.format.TzFormat` (§X3) + tests; `app.trackzero.geo.{Geo,DouglasPeucker}` + tests;
  `app.trackzero.follow.{Maneuver,TurnState}`.
- Resources: 18 `tz_bg_*` shapes, `tz_dot`, `tz_list_divider`, `animator/tz_press`, layouts `tz_c_*`,
  `tz_strings.xml`, `tz_attrs.xml`, `tz_styles.xml`, `tz_dimens_components.xml`, `tz_integers.xml`.
- Debug gallery shows every component with sample data; compared with the Components board on the emulator.
  Fixed during review: StatsIsland labels were left-aligned (default MATCH_PARENT params).
- HoldButton verified with adb: 0.9 s press → fill drains back, nothing happens; 1.8 s → "RIDE SAVED".
- Unit tests: 21 pass (TzFormat 8, Geo 4, DouglasPeucker 8, TzTextSize 1). No warnings in `app.trackzero`
  (EdgeToEdge API replaces the deprecated bar-colour setters).
- Emulator now runs headless (`-no-window`, 2 GB): the desktop display became unreachable, and one instance only.

## Phase 5 — Navigator, map screen, more skeleton

- `TzNavigator`: Screen enum (opaque flag + island tab), fragment container + persistent island in `tz_root`,
  crossfade 150 ms (`animator/tz_fade_in/out`), Back mapping via OnBackPressedCallback (enabled off-MAP; RIDE is
  delegated to the screen), insets forwarded to the current screen, restore after recreation.
- `screens/TzScreen` (base: `onInsets`, `onBackPressed`), `screens/TzMapScreen` (B1), `screens/TzMoreScreen`
  (B7 layout; Offline maps opens the downloader).
- `TzPrefs` (tz_prefs), `base/TzLocation` (LocationHelper feed), `base/TzBase` (downloader entry);
  `TzMap.centerOn`, ruler positioned in real coordinates (SDK margin handled in TzActivity).
- B1: GPS chip (mint ≤ 20 m, amber above, NO GPS without fix/permission), map controls (locate tint mint while
  following), ruler + "© OpenStreetMap" 16dp above the island, rider centred between status bar and island
  (visible area), first launch centres on the rider at z15 once.
- Verified on emulator: launch → B1 as on the board; island Map ↔ More; Back More → Map → leaves app; Offline
  maps opens the downloader and Back returns to More. Routes/Search/RIDE show "coming" toasts until their phases.
- The CoMaps startup attribution ("Map data © OpenStreetMap") still shows briefly next to the ruler at launch.

## Phase 6 — GPX import and routes library

- `routes/`: GpxTrack, GpxParser (SAX, D15), GpxException, RouteStats (distance, 5-pt smoothing + 3 m hysteresis
  gain, high point, loop / out & back / point-to-point), Route (+JSON incl. silhouette, D16), RouteStore
  (files/tz/routes/<uuid>.gpx + atomic routes.json, single IO thread), TzImport (picker + VIEW/SEND intents).
  `TzIo` (IO thread + atomic write). Parser/stats + tests by a codex worker, reviewed (fixed API-24 call).
- `screens/TzRoutesScreen`: B3 (header + IMPORT, All/Favorites/Recent, caption, cards, island clearance),
  B4 empty state (pre-computed dashed loop vector), favorites/recent empty captions, long-press Rename/Delete.
- Verified on the emulator with the user's GPX files: picker import of "Clermont-Ferrand (70.7km)" (70.5 km,
  1,293 m, LOOP); "Open with" VIEW intent of the 4.5 MB Massif Central traverse (1,437 km, point to point);
  favourite survives force-stop; Favorites filter; rename; delete removes index entry and GPX file.
- Unit tests: 41 pass (GpxParserTest 10, RouteStatsTest 10 added).

## Phase 7 — Route preview (B5, B6)

- `base/TzRouteLayer` + SDK `Framework.nativeShowTrackLine/RemoveTrackLine`: the route as two drape subroutes
  (glow + line) with a start ring, nothing persisted (D18). Drape: `RouteType::Track` width table, per-marker
  depth slots, clamped join angle (fixes a native abort on U-turns), TrackZero `Track*` colours.
- `routes/RouteGeometry` (2 m DP line, ≤ 600-pt profile, bounds) cached as `routes/<id>.geo` at import (D19);
  `RouteStore.loadGeometry` reads the cache, rebuilding it from the GPX when missing. `GpxParser` handler now
  tracks elements with an int stack instead of building path strings (cold parse of the 53k-point file on the
  emulator 5.4 s → 3.4 s, debug build; ≈ 1.0 s non-debuggable). Preview load from cache: 12 ms.
- `TzMap.showRect(…, paddingPx)` (D20); `screens/TzPreviewScreen` (B5 card: kind · starts {d} away, title,
  DISTANCE/GAIN/HIGH POINT, compact profile, START RIDE → "Coming in phase 9"; B6 peek on pan/pinch, back after
  1.5 s or on tap; star toggles favourite; back → ROUTES and removes the line).
- Verified on the emulator: Clermont loop and the 1,437 km traverse frame inside the 24dp-padded area between the
  top buttons and the card; line/glow/start ring match B5; panning collapses to the B6 peek and it returns;
  back returns to B3 and the line is gone. Unit tests: 44 pass (RouteGeometryTest 3 added).

## Phase 8 — Ride core

- `ride/`: TzFix (UTC + monotonic time), TzLocationSource, GpsLocationSource (GPS provider, 1 s, MSL altitude
  on API 34+), ReplayLocationSource + ReplayPath (debug, 25 km/h along `tz/replay.gpx`), BarometerSource,
  AltitudeFilter (baro + 60 s GPS offset, else GPS EMA), RideStats (§8.4), RideSession (state machine,
  auto-pause dated from the slow-down, D22), RideTrack, RideJournal (crash journal, D23), GpxWriter, Ride,
  RideStore (rides/<id>.gpx + atomic rides.json, marks the route ridden), TzRideService (foreground location
  service, notification "{elapsed} · {distance}" every 10 s + Pause/Resume, binder, sticky restore).
  `geo/GainCounter` shared with RouteStats. TzActivity restores an unfinished ride at launch.
- Debug-only `TzRideDebugActivity` (start / pause / resume / end + live numbers) until the ride screens exist.
- Codex workers wrote GpxWriter(+test) and ReplayPath(+test); reviewed — GpxWriter's per-point String.format
  replaced with a fixed-point append.
- Verified on the emulator with DEBUG_REPLAY and the 3.2 km Clermont → Aubière file: recording continued through
  2 min of screen-off (journal 66 → 190 lines), notification "TrackZero · Recording / 0:03:31 · 1.5 km",
  auto-pause at the end of the track, saved GPX 3,226 m vs 3,238 m file length (−0.4 %), journal removed,
  rides.json entry written. `am crash` mid-ride: the service came back within 2 s, PAUSED, with the last
  flushed totals (632 m / 91 s). Found and fixed: replay altitude pinned by the emulator barometer.
- Unit tests: 77 TrackZero tests pass (RideStats 11, RideSession 7, GpxWriter 6, ReplayPath 9 added).

## Phase 9 — Ride UI

- `screens/TzRideScreen`: ViewPager2 carousel (map R1/R2 · speed R4 · elevation R5 basic), controls sheet R6 as a
  BottomSheet whose collapsed state is the page handle (background, scrim and controls follow the slide), R7
  paused / auto-paused states, keep-awake flag, 1 s refresh + every fix, auto-zoom, rider at 67 %, route line for
  route rides, back opens/closes the sheet. `screens/TzSummaryScreen` (R8, codex worker, reviewed: thumbnail box,
  start ring, export name). `ride/TzRide` (start with permissions), TzActivity ride permission request (location +
  notifications on 13+), navigator: RIDE/SUMMARY screens, island RIDE, B5 START RIDE, opens on the ride when one
  is running or was cut short. viewpager2 declared explicitly (already resolved through Material).
- Base/SDK: `Framework.nativeScale`, `Map.setCompassEnabled` (D24).
- Fixed while testing: crash opening straight on a restored ride (follow before engine), zoom stuck at z6
  (ZoomToPoint dropped follow mode), floating RESUME drawn over the open sheet, "-0.0 %" grade, KM/H too far
  below the hero, UUID export names.
- Verified on the emulator with `emu_gps_play.py`: free ride R1 (25 km/h, z16, stats), R4, R6, R7 on all pages,
  auto-pause (no RESUME button), restore after the app was killed mid-ride (opens on RIDE, paused), route ride from
  B5 (line, turn card, SPEED · ETA · REMAINING, voice toggle shown), hold to end → R8, EXPORT GPX share sheet
  ("Clermont-Ferrand (70.7km).gpx"), DONE → map, route listed under Recent.

## Phase 10 — GPX follow engine (R2, R3, voice)

- `follow/`: `FollowRoute` (projection + cumulative distance), `ManeuverBuilder` (DP 8 m, ±30 m bearings, merge
  < 20 m, map junction filter), `Snapper`, `FollowEngine` (head to start, next/then maneuver, imminent, off-route
  5 s / back < 20 m, arrival, ETA over 15 min of moving time with the 18 km/h fallback, voice events, street
  requests). `ride/TzVoice` (TTS, ducking focus). `base/TzRoads` (street names, junctions). SDK:
  `nativeGetStreetName`, `nativeRoadJunctions`, `nativeShowTrackLine(…, dimmed)`; drape colour `TrackLineOff`.
- Tests: ManeuverBuilder (15), Snapper (5), FollowEngine (9, codex worker + reviewed), RideStats (+3).
- Verified on the emulator (Aubière → Clermont 6.6 km, real Puy-de-Dôme map): 14 maneuvers, "Turn right · Avenue
  Blaise Pascal" with THEN row, mint imminent state, R3 after a 70 m parallel detour (dashed line, `--:--`),
  back on route, restore after reinstall rebuilds the engine. `emu_gps_play.py` gained a lateral offset.

## Phase 11 — Climbs (R5)

- `follow/Climb`, `ClimbDetector`, `ClimbTracker` (current / next climb, to summit, gained, summit in),
  `ClimbNames`; `RouteGeometry` v2 (line-axis profile, 50 m elevation, waypoints); service prepares climbs with the
  follow engine and names them (SDK `nativeGetSummitName`: peaks and mountain passes). R5 page rebuilt to the
  board (header + pill, GRADE / TO SUMMIT / GAINED, Climb / Full route card with animated range, island); free
  ride and routes without elevation show ELEVATION with the ridden trace.
- Tests: ClimbDetector (9), ClimbTracker (5), ClimbNames (3), RouteGeometry (+7) — codex worker + reviewed; it found
  the start-trimming window too long (fixed). 130 TrackZero tests.
- Verified on the emulator, Clermont-Ferrand 70.7 km from km 48: "COL DE CEYSSAT" (map pass), CLIMB 5/5, to summit
  4.7 km, gained +33 m, summit in 15 min, avg 5.4 %, rider dot on the climb profile, Full route toggle; free ride
  shows ELEVATION. Real routes: 5 climbs each on the 70 km loops (10.8 km at 5.6 %, 5.5 km at 5.4 %…).

## Phase 12 — Search (B2)

- `base/TzSearch` (CoMaps search engine: text and category queries, result kinds, subtitles, map region name),
  `screens/TzSearchScreen` (field + keyboard on open, 250 ms debounce, Water / Bike repair / Café / Shelter chips,
  72dp result rows, caption "Searching offline map data · Puy-de-Dôme"), result → MAP centred and selected.
  SDK: `SearchEngine.nativeGetResultType`. TzActivity keeps a search result's selection. Test: TzSearchTest (3).
- Verified on the emulator (non-debuggable build for search speed): "puy de dome" → Puy de Dôme · Volcano · 1464 m
  first; tap → map centred on the summit with the mint selection, a tap elsewhere clears it; Water chip → springs,
  drinking water and taps nearest first.

## Phase 13 — More (B7) + History

- More rows all live: Offline maps (downloaded regions · size → downloader), Ride history (n rides → HISTORY),
  Units (toggle, applied to CoMaps), Voice guidance (language → system TTS settings), Display (keep awake in ride),
  Auto-pause ("Below 3 km/h" / Off), About (version → ABOUT). New screens `TzHistoryScreen`, `TzAboutScreen`;
  R8 from history returns to it. `TzBase.downloadedMaps / syncUnits / versionName`, `TzFormat.speedWhole`.
- Verified on the emulator: "Puy-de-Dôme · 73 MB", 8 rides, history → R8 → DONE → history, units → Imperial shows
  "GPS ±16 FT" on the map, About with the licence texts.

## Phase 14 — Polish, battery, accessibility

- Start screens and offline maps in TrackZero's UI (D31), hidden map really stops rendering + REC pulse (D32),
  accessibility / largest font / 360 × 640 fixes (D33), search heights grouped ("1,691 m"), R4 grid at the board's
  408dp, R8 track 2.6dp like the board, "— min" without unit.
- Screenshots in `shots/` (Pixel 7 emulator, 412 × 915 dp): B1 B2 B3 B5 B6 B7 R1 R2 R3 R4 R5 R6 R7 R8. Compared with
  the boards side by side. Remaining differences:
  - Map content: the boards' map is a stylised mock-up; the real style shows more detail (POI icons, building
    footprints) — the style pass of the plan is done (D4), POI density left as CoMaps' cycling style.
  - B1 rider: the engine's arrow (mint, outlined) instead of the board's dot with halo and heading cone.
  - B5 uses the real route and region; the board's high point and some values are placeholders ([X,XXX]).
  - B6 shows the card full; the board's collapsed peek appears only while the map is being moved (by design, D17).
  - B4 shot in the final checklist (route index moved aside, then restored).
  - Debug builds show CoMaps' "V: Zoom | FPS" label top-left; release builds do not.

## Z — Final acceptance checklist

Run on the Pixel 7 emulator (debug build), with airplane mode on from offline search through R8 (D34 has the fixes
it led to).
- [x] Fresh install → B1 dark map, no CoMaps UI (release build first run, Phase 14 / D31).
- [x] Import from the file picker (Phase 6) and from "Share to TrackZero" in the Files app. **Fixed:** TrackZero was
  missing from the share sheet because Android sends `.gpx` as octet-stream (D34). Now the route lands at the top
  of B3 with its stats.
- [x] B3 favorite, rename and delete survive a force-stop and relaunch. B4 shows with an empty index (`shots/B4.png`).
- [x] B5 frames the route, panning collapses it to B6 and it comes back (Phase 7).
- [x] START RIDE → R2 with real turns and street names; R3 off route; voice at 200 m / 50 m (Phase 10). **Fixed:** a
  ride started mid-route briefly showed "Head to start · 0 m" while the engine loaded (D34).
- [x] RIDE (island) → R1 free ride (Phase 9).
- [x] Exactly 3 pages: a 4th swipe stays on R5, the dots follow, and dragging the map on page 0 doesn't move it.
- [x] R4 updates every second; R5 shows the route's climbs (CLIMB 2/5). **Fixed:** "14.9 km" was clipped in the
  R5 metrics (D34).
- [x] Swipe up → R6; pause → R7; auto-pause; resume (Phase 9).
- [x] Releasing the END hold after 0.8 s does nothing; a 2 s hold → R8. Export GPX opens the share sheet and CoMaps
  imports the file through the share grant. The saved GPX passes `xmllint`, with one segment per stretch between
  restores.
- [x] Screen off for 10 min mid-ride: 5.0 → 10.8 km in ≈14 min of moving time at 25 km/h (5.8 km expected), nothing lost.
- [x] App killed mid-ride (`install -r`, twice) → reopening shows the paused ride (R7) with its distance and time;
  RESUME carries on, and R2 comes back with turns.
- [x] B2 offline search in airplane mode ("boulangerie" → nearby bakeries).
- [x] Airplane mode for the ride run above (route ride, pages, R5, R6, R8, export): all worked.
- [x] All unit tests pass; no warnings in `app.trackzero`.
- First real phone (Honor REA-NX9, release build): map downloads failed on mobile data, and a failed country
  was listed as on the phone. Both fixed (D35); Puy-de-Dôme downloaded over LTE.
- Emulator artifacts, not bugs: its barometer is fixed at 1013.25 hPa, so altitude, grade and gain stay flat during
  ride tests. Debug builds show the native "V: Zoom | FPS" label. uiautomator can't get an idle tree on pages that
  update every second (use screenshots).

## Phase 15 — Base routing + battery pass

- Bicycle paths from CoMaps' router without a routing session (D37):
  - a leg to a far start;
  - R3 "Recalculating…" then a leg back onto the track;
  - Navigate here from search → place card → preview → ride to the place, rerouted when left;
  - roundabout exits; JOIN at the end of a leg.
  `GuideTest` (7) covers the decisions.
- Verified on the emulator (Puy-de-Dôme map):
  - start 900 m away → 1.2 km leg with street names, remaining = leg + track;
  - off route 80 m → 119 m leg back, then the track's turns again;
  - Jardin Lecoq: card, preview (930 m, gain 25 m, high point 396 m, profile), ride with turns, saved under its name.
- Battery (D38), checked with `dumpsys location` / `dumpsys sensorservice`:
  - map location requests at 1 s;
  - fused location stopped in the background;
  - screen-off ride request `maxUpdateDelay=15s`, back to immediate on screen-on;
  - manual pause + screen off: no GPS request;
  - compass sensor unregistered while riding at 25 km/h.
- Fixed on the way:
  - map screens wiped each other's line and rider point on transitions (D39);
  - a search result was pulled back to the rider by follow mode;
  - the B1 ruler sits above the place card;
  - START RIDE is neutral until there is a path.


## Phase 16 — Baseline, trace tool, backlog file (plan v2)

- `TZ_BACKLOG.md` copied to the repository root as handed over.
- `TzTrace` (D40): ring buffer of 2,000 events, `files/tz/trace.txt` on demand. Trace points in TzMap (camera calls,
  mode changes, engine ready, heading retries, mode switches), TzActivity (mode changes, provider restarts, map
  rendering, rendering ready, every map fix with provider / accuracy / age), GpsLocationSource (start, stop,
  `setMaxDelay`, every fix), TzRouteLayer (show / showPath / showLeg / hide / setDimmed / engine ready, owners and
  engine ids), TzRideScreen (start, service connected, pages, first render, route load, lines, each auto-zoom branch),
  TzRideService (session start, guide created, first fix, placed), TzNavigator.show. Debug builds also mirror them to
  logcat (tag `TzTrace`).
- About: 7 quick taps on the version toggle tracing in release builds; a hidden group then shows **Share trace**
  (always shown in debug builds). `TzBase.shareGpx` and the trace share one FileProvider share path.
- Appendix A icons added (24 files, copied from the plan by script).
- `TZ_BASELINE.md`: the Road baseline on the emulator (scripts `tools/trackzero/measure.sh`, `baseline_run.sh`,
  build `-PtzMeasure`).
- Smoke test scripted (`tools/trackzero/smoke.sh`, D40). Starting point (debug build): all 13 steps pass after one fix:
  - **Fixed:** step 12's second Back left the app with the place card open; Back now closes the card first (D41).
  - Step 8: with Phase 15's rerouting, R3 shows for a few seconds and becomes a routed leg back ("Turn right · 60 m"),
    as designed (D37).
  - No crash in `logcat -b crash`.
- Unit tests: `TzTraceTest` (order, ring wrap, coordinate format). 169 JVM tests pass.
- **Open, for Phase 38:** leaving a place preview while its framing animation runs (Back within about 0.5 s of the
  path arriving), then closing the card on B1, can leave the place's selection circle drawn (1 of 5 tries). The trace
  shows the same calls as a clean run (`clearShown`, then the core's place-page deactivation); the engine keeps the
  circle after an interrupted camera animation.
