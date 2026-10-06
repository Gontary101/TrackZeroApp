# TrackZero — Progress log

## NEEDS OWNER (checks on the real phone)

Collected from the v2 phases; each says what to do, what to look at and what counts as a pass. Report back per item.

- **N16 · Battery baseline, real ride (Phase 16).** With the build already on the phone (2026.10.02-5, before v2;
  git tag `phase-16` builds the same ride code), ride the same 60-minute route you know, screen on, a route loaded, voice on. Before: fully charge,
  then *Settings › Battery › Battery usage* and reset if the phone allows it. After: note TrackZero's share and the
  phone's total % used. If a computer is at hand: `adb shell dumpsys batterystats --reset` before,
  `adb shell dumpsys batterystats app.trackzero > ride.txt` after, and send `ride.txt`. Repeat the same ride on the
  final build (Phase 38). Pass: the two numbers are recorded for both builds.
- **N17 · Ride start (BUG-004, Phase 17).** Mobile data on, Wi-Fi off. 10 ride starts from B5 START RIDE and 10 from
  the island RIDE, half of them indoors right after opening the app (GPS not locked yet) and then walking outside,
  half outdoors. Look at the first seconds: does the map jump to other places or zoom in and out? Is the route line
  there all the time (no blink between preview and ride)? How long until the map is heading-up on the rider at about
  two thirds of the screen height? Pass: every start shows the rider and the line correctly from the first second (or
  from the first GPS position, indoors), no jumps, no zoom in/out; heading-up within about a second. If one fails:
  About › tap the version 7 times › Share trace, and send the file.
- **N20 · Route line in sun (FEAT-005, Phase 20).** Midday, sun out, phone on the handlebar at arm's length.
  About › tap the version 7 times › **Route line** cycles B → A → C → D (B is the default). For each, look at a route
  preview (B5) and a ride (R2) for a second and look away: which line can you follow most easily at a glance? Report
  the order. Pass: one is chosen; I then remove the others.
- **N21 · Direction and wrong way (BUG-002, Phase 21).** On a route preview (B5) and a ride (R2), the black chevrons
  inside the line point the way to ride and can be seen at a glance. On a quiet road, ride about 100 m of the route
  backwards: the card turns amber with "WRONG WAY · Turn around" and the voice says "Wrong way" within about 10 s.
  Turn round: it clears within a few seconds. At the turning point of an out-and-back route it must not appear. Pass:
  all of these.
- **N23 · Surfaces (FEAT-006, Phase 23).** Open the preview of a long route you know (B5), for the first time after
  installing: the surface bar should appear within about a second, and at once the next time. Do the paved, gravel
  and rough parts match what you know of the route? "No data" is roads the map has no surface for. Pass: quick, and
  plausible.
- **N24 · Gravel (FEAT-003, Phase 24).** On a route preview (B5), choose Gravel in the profile pill. Ride a short
  route you know that has gravel tracks nearby, starting a little away from its start, so a leg to the start is worked
  out. Does the leg take quiet roads and easy tracks rather than main roads? Leave the route on purpose: does the way
  back avoid main roads too? Pass: the legs look like what you would choose on a gravel bike, and no rough or
  technical ground unless there is no other way.
- **N25 · Walking (FEAT-012, Phase 25).** Choose Walk on a place preview (search › NAVIGATE HERE): the path should use
  footpaths and pedestrian streets, and the card says "On foot · about H:MM". Start it and walk: turns are announced,
  the map stays at the same close zoom, the ride pauses by itself when you stand still for about 10 s and goes on when
  you walk again. After 30 minutes, note the battery used and compare with a 30-minute ride. Pass: all of these, and
  the battery use is reasonable.
- **N26 · Hiking (FEAT-013, Phase 26).** Choose Hike on a place you would hike to (search › NAVIGATE HERE): the path
  should take trails where they exist, the card shows CLIMB and DESCENT and "Hike · about H:MM". Is the time about
  right for you? If the map grades part of the way as demanding, a line says so under the surface legend. Also: the
  plan says no text may say "safe", "easy" or "accessible", but board N2's Gravel text says "easy tracks"; I kept the
  board's words (D51). Tell me if it should change. Pass: trails taken, times plausible.
