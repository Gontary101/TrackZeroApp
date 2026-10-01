# TrackZero — Progress log

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