- **N27 · Bike + walk (BUG-001, Phase 27).** Search a place a bike cannot reach (the Puy de Dôme summit: "Temple de
  Mercure") › NAVIGATE HERE. The card says "To a place · bike + walk" with BY BIKE and ON FOOT, the map shows the ride,
  a hollow ring where to leave the bike and dots to the place. Ride towards it (any part): the ETA is sensible, and at
  the ring the card says "Leave the bike, walk" and the voice says it. Leave the route before the ring: you are routed
  back by bike to the ring. Pass: all of these. (The board's "P" letter on the ring is not there yet: D52.)
- **N28 · Plan on the map (FEAT-002, Phase 28).** ROUTES › NEW › Plan on the map, choose Gravel, tap a destination and
  two points on the way; drag a pin (hold it first), remove one, reorder the list, undo. PREVIEW, then START RIDE and
  ride a little; end the ride and tap SAVE ROUTE. The route is in ROUTES as "Planned · Gravel". Long-press it › Export
  GPX. Also: a long-press on the map (B1) opens the planner with that point. Pass: all of these feel right with a
  finger on a real screen.
- **N29 · Propose a loop (FEAT-004, Phase 29).** ROUTES › NEW › Propose a loop. With your position: 30 km, Rolling,
  Gravel, PROPOSE LOOPS. Note how long "LOOKING… n/8" takes (the emulator: about 45 s). Look at the three loops: do
  they go out and come back by different roads, are they rideable on a gravel bike, are the names (direction · town)
  sensible? Tap one: the preview, then save it (bookmark) or START RIDE. Also: Pass through › tap a place on the map ›
  PROPOSE LOOPS (every loop goes through it); TRY OTHER LOOPS; tap the button while it is looking (it stops); a walk
  at 10 km. Pass: at least two loops you would actually ride, found in under a minute, and nothing keeps working
  (battery) once you leave the screen.
- **N30 · Stops along the route (FEAT-007, Phase 30).** On a route ride with water you know of ahead: R6 › STOPS.
  Are the places and their distances ahead believable (compare with what you see), and the repair shops? Pick one
  2–5 km ahead › ADD STOP: "Recalculating…", then back to the ride with the route unchanged; 2 km before it the turn
  card leads you there, then "You have reached …", then back onto the route further on. Also: add one and remove it
  (S1 › the row › REMOVE); B5's pin button (the whole route; a row shows the place on the preview); a free ride's
  STOPS (within 5 km, no ADD STOP). Pass: the stop is reached and the route continues; nothing searches while S1 is
  closed.
- **N31 · Bivouac places (FEAT-010, Phase 31).** Search › Bivouac where you know the campsites, and R6 › STOPS ›
  Bivouac on a route through them. Are the campsites and huts there (VERIFIED), is every shelter NOT CONFIRMED with
  "No source says sleeping here is allowed.", are the hours and fees right where the map has them, and are there no
  bus shelters? Tap one in Search: the route there (B5). Pass: no shelter is ever shown VERIFIED and the list helps
  you find a place for the night.
- **N32 · Tones (FEAT-008, Phase 32).** More › Sounds: tap each tile. Then ride with tones on and the voice off,
  phone on the handlebar at 25–35 km/h, on a route with left and right turns. Can left, right and straight be told
  apart without looking? Are the off-route and GPS-lost sounds clearly different from turns? Note the media volume
  you needed. With earbuds or a car connected, do the tones still come from the phone? Pass: you can tell the turns
  apart by ear, and each warning plays once.
- **N33 · Ride-time estimate (FEAT-009, Phase 33).** With your real ride history on the phone, start a route you know
  and open R6: "from your last n rides" and a time. Ride it, and compare with the real total time (moving and
  stopped) on R8. Does the estimate stay the same all ride? Pass: within about 15 % on a ride like your usual ones.
- **N34 · Share location (FEAT-014, Phase 34).** On a ride, R6 › SHARE. Is the "Near …" line right? Send a
  check-in to yourself with the messaging app you use, and open the link: the right place? Tap Low battery / help
  twice: "I need help". In airplane mode: the text is still built and COPY works. Pass: the message reaches you and
  its link shows where you were.
- **N35 · Battery saver (FEAT-011, Phase 35).** Two real rides of 2 h or more on the same kind of route, one normal
  and one with R6 › SAVER › TURN ON from the start, screen off in a pocket or on the bars. Note the battery % at the
  start and end of each (battery % per hour). In the saver: did every turn give its tones in time (none missed, none
  late)? Off route: the warning plays? Unlock mid-ride: X2 shows at once, EXIT SAVER brings the map back at once.
  Pass: no missed turn, and the saver ride uses clearly less battery per hour (the numbers go in TZ_BASELINE).
- **N36 · Light appearance (FEAT-015, Phase 36).** More › Appearance switches Dark / Light in one tap and comes back
  to More. Midday sun, phone on the handlebar at arm's length: look at R2, R4 and B5 in both appearances. Which is
  read faster at a glance? Does anything disappear in Light (the route line and its chevrons, the rider, the turn
  card, the surface bar's classes, the stats)? If a computer is at hand, run Accessibility Scanner on B1, B3, B5, R2, R4 and R6 in Light. Pass: you
  can name the faster one, and nothing disappears.
- **N37 · Tutorial (Phase 37).** Install the build fresh (or clear the app's data): after the map download, T1 shows
  before the map, and the location question comes only after it. SKIP on any step goes to the map; the next start
  opens the map directly. More › Show the tutorial plays it again and comes back to More. With TalkBack on, each step
  reads sensibly (step number, SKIP, the picture, the title) and the tone tiles play. Pass: all of these.
- **N18 · Immersive ride (FEAT-001, Phase 18).** On a ride, no status bar or navigation bar on all three pages and with
  the controls open. Swipe from the top or bottom edge: the bars appear briefly over the ride and nothing moves. Tap the
  handle 10 times: it opens the controls every time. A quick upward swipe from just above the handle opens them too.
  The handle sits under the stats island with a small gap, never overlapping (BUG-003), on R1–R7. The first hold on HOLD TO END works. A camera hole does not cover
  the turn card. After a ride with an off-route detour, B1 shows no leftover route line. Leave the ride (summary, Home
  and back): the bars are back outside the ride. Pass: all of these.

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

## Phase 17 — BUG-004: ride start camera and line

- **Reproduced first** with `TzTrace` (`tools/trackzero/ride_start_check.sh`, cold start → B3 → route → START RIDE
  2 s after the preview appears, GPS moving): 3 of 3 starts failed. Causes and evidence in D42: zooms added up
  (13 → 19 → 13), the follow toggle flipped heading-up off, the line was missing for over a second, the camera used
  the preview's visible area; map and ride used different position sources (fused vs GPS). Provider restarts ruled out.
- **Fixes:**
  - engine command `FollowMyPosition(rotate, zoom, animate)` (drape, map framework, JNI) instead of "switch to next
    mode";
  - `TzMap`: one follow request (`FollowRequest`), cancelled only by a touch; the ride camera in one command after
    the ride's visible area; zooms as levels (`RideZoom`);
  - `TzRouteLayer.transfer`: START RIDE hands the preview's line to the ride screen;
  - `LocationHelper.setExternalSource` / `pushLocation`: during a ride the map shows the ride's GPS fixes, and CoMaps'
    fused request is stopped.
  - Removed: the D10 step machine, the "refused" timeout and heading retries (D36), `Framework.nativeScale`.
- **Matrix** (emulator, debug build):
  - cold starts, 20 each with mobile data / Wi-Fi only / no network: 59 / 60 pass, with a 10 s window for follow
    (`shots/bug004/`). Lines from the first frame (handed over), map fixes = ride fixes, at most 2 zoom commands.
  - The one miss (no network #9) was the engine's queue (D42): follow was asked again correctly and arrived later.
    The extra no-network set with a 15 s window passed 8 / 8, follow at +2.6 to +8.9 s.
  - Follow took 0.7–9 s on this emulator: SwiftShader GL handles queued engine commands at a few per second. CoMaps'
    own locate button took 4.7 s on an idle B1. Phone timing is N17.
  - Warm starts, START after 10 s, place rides (NAVIGATE HERE → START) and the island RIDE: same traces in the smoke
    runs; the island free ride uses the same camera command.
  - Not reproducible here: network positions before GPS lock (the emulator has none). The fix removes the second
    source; field check N17.
- **Earlier fix superseded:** the first zoom fix waited for a "settled" zoom and still went 13 → 19 in 1 start of
  18. The final version sends zoom levels, which cannot add up.
- Tests: `FollowRequestTest` (6), `RideZoomTest` (6), `TzRouteLayerTest` (4); 185 JVM tests pass.
- Smoke test (Appendix D): all steps pass, no crash. The script's page swipes now wait 3 s (one was dropped at 2 s).

## Phase 18 — Immersive ride (FEAT-001)

- `TzDisplay.setImmersive`: bars hidden while the ride screen is started, shown again when it stops (summary, other
  apps, background). Swipe from an edge shows them for a moment (`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`).
- `TzActivity`: `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`. `TzNavigator`: insets `systemBars | displayCutout`, the
  top read ignoring visibility (steady when the bars are revealed); also fixed an early return I had added there, which
  left the island under the 3-button bar after a navigation-mode change.
- Deviation (D43): the ride's touch targets sit above the system gesture strip, where touches are not reliably delivered
  with the bars hidden; the map and pages reach the edge.
- Checked on the emulator, gesture and 3-button navigation: no bars on R2, R4, R5 and the open sheet; a top-edge swipe
  reveals the status bar without moving the ride; R8 and B1 show the bars; handle tap 4/4; END hold works first time.
  Android shows its own one-time "Viewing full screen" hint the first time.
- Smoke test passes. The scripts' touch points are now variables, and page swipes start away from the screen edges:
  in immersive mode an edge swipe reveals the bars instead of reaching the pager.
- Time lost on an emulator artifact (D43 note): a route line removed about a minute late by the slow debug engine.

## Phase 19 — Ride handle at the bottom edge (BUG-003)

- Cause (D44): Material's bottom sheet raised its own peek height to the gesture inset + 48dp, while the islands
  were placed from TrackZero's peek. Measured 10 px of it on the emulator; about 70 px on a gesture-navigation phone
  with the old layout.
- `applyBottomLayout()` / `RideBottomLayout`: one base for the handle, islands, RESUME, page padding and sheet content;
  called from insets, page bind and resize; `setGestureInsetBottomIgnored(true)`. Handle 34dp, island 4dp above it,
  grabber-to-dots 6dp. Fast swipe up from the bottom 120dp opens the sheet.
- Checked: island bottom 2174 px, sheet top 2185 px (4dp); gesture and 3-button navigation; R7 with RESUME;
  360 × 640 dp at the largest font: handle below the island, nothing overlaps.
- **Fixed on the way:** a configuration change (display size, font) crashed the ride screen: `fitGrid`, posted from
  the layout pass, ran after the fragment had detached. Both posted fit methods now check `isAdded()`.
- Tests: `RideBottomLayoutTest` (2). Smoke test: steps 1–21 pass; the run then lost its way in Android's share chooser
  (script: Back now waits 3 s). Page swipes in the scripts are 300 ms (adb's 120 ms swipe sends too few move events).
- `shots/R1`–`R7.png` updated.

## Phase 20 — Route line for daylight (FEAT-005)

- `TZ_RESEARCH_ROUTE_VISIBILITY.md`: ambient-contrast table (`tools/trackzero/contrast.py`, copied from the handoff),
  what helps (width, a hard casing, direction marks, a light map in the brightest light), sources checked against
  their abstracts. One correction to the plan's summary: Dobres et al. (2017) found the polarity effect in dark
  surroundings; in bright light both polarities read well.
- `nativeShowTrackLine(…, style)`: B (shipped) 8dp line on a 12dp black casing; A, C, D for the comparison
  (D45). Dimmed line unchanged. Leg and place path use the same style. Drape colours `TrackCasing`, `TrackLineBright`;
  styles regenerated. Hidden About row "Route line" (tracing on) cycles the styles; kept in `tz_prefs`.
- Checked on the emulator: B5 in all four styles; R2 and R3 in B. Same number of subroutes as before (casing + line
  instead of glow + line), so no extra draw work; the emulator's frame numbers are too noisy to show a difference
  either way (TZ_BASELINE).
- Smoke test: all steps pass. The line shown on B1 just after the ride is the emulator engine's lag (D43): it was
  gone 2.5 minutes later.
- `shots/B5.png`, `R2.png`, `R3.png` updated.

## Phase 21 — Direction chevrons and wrong way (BUG-002)

- Chevrons (D46, the plan's approach B): two black strokes per chevron in the line's own mesh
  (`RouteShape::AddChevrons`), at distances that `nativeShowTrackLine(…, chevronSpacingM)` computes. Ride lines and
  the leg: every 150 m. Previews: max(150 m, length / 14). None on the dimmed line, none within 25 m of the ring.
  Drape colour `TrackArrow` (#000000); styles regenerated. `TzRouteLayer` keeps the spacing per line, and a spacing
  change (preview → ride) draws the new line before removing the old one.
- First build drew nothing: the strokes' winding was the opposite of the line's and drape culls back faces (found from
  a native log of the built mesh, fixed by vertex order).
- Cost: baseline run with chevrons (`shots/baseline/phase21_chevrons.md`): R2 82 % CPU, 125 frames (Phase 16: 90 %,
  130), within the noise and the +5 % budget.
- Wrong way: `FollowEngine.Phase.WRONG_WAY` (10 s against the route's direction by more than 120° and 30 m back; leaves
  after 3 s with it or 20 m forward; never near a U-turn or off route). `TurnState.wrongWay`, frozen remaining, ETA
  `--:--`. `TzTurnCard` has one warning layout for off route and wrong way (R3W: U-turn glyph, "WRONG WAY",
  "Turn around", "The route goes the other way"). `Events.onWrongWay` → voice "Wrong way". `Guide` passes it
  through for the track and for legs, and treats it as being on the route.
- Tests: `FollowEngineTest` (backwards ride enters after 10 s and 30 m and leaves on turning round; 30 m from the furthest place; slow creep needs the
  30 m; out-and-back turn-around; standing still; off route), `GuideTest` (track and leg pass-through),
  `TzRouteLayerTest` (spacing, redraw without a blink). 196 tests pass.
- `tools/trackzero/wrong_way_check.sh`: rides the route forward, backwards, forward again on the emulator, then ends
  the ride. R3W shown and cleared as expected (`shots/R3W.png`); the emulator's fixed course of 0 limits where it can
  be checked (D46). A per-fix trace line (`ride follow along= course= v=`) shows the engine's view.
- Smoke test: all steps pass, no crashes. `shots/B5.png`, `R2.png` (with chevrons), `R3W.png` updated.

## Phase 22 — Navigation profiles: the shared foundation

- `routes/Profile` (ROAD, GRAVEL, WALK, HIKE; only ROAD available), `TzPrefs.profile()` (default ROAD, falls back to
  ROAD when the stored one is not available), `ui/TzProfiles` (label, description, icon from resources).
- Router (D47): `BicyclePath.nativeBuild(lat[], lon[], profile, hasBearing, bearing)` with waypoints, one router at a
  time remembered with its profile, `nativeLoadedProfile()`. `TzRouter.route(lat[], lon[], Profile, …)` plus a
  two-point overload; `Callback.onPath(path, Reason)`.
- The ride's profile: intent extra at START (from `TzPrefs`), `RideSession.profile()`, journal record `F,{id}` (a
  restored ride keeps it), `Ride.profile` in `rides.json`, optional `Route.profile` in `routes.json`; the guide's paths
  use it.
- UI: `ui/TzProfilePill` (44dp, icon + label + chevron), `ui/TzBigRow` (the sheet's rows: 28dp mint check or 2dp
  ring), `screens/TzProfileSheet` (BottomSheetDialogFragment, surface1, 32dp top corners, grabber, PROFILE, note).
  B5: the pill between Back and the star, top-aligned with them; choosing a profile works the path to a place out again.
- Idle check (22.4): on B1 after a GPX preview, and after the whole smoke test (rides and a place preview), the debug
  activity reads "router none" and the app has no TzRouter thread.
- Tests: `ProfileTest`, `TzRouterTest` (result codes → reasons, constants against CoMaps' enum), `RideSessionTest`
  (profile through the journal; old journals are Road; ride to a place). 205 tests pass.
- Baseline with a Road ride (`shots/baseline/phase22_profiles.md`, table in `TZ_BASELINE.md`): same threads,
  location request and sensors as Phases 16 and 21; CPU within the emulator's noise (R2 83 %, Phase 16 90 %).
- Smoke test: all steps pass, no crashes. In step 28 the place path came 6 s after opening (debug native on the
  emulator: 2.1 s to load the road graph, 2.6 s to route), after the screenshot; the log shows it was found.

## Phase 23 — Surface preview (FEAT-006)

- `Framework.nativeRoadSurfaces` (closest road within 15 m, its `psurface` class; samples read a stretch at a time,
  D48), `TzRoads.surfaces`, `routes/SurfaceProfile` (runs, 1 % merging on the bar, percentages summing to 100,
  `upToDate`), `routes/RouteSurfaces` (sampling every max(50 m, length / 3000), maps stamp, paths to a place),
  `RouteStore.loadSurfaces` (RouteGeometry format 3 cache; version 2 read without surfaces).
- `ui/TzSurfaceBarView`: 8dp bar, 2dp gaps, radius 4, legend of the classes present (8dp squares, 11sp caps, wraps),
  content description "60 percent paved, 1 percent poor paved, …". Colours `tz_sf_*` (Tokens v2).
- B5: bar and legend under the sparkline; the card grows (180 ms) when they arrive and the route is framed again;
  "Download this region's map to see surfaces" when no sample has a map. A path to a place gets them too.
- Checked on the emulator: the Clermont-Ferrand 70.7 km loop shows paved 60 %, poor paved 1 %, rough 1 %, no data
  38 % after 1.6 s the first time (debug native) and at once the second time; a test route in Paris (no map) shows the
  caption; the place path in the smoke test shows its bar. `shots/B5.png`, `B5_surfaces_loop.png`,
  `B5_surfaces_no_map.png`.
- First build aborted in CoMaps' storage thread check (maps stamp read on the IO thread); fixed by reading it on the
  main thread before the work is queued.
- Tests: `SurfaceProfileTest` (9), `RouteGeometryTest` (format 3 round trip, version 2 read). 216 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 24 — Gravel profile and quieter roads (FEAT-003)

- `libs/routing_common/tz_profile.{hpp,cpp}` (`SetTzProfile` / `GetTzProfile`, atomic); `BicycleModel` builds its
  speeds, factors and surfaces for the profile, on top of the country model's own; `BicyclePath.cpp` sets the profile
  before it builds a router. Road: quieter roads; Gravel: minor roads, gravel and easy tracks (D49, final table).
- Gravel switched on (`Profile.available()`): the profile sheet has two rows (`shots/N2.png`).
- Check with data: About › hidden tools › **Compare profiles** (`routes/ProfileCompare`, `tools/trackzero/
  profile_compare.md`), ten trips with both profiles plus the contraflow trip. `Framework.nativeRoadClasses` gives the
  road-class shares; it and `nativeRoadSurfaces` now share one batched lookup. Result and pass in D49: passes with the
  plan's starting numbers; contraflow street ridden against its one-way.
- Idle check again with Gravel selected and no ride: "router none".
- Smoke test with Gravel selected (both rides started with `profile=gravel`): all steps pass, no crashes. The
  emulator was set back to Road afterwards.
- Seen once, not reproduced: right after the comparison run (minutes of router work), one B5 opened at zoom 16 near
  the start without the line; reopening it showed it framed and drawn. Emulator engine lag (D43) is the likely
  cause; noted for the phone check.
- 216 tests pass (`ProfileTest` updated for two profiles).

## Phase 25 — Walking profile (FEAT-012)

- Native: Walk and Hike build the pedestrian router; `BicyclePath.turnPedestrian` (a second int per turn), mapped by
  `TzRouter.Turns.pedestrian`.
- On foot (D50): `RideSession` auto-pause (1 km/h for 10 s, resume above 2 km/h), `FollowEngine.setFoot` (ETA
  fallback 4.8 km/h, course from 1 m/s, so wrong way from 1 m/s), `Guide` passes the flag to its engines,
  `RideZoom.setFoot` (z17), free walk named "Walk {date}". `TzConfig` holds the foot constants.
- `routes/FootTime` (4.8 km/h + 1 h per 600 m up), `TzFormat.hoursMinutes`; the place preview's label reads "On foot ·
  about H:MM" with Walk. Walk switched on (three rows in the profile sheet).
- Checked on the emulator: the comparison tool with Walk (D50: footpaths and tracks Road does not use); NAVIGATE HERE
  to La table du Puy with Walk (3.4 km, about 0:49); a walk ride saved with `"profile": "walk"`; a free walk saved as
  "Walk Oct 6, 2026"; 2-minute R2 measurement on a walk (`shots/baseline/phase25_walk.md`).
- Tests: `TzRouterTest` (pedestrian turns), `RideSessionTest` (foot auto-pause against a bike's), `FollowEngineTest`
  (foot ETA fallback), `FootTimeTest`, `ProfileTest`. 220 tests pass.
- Smoke test with Walk selected: steps 1–25 pass, no app crash (free walk saved as "Walk Oct 6, 2026"). The
  emulator's Android system hung during step 26 (clock jump, Play services died, then no activity service; `adb
  reboot` did nothing), so the emulator was restarted and the smoke test run again with Road: all steps pass, no
  crashes.

## Phase 26 — Hiking profile (FEAT-013)

- `PedestrianModel` with Hike's factors, and `path|expert` at half weight on a hike (D51). Hike already got the
  pedestrian router in Phase 25.
- `Framework.nativeRoadDifficulty` (difficult or expert path per the map, the shared sampler), `TzRoads.difficulty`,
  `RouteSurfaces.demanding`.
- `FootTime.hikeSeconds`, `RouteStats.ascentDescent`. The Hike place preview shows DISTANCE / CLIMB / DESCENT, "Hike ·
  about H:MM", the no-elevation caption and the demanding line when they apply. Free hikes are named "Hike {date}".
  All four profiles are switched on (`shots/N2.png`, `shots/B5_hike.png`).
- Check (D51): Hike from Orcines to the Puy de Dôme summit takes the trail (65 % path); Walk in town stays on streets;
  Hike and Walk differ where trails exist. A Hike place preview: "Hike · about 1:04", climb 58 m, descent 30 m.
- Tests: `FootTimeTest` (hiking time), `RouteStatsTest` (ascent and descent), `ProfileTest`. 222 tests pass.
- Smoke test with Hike selected (both rides `profile=hike`, free hike "Hike Oct 6, 2026"): all steps pass, no crashes.
  The emulator was set back to Road.

## Phase 27 — A place a bike cannot reach (BUG-001)

- `follow/BikeWalkPlanner` (bike → walk → bike to candidate points, 8 s each; joins the paths), `RoutedPath.walkFromM`,
  `ridePart` / `walkPart` / `walkTail`, `Maneuver.LEAVE_BIKE` ("Leave the bike, walk", `ic_tz_walk`).
- Native: `BicyclePath.nativeBuild(…, timeoutSec)`; `nativeShowTrackLine` takes a look (line, dimmed, walk dots) and
  ring 3 / 4 (hollow at the start, and the end's ring). `TzRouteLayer.showRide` / `showWalk` / `hideWalk`.
- Preview: bike profiles go through the planner; B5W card (label, BY BIKE / ON FOOT / GAIN, the note row on surface3),
  the walk-only offer starts a Walk ride, "No route to this place" when even walking fails.
- Ride: ETA walks the part after P; leaving the path re-routes by bike to P (joined to the same walk) or on foot after
  it; the ride screen draws both parts.
- Checked on the emulator (D52): Temple de Mercure → B5W (`shots/B5W.png`, `B5W_walk_part.png`), started and ended
  as a ride; La table du Puy unchanged.
- Tests: `BikeWalkPlannerTest` (7: unchanged bike place, bike to the 400 m candidate then walk, walk-only, no route,
  hike fallback, no map passed through, cancel), `GuideTest` (re-route before and after P), `FollowEngineTest`
  (bike + walk ETA). 232 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 28 — Plan routes on the map (FEAT-002)

- Map plumbing: `Framework.nativeScreenToLatLon` / `nativeLatLonToScreen`, `TzMap.screenToLatLon` /
  `latLonToScreen` / `recentlyTouched`, `ui/TzMapTap`.
- `routes/PlanDraft` (tap rules, 8 waypoints, move, remove, reorder, undo 20 deep, start), `ui/TzPinLayer`
  (numbered pins, destination ring, picked start; drag after 300 ms, tap to remove; moves only while the map can move).
- `screens/TzPlanScreen` (navigator screen PLAN): P1 and P2 cards, profile pill (reroutes), undo, CHANGE start,
  rows with drag handles (`ItemTouchHelper`) and remove, routing 400 ms after a change, failure messages, PREVIEW,
  "Discard this plan?".
- Library: `Route.temp`, `RouteStore.putTemp` / `saveTemp` (temp never listed, replaced by the next),
  `GpxWriter.writeRoute` (track with elevations, points as `wpt`), "Planned · {Profile}" kind, Export GPX in the card
  menu. B3 NEW and B4 NEW ROUTE open `TzNewRouteSheet` (N1); `TzBigRow.showChevron`. B5 shows Save (bookmark) for a
  temp route; R8 offers SAVE ROUTE. A long-press on B1 opens the planner.
- Checked on the emulator (D53): N1, P1, P2, B5 with Save, B3 "Planned · Gravel", export and re-import, R8 SAVE
  ROUTE, discard dialog, idle CPU (`shots/N1.png`, `P1.png`, `P2.png`, `B5_planned.png`, `R8_planned.png`).
- Tests: `PlanDraftTest` (7), `GpxRouteRoundTripTest` (distance within 0.5 %, waypoints, elevation). The RouteStore
  temp round trip uses `org.json`, which local tests do not have (D47); its rules were checked on the emulator
  instead. 240 tests pass.
- Smoke test: all steps pass, no crashes. The emulator was set back to Road.

## Phase 29 — Propose a loop (FEAT-004)

- `routes/LoopPlanner` (pure; the router is an interface): eight candidate triangles start → A → B → start in eight
  directions (or four start → via → C → start with a point to pass through). Each candidate is routed once, moved
  closer once if a point has no road, and resized once if more than 25 % off. Candidates are scored on size, climb and
  retraced share; at most 25 % retraced is kept. Three are picked at least 90° apart. Budget: 8 tries + 4 retries.
- `screens/TzLoopScreen` (navigator screen LOOP, opaque): L1 (start, distance chips 20–80 km or 5–30 on foot,
  climbing Any / Flat / Rolling / Hilly with its target, profile sheet, pass through) and L2 (up to three cards
  "{direction} · {town}", "{n} % new roads", the request line, TRY OTHER LOOPS turned 22.5°). A point is picked on the
  map with the screen's panels hidden and the map drawn, framed on the start for the loop's size. PROPOSE LOOPS reads
  "LOOKING… n/8"; a tap cancels. A card becomes the temp route (Phase 28) and opens in the preview. Leaving the
  screen cancels the search and releases the router.
- `Framework.nativeGetPlaceName` (the engine's `CityFinder`) names the loop's far side; `TzListRow.setIcon`;
  `TzRouteCard.showChevron`; N1 gets "Propose a loop".
- Three debug-only `ASSERT`s in the base turn code (`turns_generator.cpp` ×2, `car_directions.cpp`) fired on loop
  routes. A path through waypoints can turn back on its own segment or end inside a roundabout. Release builds skip
  them and already recover, so they are now warnings (D54).
- Checked on the emulator (D54): Clermont, 30 km Rolling Gravel: 3 loops in 47 s (29.9 / 34.9 / 35.4 km, 93–99 % new
  roads); through Royat: 2 loops in 68 s; cancel; preview of a loop (`shots/L1.png`, `L2.png`, `L2_via.png`,
  `L_pick.png`, `B5_loop.png`).
- Tests: `LoopPlannerTest` (8: three closed loops within the size, three directions, out-and-back rejected, retrace
  of an out-and-back ≈ 0.5, the try budget, cancel, the via point, direction names). 248 tests pass.
- Smoke test: all steps pass, no crashes. The emulator was set back to Road.

## Phase 30 — Stops along the route (FEAT-007)

- `Framework.nativePoisAlong` (2 km pieces of the line, boxes grown by the radius, country maps, points and areas,
  each once; taps, springs and water points marked not drinkable left out), `base/TzPois` (off the main thread,
  `slice`). `follow/Stop`, `follow/Stops` (placed on the route within the window, ≤ 1 km off, not behind the
  rider; free ride ≤ 5 km by straight distance; S2's detour 2 × off route × 1.3).
- Guide stops: added stops are routed at once (STOP_CHECK). A failure changes nothing. They are kept in route order;
  the leg starts 2 km before a stop, with the route quiet meanwhile (`FollowEngine.setQuiet`). It ends at 30 m ("You
  have reached …") or when the rider is 500 m past it on the route; then the usual rejoin, or a new path to the
  place. `Maneuver.STOP` (pin glyph, "Stop", the name as street; voice "In 200 meters, stop at …"). The journal
  keeps pending stops (`A` / `X` lines), and a restored ride gets them back.
- Screens: S1 `TzStopsScreen` (STOPS, opaque; chips Water / Repair; from R6, from B5's new pin button, free ride),
  S2 `TzStopScreen` (STOP, over the map: the route, dots from it with a hollow ring, `tz_pin_place` pin; AHEAD,
  DETOUR, EXTRA TIME; ADD STOP → "Recalculating…" → the ride, or the toast; REMOVE for an added one). R6 gets the
  ActionTile row (STOPS; SHARE and SAVER later). B5 shows S1's place framed with the route next to it.
- Checked on the emulator (D55): Aubière ride, S1 from R6 (water ahead 390 m … 5.2 km), S2 for a water point 260 m
  off route, ADD STOP (path 943 m) then ridden past: re-routed once, dropped 500 m on. The spring 3.2 km ahead was
  added with the route unchanged; its leg came at 2.4 km along (2 km before it), the stop was reached, and a 185 m
  leg led back onto the route. B5 pin and the place framing on the Aydat loop (`shots/R6_stops.png`, `S1.png`,
  `S1_route.png`, `S2.png`, `B5_stops.png`, `B5_stop_place.png`, `R2_after_stop.png`). Once, S2's pin and dots were
  drawn late (the emulator's engine lag).
- Tests: `StopsTest` (4), `GuideTest` (+5: far stop waits then leg and back, failure changes nothing, a near stop
  uses its check path, two in route order, ridden past / removed), `RideSessionTest` (+1: stops in the journal).
  258 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 31 — Bivouac places (FEAT-010)

- `nativePoisAlong` category 3: campsite (`tourism-camp_site`), hut (`tourism-wilderness_hut`, `alpine_hut`),
  shelter (`amenity-shelter` and subtypes, but not `public_transport` and not one that is also a bus stop, bus
  station, halt or platform). Also returns `FMD_OPEN_HOURS` and flags: fee (`fee-yes` / `fee-no`), private
  (`hwtag-private`). `Stop` gains `hours` and `flags`.
- `follow/Bivouac` (pure): VERIFIED only for a campsite or hut that is not private; restrictions in order private,
  hours, fee / no fee. `TzStopKinds.bindBadge` (Components v2 Badge: VERIFIED surface3 / text1 / check; NOT
  CONFIRMED amberBg / amber / warning; 22dp, radius 11, 11sp bold) and `bivouacNote` ("Open May-Oct · Fee"; not
  confirmed: exactly "No source says sleeping here is allowed.").
- S1: the Bivouac chip, the badge under the subtitle, the note lines, and the footer "Verified means the map marks it
  as a campsite or hut. Rules can change: check signs on site." S2: badge and limits above the numbers; ADD STOP as
  in Phase 30. B2: the Bivouac chip replaces Shelter (third, as on the board). It lists the same places around the
  rider within 5 km, or 20 km when there are none, with S1's rows; a tap opens the route there (B5).
- On a free ride, S2 frames the rider and the place together.
- Checked on the emulator (D56): Aydat loop › S1 › Bivouac, only real shelters, all NOT CONFIRMED, with the footer.
  The first try listed the station's platform shelters ("Quai A-C", "Busstop for …"), now left out. B2 at Lac
  d'Aydat: Domaine du lac, Camping du Lac d'Aydat (Fee), La Clairière, Camping Les volcans (Open Jun-Sep · Fee)
  VERIFIED; Gite de Montlosier (hut) VERIFIED; Observatoire and an unnamed shelter NOT CONFIRMED. Free ride S1 and
  S2 with the badge (`shots/S1_bivouac.png`, `B2_bivouac.png`, `S2_bivouac.png`).
- Tests: `BivouacTest` (3: type to badge, private overrides, restrictions). 261 tests pass.
- Smoke test: all steps pass, no crashes (B2 shows the Bivouac chip).

## Phase 32 — Direction tones and warnings (FEAT-008)

- `ride/TonePattern` (pure): the ten patterns of the plan's table (sine notes, 40 ms apart; 90 ms for OFF_ROUTE),
  16-bit mono PCM at 44.1 kHz, 12 ms linear fades, warnings with 30 % of the third harmonic, peak 0.8 of full scale.
  `ride/ToneRules` (pure): turn soon once, turn now twice 250 ms apart, ARRIVE for arrived / joined / a stop,
  roundabout then its side when the angle is known, LEAVE_BIKE as STRAIGHT then ARRIVE, off route once per episode,
  wrong way, GPS lost after 20 s without a fix (once per loss) and GPS_BACK after 5 s of fixes.
- `ride/TzTones`: one static `AudioTrack` per sound (the patterns mixed at their times), usage navigation guidance /
  sonification, the built-in speaker preferred (Android 6+). It is released when the sound ends; buffers are built
  once. `ride/TzAudioFocus` is shared by the tones and `TzVoice` (one ducking session). The words wait for the tone
  (`playSilentUtterance`).
- `TzRideService`: tones on every follow event and on stops; the GPS check is one delayed message moved on by each
  fix (none while paused); `gpsLost()` / `lastFixMs()` for the screen. Prefs `tones`, `warnOffRoute`, `warnGps`.
- UI: R6 Turn tones toggle (free-ride subtitle "A short tone for warnings"). The More row is now Sounds ("Tones +
  voice" / "Tones" / "Voice" / "Off") and opens M1 `TzSoundsScreen`: four toggles in two groups; Spoken directions'
  text opens the system voice settings, its switch toggles; six `TzToneTile`s; the caption. The NO GPS turn card
  (gps_off glyph, the time since the last fix, "Last turn tones may be late"); on a free ride it replaces the pill's
  row.
- Fixed on the way: leaving a preview while its surfaces were still read could run a reframe on a detached screen
  (crash in the smoke test's last step); the runnable is now cancelled in `onDestroyView` and guarded.
- Checked on the emulator (D57): M1 matches the board; each tile takes audio focus for its length (0.5 s LEFT,
  0.95 s GPS LOST); the emulator has no speaker to listen to. Aubière ride: [RIGHT, RIGHT] / [LEFT, LEFT] at each
  turn's 50 m, one OFF_ROUTE per detour in the smoke test. Location switched off: GPS_LOST once at 20 s and the NO
  GPS card counting; GPS_BACK 6 s after fixes returned (`shots/M1.png`, `R6_tones.png`, `R2_no_gps.png`).
- Tests: `TzTonesTest` (4), `ToneRulesTest` (7). 272 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 33 — Personal ride-time estimate (FEAT-009)

- `ride/RideEstimator` (pure): effort-km (km + 0.8 per 100 m) at the median effort pace of the last 12 bicycle rides
  of at least 5 km, or only the profile's own when it has 3. On foot: the walking or hiking formula times the median
  moving/formula ratio (rides of at least 2 km). Stops: the median (wall clock / moving − 1), 0–35 %. Fewer than 3
  usable rides: Road 20 / Gravel 16 effort-km/h, factor 1, stops 10 %, basis 0. No altitudes: effort = distance.
- `TzRideService.estimate` is the only call site. It runs when the route is first known (after `prepare()` for a
  track, with the previewed path for a place), behind `EstimateOnce`. The past rides are read off the main thread.
  The result goes to `RideSession` (`estimateS`, `estimateBasis`) and the journal (`E,{s},{basis}`); a restored ride
  keeps it.
- R6: the Estimate row under the header ("ESTIMATED RIDE TIME", "from your last n rides" / "typical pace", H:MM),
  hidden on a free ride. The planner's TIME uses the same estimator (past rides read once when P1 opens).
- Checked on the emulator (D58): Aubière ride, "estimate 1351 s from 0 rides" (the emulator's rides are all under
  5 km), R6 "typical pace · 0:23" (`shots/R6_estimate.png`).
- Tests: `RideEstimatorTest` (7: median, pooling rule, stops clamp, typical pace, foot factor, no-elevation route,
  exactly once). 279 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 34 — Share my location (FEAT-014)

- `ride/ShareText` (pure): the message in three wordings (check-in, low battery with its %, I need help), the
  coordinates with 5 decimals in US format and ± accuracy, "Near {street, town}" when known, the OpenStreetMap link.
  A fix older than 2 min adds "(position from {time}, may be out of date)"; with no fix there are no coordinates and
  no link. Age classes: fresh ≤ 30 s, recent ≤ 2 min, stale, none.
- `screens/TzShareSheet` (E1): the segmented control (equal halves; a second tap on the second option switches to "I
  need help" and back: `TzSegmented.setFill`, `setReselectListener`, `setOptionText`). It has the editable message
  (not rewritten once the rider edits it), the fix and battery line with its dot (mint fresh, grey recent, amber
  stale or none), COPY ("Copied"), SHARE (`ACTION_SEND` text/plain through the chooser; hidden when no app takes
  it, with a `<queries>` entry for Android 11+), and the caption.
- The position is the ride's last accepted fix (`TzRideService.lastGoodFix`); no new fix is asked for. Battery from
  `BatteryManager` when the sheet opens. The place: one lookup off the main thread, street within 50 m plus the
  town (`nativeGetPlaceName`, now safe from any thread).
- R6: the SHARE tile in the second third.
- Checked on the emulator (D59): from a ride, E1 with a fresh fix; Low battery (100 %), then "I need help"; SHARE
  opens Android's sheet with the text; Back returns to the ride. The place took 569 ms on the emulator ("Mail
  Vasarely, Aubière"). A restored ride kept its estimate (`shots/E1.png`, `E1_help.png`).
- Tests: `TzShareTest` (5: fresh with place, both other wordings without, stale, no fix, coordinates under a French
  locale). 284 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 35 — Extreme battery saver (FEAT-011)

- `ride/SaverPolicy` (pure): how often the GPS reports. Normal 1 s (batched 15 s with the screen off when nothing
  speaks). Saver, guided: 3 s (batched 9 s with the screen off) more than 600 m from the next maneuver on the route;
  1 s and unbatched within 600 m, off route, wrong way or not placed yet. Saver, free ride: 3 s, batched 15 s. Chosen
  after every fix and on every change of state (`TzLocationSource.setInterval`).
- `TzRideService.setSaver`: no voice (engine shut down), no rerouting (`Guide.setRerouting`; off route only warns),
  no barometer (altitude from the GPS), tones forced on, the notification only on state changes. Outside the saver,
  tones now keep fixes unbatched like the voice (they could have come up to 15 s late with the voice off).
- The flag is in the session and the journal (`Z` line): a restored ride comes back in the saver.
- X1 `screens/TzSaverSheet` from R6's SAVER tile: what keeps working and what pauses, TURN ON. X2
  `ui/TzSaverView`: a black layer over the ride screen (`tz_saver_*` colours, brightness 0.04, keep-awake cleared,
  map rendering off, the tick stopped, pages and sheet hidden), the maneuver, its distance, what's left, the clock and
  battery, SHARE and EXIT SAVER. It redraws only when the maneuver, its distance band, a warning or the minute
  changes. The compass is held off (`TzActivity.pauseCompass`).
- Checked on the emulator (D60): X1 and X2 on a guided ride, tones through turns in the saver, 3 s request with the
  screen off, EXIT SAVER restores the map, page and 1 Hz at once (`shots/X1.png`, `X2.png`). Measured with
  `tools/trackzero/saver_measure.sh` (TZ_BASELINE "Battery saver"): X2 0.7 % of a core against 103.7 % for the map
  page, no barometer, no wake lock.
- Tests: `SaverPolicyTest` (4), `SaverTonesTest` (2: 20 turns at 25 and 40 km/h, every "turn now" 20–80 m before
  its turn), `GuideTest` (+2, rerouting off and back on), `RideStatsTest` (+1, 3 s fixes), `RideSessionTest` (+1,
  the journaled flag). 294 tests pass.
- Smoke test: all steps pass, no crashes.

## Phase 36 — Light appearance (FEAT-015)

- Colours: `values/tz_colors.xml` is Light (Appendix B), `values-night/tz_colors.xml` is Dark, moved unchanged; the
  saver's colours are defined once in `values/`. `TzTheme` is DayNight. `tz_light_bars` (bool) gives the splash,
  first run and sheets dark bar icons in Light; `TzActivity` picks `SystemBarStyle.light` or `dark`.
- `TzPrefs.light` ("dark" by default). The night mode is set in `MwmApplication.onCreate` and by
  `ThemeSwitcher.restart`, which with TrackZero on takes the setting for the UI and the map (`MapAppearance.Light` /
  `Dark`). Never the system's setting or the time of day.
- More › Appearance (B7, above Units): one tap toggles; the activity is recreated and comes back to More with the
  map's camera unchanged.
- Map: `data/styles/trackzero/light/{colors,drape_colors}.mapcss` (Tokens v2 "Map style · Light"), imported by
  `cycling/light/style.mapcss`; `drules_proto_cycling_light.bin` in the SDK assets; the light rider dot is signal
  green with a white ring. Styles and symbols regenerated.
- Audit: no hard-coded colour left in TrackZero's Java, layouts or drawables; no colour cached statically (switching
  adds no listener or work). Contrast: `contrast.py --pairs` and the extra pairs: nothing LOW (D61).
- Found and fixed taking the shots: a debug routing assert on loops (`FixupCarTurns`, now a warning like D54's) and
  a ride-screen crash when R6 › STOPS replaced the screen while the sheet settled (D61).
- Shots: `shots/light/` and `shots/dark/`, 27 screens each (B1 B2 B3 B5 B5_place B7, R1 R2 R3/R3_leg R3W R4 R5 R6 R7
  R8, N1 N2 P1 P2 L1 L2 S1 S2 M1 E1 X1 X2), from the smoke test and `tools/trackzero/screens_v2.sh` (new) run in
  each appearance. Dark compared with Phase 35's smoke shots: identical apart from the new row and the moving map.
  States seen in Light: imminent turn (filled glyph well), wrong way, the off-route leg, paused, hold to end,
  toggles, selected chips and segments, favourite stars, the surface bar, X2 black.
- Tests: 294 pass (no new pure code).
- Smoke test: all steps pass in Dark and in Light, no crashes.

## Phase 37 — First-launch tutorial

- `tutorial/TutorialFlow` (pure): first screen (ride, tutorial or map), finish (done; to B1, or to More after a
  replay). `TzNavigator.Screen.TUTORIAL` (opaque, no island); `TzPrefs.tutorial`.
- `screens/TzTutorialScreen`: dots (`TzPageHandle.showDotsOnly`), SKIP, a four-step `ViewPager2`, NEXT / START. T1
  phone and nudging arrows; T2 phone with a rising sheet and the practice bar (`TzHoldButton.setMini`); T3 the tone
  grid (`tz_tone_grid` + `ToneGrid`, shared with M1); T4 `ui/TzTutorialMapView` and the profile pills.
- More › Show the tutorial (B7). `TzActivity` holds the location request while the tutorial shows. The map is
  hidden from accessibility under opaque screens.
- Checked on the emulator (D62): first start shows T1; SKIP from T2 goes to B1 and a cold start then opens B1;
  replay from More, Back and START both return to More with settings unchanged; the practice bar fills, empties and
  never swipes the pager; TalkBack's tree (compressed dump) has no map, "Step n of 4", SKIP, the pictures' sentences;
  animations off: T4 opens on its final frame; Dark and Light (`shots/dark/T1–T4`, `shots/light/T1–T4`).
- Tests: `TutorialFlowTest` (5). 299 tests pass.
- Smoke test: all steps pass, no crashes.
