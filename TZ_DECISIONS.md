# TrackZero — Decisions

Choices not dictated by `TRACKZERO_BUILD_PLAN.md` / `design.html`, and every place we deliberately deviate from the
plan. Newest at the bottom.

## D1 · Base tag `v2026.08.31-14` (Phase 0)
Latest general CoMaps release. Newer tags (`v2026.09.08-3-apple`, `v2026.09.23-2-car`) are iOS / Android-Auto
releases. The repo is cloned shallow (`--depth 1`) with shallow submodules; `git fetch --unshallow` if history is needed.

## D2 · Build host tooling without root (Phase 0)
`configure.sh` needs `optipng` and Qt6 Svg/Positioning dev packages for the map symbol generator. They were
extracted from the distro `.deb`s into `~/.local/opt/` (no sudo). Only affects regenerating map styles/symbols.

## D3 · Doto is a static Black instance; Dot styles don't set `textStyle=bold` (Phase 2)
Google Fonts ships Doto only as a variable font (`ROND`, `wght`). Android picks variable-font weights unreliably
before API 26 (minSdk is 21), so `res/font/doto.ttf` is a static instance cut with fontTools at `wght=900,
ROND=0` — the same instance design.html embeds. Because the file itself is Black, `TzText.Dot*` drop
`android:textStyle="bold"` (it could only trigger synthetic emboldening). Barlow/Barlow Condensed are the full
static TTFs from google/fonts (not the Latin-subset fallback).

## D4 · Permanent debug gallery instead of a throwaway screen (Phase 2)
`TzGalleryActivity` lives in the `debug` source set from Phase 2 (type + icons) and grows into the Phase 4
component gallery, so nothing has to be written twice and deleted.

## D5 · Extra icon `ic_tz_flag` (Phase 2)
Appendix A lists maneuver glyph `flag` (ARRIVE, R5 summit) but has no file for it. Drawn in the same style
(24 grid, stroke 2, round caps): pole + notched pennant; `_bold` copy at 2.6.

## D6 · TrackZero gets its own host activity instead of hiding MwmActivity's chrome (Phase 3)
Plan §3.2–3.3 adds `tz_root` to MwmActivity and hides ~10 base views, then patches every method that re-shows
them. MwmActivity is 2,600 lines with controllers that re-show views from many paths (routing, search, place page,
insets, track recording restore…), so that approach needs guards everywhere and breaks on every upstream merge.
The SDK already exposes the map as `MapView` + `MapController` (lifecycle observer), so `app.trackzero.base.TzActivity`
hosts just the map and `tz_root`. Base chrome is never created, so there is nothing to hide; the place page,
CoMaps TTS (only driven by MwmActivity's navigation UI) and `TrackRecorder` (only started from MwmActivity) are
unreachable by construction. Upstream edits shrink to: launch redirects in `DownloadResourcesLegacyActivity.showMap`
and `DownloaderAdapter` (via `TzActivity.mapActivityClass()`, honours `TzConfig.ENABLED`), the ThemeSwitcher hook
(D8), one JNI binding (D9) and the style files (D7). MwmActivity is untouched apart from Phase 1's portrait lock.
`TzActivity` reimplements only what a map host needs: map lifecycle, location-mode → LocationHelper start/stop,
permission request, place-page suppression, units init, country "explore" intent.

## D7 · Map style: CoMaps cycling dark + TrackZero palette (Phase 3)
Closest to B1 is the cycling style (cycleways emphasised) in dark. CoMaps Android only ships default/outdoors
styles, so `drules_proto_cycling_dark.bin` is added to the SDK assets (symlink into `data/`, like the others).
Palette (§3.4.2) without touching base colour files: `data/styles/trackzero/dark/colors.mapcss` redefines the
colour variables after `default/dark/colors.mapcss` (kothic: last definition wins), and
`data/styles/trackzero/dark/drape_colors.mapcss` replaces the default drape colours (kothic only reads the
first `colors{}` block) — mint rider arrow/accuracy/selection/bicycle route/default track, ruler text in text2.
`cycling/dark/style.mapcss` imports both. Tracks/paths (not in the token list) use `#26292D`, between minor and
major roads; all labels use the single label grey. The rider dot `default/dark/symbols/current-position.svg` is
mint with a black ring (only the dark skin is used). Regenerate with `tools/unix/generate_drules.sh` /
`generate_symbols.sh` (or `configure.sh`).

## D8 · ThemeSwitcher is dark-only when TrackZero is enabled (Phase 3)
`MwmApplication` runs `ThemeSwitcher.restart()` at startup, which would switch the map to the light style by
day. Root fix in `ThemeSwitcher.restart`: with the flag on, always NIGHT and always apply `MapAppearance.Dark`
(stock `setTheme` skips the map appearance on the call that recreates the activity, which let the cycling mode be
persisted as `MapStyleCyclingLight`, a style we don't ship → crash at init). TzActivity applies dark before
`MapMode.set(Cycling)`, and only once the engine exists (`MapMode.set` touches engine-backed managers).

## D9 · `TzMap` API: visible area + `nativeShowRect` (Phase 3)
CoMaps has no Java "show lat/lon rect", but C++ `Framework::ShowRect(rect, maxScale, anim, useVisibleViewport)`
exists; one JNI binding `Framework.nativeShowRect` exposes it. Instead of a `bottomPaddingPx` argument, screens
declare the map area their UI doesn't cover with `TzMap.setVisibleArea(topPx, bottomPx)`
(`nativeSetVisibleRect`): `showRect` frames inside it, and the core also centres the rider arrow inside it, which
is what the ride screens need (rider between turn card and stats island). `setTouchEnabled` is dropped: the
ride page consumes touches itself (§3.6), nothing else needs it. `setRenderingEnabled` gates the map's
pause/resume together with the activity lifecycle (map runs only when resumed AND wanted), so returning from
background onto an opaque screen doesn't resume drawing.

## D10 · Follow mode is driven by mode-change events (Phase 3)
The core only offers "switch to next mode" (async). `TzMap.followRider(headingUp)` records the requested mode
and advances one step per `onMyPositionModeChanged` until reached; a user pan (NOT_FOLLOW not caused by us)
cancels the request. FOLLOW→FOLLOW_AND_ROTATE is refused by the core until a heading exists, so callers re-request.

## D11 · Component implementation choices (Phase 4)
- Extra tokens the plan's token files don't list but the Components board specifies live in
  `tz_dimens_components.xml`, `tz_styles.xml`, `tz_integers.xml` and three colours appended to `tz_colors.xml`
  (`tz_grid #1D1F22`, `tz_amber_unit #C9A46A`, `tz_attribution #6B7076`), so the plan's files stay recognisable.
- `TzMetricView` (number + half-size unit sharing a baseline) implements §X3's "number and unit in separate
  TextViews" once; StatsIsland, TurnCard, RouteCard and the ride grids reuse it.
- Buttons are `MaterialButton` styles (`TzButton.Primary/Secondary/Danger/Primary.Resume`): `iconGravity=textStart`
  centres icon + label like the board; press scale is a state-list animator (90 ms, 0.96).
- `TzHoldButton` measures the hold on the frame clock (`SystemClock.uptimeMillis` + `postOnAnimation`), not a
  ValueAnimator: with system animations disabled an animator completes instantly, which would turn "hold 1.5 s to
  end the ride" into a tap. Screen readers get a long-click action instead.
- Follow value types (`follow.Maneuver`, `follow.TurnState`) are defined now so the TurnCard binds its real
  input; maneuver phrases are string resources (shared with voice in Phase 10).
- Motion honours "Remove animations" through `TzMotion.enabled()` (reads the global animator scale; works below
  API 26 where `ValueAnimator.areAnimatorsEnabled()` doesn't exist).
- `TzElevationProfileView`, `TzSilhouetteView`, `geo.Geo`, `geo.DouglasPeucker` (+ tests) were written by a codex
  worker from a precise spec and reviewed/adjusted (XML `tzCompact` attribute, motion check).

## D12 · The navigator owns the bottom island (Phase 5)
The Flow board says switching Map/Routes/Search/More is a "crossfade 150 ms, island stays". A per-screen island
would fade out and back in with every tab switch, so `TzNavigator` adds one `TzBottomIsland` above the screen
container and shows/hides it per screen (`Screen.islandTab`); HISTORY keeps the island with MORE active. The
navigator also decides map rendering from `Screen.opaque` (RIDE manages its pages itself), which avoids the
resume-then-pause flicker of doing it in each fragment's onResume/onPause.

## D13 · Page-title screens start 16dp under the status bar (Phase 5)
B3/B4/B7 boards place the Doto title at y=64 on a 48dp status bar (padding 64); map overlays (B1/B5/R*) use
status + 8 as in §X2. Opaque list screens therefore use `top + tz_space_16`.

## D14 · Remaining base-app UI will be replaced (Phase 5 → Phase 13)
The plan reuses CoMaps' DownloaderActivity for Offline maps, and the first launch still shows CoMaps' splash and
"Download the world overview map" screen. They are CoMaps' green Material UI, which the user does not want
anywhere in TrackZero. Phase 13 adds TrackZero-styled offline-maps and first-run screens built on the SDK's
`MapManager` API, and the base screens become unreachable.

## D15 · GPX parsing with SAX instead of XmlPullParser (Phase 6)
Android's `XmlPullParser` is a stub in plain JVM unit tests, so the parser would need Robolectric or an extra
library to be tested. `javax.xml.parsers` SAX exists on Android and the JVM, so `GpxParser` stays pure and is
tested without new dependencies. External entities/DTDs are disabled. Measured on the user's 20 routes: distances
within 0.4 % of the lengths in their file names; the 4.5 MB / 53,696-point traverse parses in 168 ms (JVM).

## D16 · Route index stores the card silhouette (Phase 6)
Each `routes.json` entry carries `"sil"`: ≤ 200 Douglas–Peucker points (5-decimal lat/lon) computed at import, so
the library never parses GPX files to draw cards. All library IO runs on one background thread (`TzIo`), which
also serialises the atomic index writes without locks.

## D17 · System widgets follow the mint rule; dialogs are TrackZero-styled (Phase 6)
`TzTheme` sets `colorPrimary/colorAccent` to text1 (mint is only for live data and start CTAs, §X1), disables
dark-theme elevation overlays (they lighten surfaces), and `ThemeOverlay.TrackZero.Dialog` gives
MaterialAlertDialogs surface1, 28dp corners and Barlow type; destructive confirmations use the danger colour.
Recent with no rides shows "Routes you ride in the next 30 days show up here." (plan only defines Favorites).
GPX "Open with / Share to" reuses the base manifest's existing GPX filters on SplashActivity (no duplicates);
the intent reaches TzActivity, which imports it. (Corrected in D34: the SEND filter needed octet-stream.)

## D18 · The route line is an engine subroute, not a bookmark import (Phase 7)
The plan imports the GPX into a hidden bookmark category. That persists data into the base app's bookmark store
(visible in base lists, synced into its KML files) and bookmark tracks can't be styled like the design. Instead
`Framework.nativeShowTrackLine` hands drape a `df::Subroute` built from the simplified line: nothing persists and
`TzRouteLayer.hide()` removes it. To match the boards (5.5dp mint line over a 16dp glow, 13dp black start disc with
a 3dp mint ring and 4.5dp dot):
- new `df::RouteType::Track` with a constant 8dp half-width table; the line subroute is capped to 5.5dp
  (`m_maxPixelWidth`), the glow subroute below it uses the full width;
- `TrackLine` / `TrackGlow` / `TrackMarkerCenter` colours in the TrackZero drape palette. The glow is opaque
  (#1E2C21 = mint 18 % on the black ground): GPX lines are dense, overlapping segments of a translucent 16dp line
  blend once per overlap and show streaks;
- markers in one subroute get their own depth slots so the ring is three stacked circles (base change: one loop
  in `RouteShape::PrepareMarkersGeometry`; one marker behaves exactly as before);
- the subroute's fake head/tail are disabled (the default 0 tail paints the whole line in the grey "fake" colour).
Engine bug fixed at the source: `GenerateJoinNormals` took `acos` of a float dot product that comes out just below
-1 at an exact U-turn (every out-and-back GPX), producing NaN → INT_MIN segments → `std::length_error` abort.
The cosine is clamped.

## D19 · Route geometry is computed once at import and cached (Phase 7)
`RouteGeometry` (2 m Douglas–Peucker line, ≤ 600-point profile, bounds) is written to `routes/<id>.geo` (versioned
binary, ~420 KB for the 53k-point traverse) at import; opening a route reads it instead of re-parsing the GPX.
Measured on the emulator, non-debuggable build: that file imports in 1.1–1.3 s (parse ≈ 1.0 s, one-time) and its
preview loads in 12 ms instead of a full re-parse. Missing / old-format caches are rebuilt from the GPX on first open.
The card silhouette is now simplified from the cached line instead of the raw track.

## D20 · Framing padding lives in `TzMap.showRect` (Phase 7)
The engine fits a rect edge to edge into the visible viewport. Rather than shrinking the visible area (which also
positions the rider), `showRect(…, paddingPx)` grows the rect in mercator so the original sits `paddingPx` inside
the visible area on every side. The preview frames between the top buttons and the card with 24dp padding and
stops following the rider first (the preview is about the route; the follow animation would also fight the rect
animation).

## D21 · Ride recording: own GPS subscription, no wake lock, monotonic clock (Phase 8)
- `GpsLocationSource` subscribes to the GPS provider itself (plan §8.1) instead of riding on CoMaps'
  `LocationHelper`: the app stops `LocationHelper` in the background and when the rider turns location off, so
  using it would need base hooks; a second subscription to the same chip costs nothing extra.
- No `PARTIAL_WAKE_LOCK` for real GPS (plan §8.2 asks for one while RECORDING). Fixes arrive once a second and
  wake the process; journal flushes and notification updates are driven by fixes, not timers, so nothing needs
  the CPU between fixes. Only the timer-driven `ReplayLocationSource` (debug) holds a wake lock, and replay rides
  don't use the barometer (it knows nothing about the replayed track: on the emulator its constant pressure
  pinned the altitude and gain stayed 0).
- Fixes carry both UTC (`timeMs`, for the GPX) and `elapsedMs` (`elapsedRealtime`): every duration is measured on
  the monotonic clock, so GPS-vs-system clock offsets and clock changes can't produce negative or doubled time.
- Altitude prefers `Location.getMslAltitudeMeters()` (API 34+) — GPX elevations are MSL, while
  `getAltitude()` is above the ellipsoid (~50 m higher in France).

## D22 · Auto-pause dates the stop from when the rider slowed down (Phase 8)
Speed below 3 km/h for 5 s → AUTO_PAUSED (plan); the 5 s of waiting are taken back out of the moving time, so
each traffic light doesn't add 5 s to TIME and lower the average. Thresholds use the displayed (3-fix average)
speed. Turning auto-pause off while AUTO_PAUSED resumes recording.

## D23 · Crash journal = points + totals (Phase 8)
`current.partial` holds a header (start time, route, name), the recorded points with segment breaks, and a
totals line (distance, moving time, gain, max speed, max grade) appended at every flush (30 s and every state
change). A restored ride keeps its numbers instead of recomputing them from points, and a line cut short by the
crash is ignored. The ride restores PAUSED from `START_STICKY` restarts and, after a reboot, from app start.
Gain uses one `GainCounter` (3 m hysteresis) shared with `RouteStats`.

## D24 · Ride map camera: zoom as a scale gesture, calls safe before the engine (Phase 9)
- Auto-zoom (z17 / z16 / z15 by speed band, 5 s stable) uses a new SDK `Framework.nativeScale(factor)` — the same
  scale event as the zoom buttons. `nativeZoomToPoint` sends a set-centre event, which the core treats as the user
  moving the map and drops follow mode. A jump of more than 2 levels is applied without animation (follow updates
  cut long animations short) and the screen re-checks the real zoom on the next tick.
- The app can open straight on a ride (restore), before the map engine exists: `TzMap` drops camera calls until
  then and applies a pending follow request (and `TzRouteLayer` a pending line) in `onEngineReady()`.
- Rider at 67 % of the height: the visible area starts at 34 % of the screen (the engine centres the rider in it).
- CoMaps' compass widget is not set up (SDK `Map.setCompassEnabled(false)`); TrackZero's map UI has no compass.

## D25 · Ride and summary details (Phase 9)
- Times (R8 "09:12 – 12:41", ETA) follow the system 12/24 h setting instead of always 24 h (plan §9.8): the
  board shows a 24 h locale; a US phone set to 12 h should read 8:06 PM.
- R4 hero is sized in dp (208), not sp: it already fills the width and would overflow with a larger font scale.
  `TzTextSize.applyLineHeight` reproduces the board's CSS line-height 0.85 (KM/H 10dp under the digits).
- R7 on the map page: the RESUME button takes the stats island's place and the island moves up above it. The
  floating RESUME fades out as the sheet opens (the sheet has its own RESUME RIDE).
- EXPORT GPX shares a copy named after the ride ("Clermont-Ferrand (70.7km).gpx") from the cache, not the internal
  UUID file name. Rides store a 200-point silhouette in rides.json like routes; R8 draws it with a start ring.
- R2 until Phase 10: the turn card shows "Continue straight" + route name with remaining = route length − ridden.

## D26 · Testing rides on the emulator (Phase 9)
DEBUG_REPLAY only feeds the ride recorder; the map's rider keeps the device position. For UI testing,
`tools/trackzero/emu_gps_play.py route.gpx [km/h]` drives the emulator GPS itself (`adb emu geo fix` with speed —
this emulator ignores `geo nmea`), so the map and the recorder see the same moving rider through the real GPS path.
The emulator's constant barometer pins the ride altitude after calibration; altitude/gain checks need replay mode.

## D27 · Turn-by-turn on a GPX track (Phase 10)
- The engine (`follow/`) is pure Java and runs in the ride service, so voice works with the screen off; the ride
  screen only reads it. Route and maneuvers are prepared off the main thread at ride start (and on restore).
- **Turns come from the map, not only the shape.** A track's bends cannot tell a crossroads from a curve: on a
  winding road the plan's geometry rules gave 271 "maneuvers" in 70 km. The SDK gains `nativeRoadJunctions`: for
  each candidate, are there 3+ road arms within 12 m (a way ending at a node counts 1, passing through it 2, its own
  vertex or not). Bends away from junctions are dropped, except a U-turn where the track comes back along itself
  (an out-and-back, not a hairpin). Without a downloaded map, every bend stays (the plan's behaviour).
- Bends closer than 20 m are one maneuver measured across the group, so a jog across a junction cancels out.
- Street names (plan §10.1 step 5): `nativeGetAddress` only finds buildings under a point, so the SDK gains
  `nativeGetStreetName` (closest named street within 20 m, core reverse geocoder). The street after a turn is the one
  found at both 30 m and 80 m into the next leg (one probe named the road being left); none → the line is hidden.
- Snapping: a search window around the last place on the route, and a 15 m handicap for segments running against
  the rider's course (> 120°): the two legs of an out-and-back lie on the same road. While off route the place stays
  where the rider left; only a rejoin within 20 m anywhere (a shortcut) moves it. The first snap takes the earliest
  of equally close places (a loop that ends at its start). Projection and distances use one sphere (`Geo`).
- R3 (off route): "Head back to the route" (no rerouting in v1), the route line dashed in 45 % mint without glow
  (pre-composited colour, see D18), ETA `--:--`, REMAINING `-- km`, haptic reject + "Off route".
- Voice: phrases are spoken in the language they are written in (`tz_voice_language`), never an English phrase in
  a French voice. "In 200 meters" is rounded like speech and follows the unit setting. Nothing while manually
  paused. No "in 200 m" for the arrival (only "You have arrived").
- Ride numbers fixed while testing (Phase 8 code): gain, grade and the elevation trace use the barometer with the
  first GPS offset held (`AltitudeFilter.relativeM`), so the 60 s calibration is not counted as climbing (22.7 %
  max grade on a flat ride); a hop between fixes faster than 150 km/h is a GPS jump, not distance; the shown speed
  drops to 0 when fixes stop for 5 s.

## D28 · Climbs (Phase 11)
- One distance axis: the route profile is measured along the simplified line the rider is snapped to (each GPX
  point placed on it in proportion), so TO SUMMIT and the rider dot agree with the follow engine even far into a
  long route (the raw GPX path is ~0.5 % longer). Geometry cache v2 adds the elevation every 50 m (climbs and the
  R5 profile) and the waypoints.
- Detection is the plan's (low → peak, closed by a 10 m drop; ≥ 30 m, ≥ 500 m, ≥ 3 %), but the start moves up to
  where the road rises at 3 % over 100 m: the lowest point can be kilometres of flat before the climb, which
  would dilute the average under 3 % and hide it (or report 7 % for an 8 % climb).
- Names: the map's peak or mountain pass within 300 m of the summit first (the design's rule; it names the place,
  "Col de Ceyssat"), else a GPX waypoint there, cleaned of list numbers ("1 — ") and ignoring route-planner markers
  ("via12", "from"); else CLIMB N. Long names shrink to fit (26 → 16 sp).
- GAINED comes from the route profile (rider's place on it minus the climb's start), consistent with the chart;
  SUMMIT IN uses the speed ridden on this climb, shown after a minute of it; AVG GRADE is the climb's.
- After the last climb: NO CLIMBS AHEAD over the full profile, island ALTITUDE · GAIN · GRADE (as a free ride).

## D29 · Search (Phase 12)
- Text results keep the search engine's order; only category results are sorted by distance. The plan and the
  board say "sorted by distance", but the engine already weighs distance against how well the name matches: sorted
  by distance, "puy de dome" listed twenty nearer roads named after the volcano before the volcano itself (the
  board's own sample is not in distance order either). Every result of a category is equally relevant.
- Chips run CoMaps category searches: the category's English name plus a space, `isCategory` (the core always
  understands English); "bike" matches both repair stations and bike shops. The chip's label fills the field.
- A result opens MAP through the core's ShowSearchResult: zoom chosen for the kind of place (a summit closer than
  a town) instead of a fixed z16, centred in the visible area, marked by the map selection (mint, D3). TzActivity
  normally drops any selection (no place page); it keeps the one a search result makes until the next tap.
- Result icons come from the classifier type (SDK `SearchEngine.nativeGetResultType`): summit/volcano/pass, water
  (taps, fountains, springs), café, bike; else a pin. Subtitles: type · detail (a summit's height) or the town;
  CoMaps' description repeats the type and uses "•" and "▲", which are dropped.
- Not done: the board's note "chip tap shows results on the map" and the bottom card with NAVIGATE HERE (routing is
  Phase 15).
- Debug builds search 2-3x slower: on the emulator "puy de dome" hit the engine's deadline before the summit was
  found; a non-debuggable build finds it first.

## D30 · More and history (Phase 13)
- Units: TrackZero's setting is the one source and drives CoMaps' too (scale bar, GPS chip accuracy). On first
  start it takes CoMaps' default for the phone's country instead of always metric (a US phone gets miles); after
  that TzActivity applies it to the map at every start and More applies changes at once.
- Offline maps detail lists the downloaded regions, not the world overview that comes with the app; the top level
  of "my maps" is countries partly downloaded, so their regions are listed ("Puy-de-Dôme · 73 MB", "+2" for more).
- Voice guidance shows the language the announcements are spoken in (the phrases' own, D27) and opens the system
  text-to-speech settings, where the voice is installed or chosen.
- History reuses the B3 card with the date in place of the route kind and no star; a ride opens R8, whose DONE and
  Back return to History. About shows the version without build flavor/type, credits, and the font licences from
  the bundled OFL texts.

## D31 · No CoMaps UI left (D14 done, Phase 14)
- Start: SplashActivity and the first-run download (DownloadResourcesLegacyActivity) keep their logic (permissions,
  core init, world map download, "Download Puy-de-Dôme?") and get TrackZero layouts and themes under
  `TzConfig.ENABLED` (layout ids unchanged). The system splash is black, not CoMaps' green. The custom map server
  button of the first run is hidden.
- Offline maps: `screens/TzMapsScreen` on `base/TzMaps` (SDK MapManager) replaces CoMaps' DownloaderActivity: maps on
  the phone, the map where the rider is, all countries → regions, download (with free-space check) / progress /
  stop / retry / update / delete. Nothing in TrackZero opens a CoMaps screen any more.
- The engine's 10 s "Map data © OpenStreetMap" label at start is off (SDK `Map.setCopyrightEnabled`): B1 and the ride
  map show the attribution themselves, and the engine hid the scale bar until its label had faded.
- The rider arrow keeps its black outline on every map (engine: only while CoMaps routing), as on the boards; it
  is what separates it from the mint route line.

## D32 · Battery: a hidden map really stops (Phase 14)
Measured on the emulator during a ride: R4 still used ~95 % of a core. CoMaps' pause (`Map.onPause`) only stops
presenting frames; the engine keeps rendering. TzActivity now also freezes the render loop
(`Map.setAccessibilityFreezeFrame`, the SDK's existing drape-engine disable) whenever the map is not shown, and
applies it again after surface re-creation. R4: 95 % → 30 %; the rest was the REC dot animating at 60 fps, now a
10-step pulse, stopped when its page is not shown: R4 ~10 %, R5 ~15 %. Map page ~120 % (emulator software GL).
GPS is 1 Hz only during rides (GpsLocationSource); outside rides CoMaps' own location behaviour.

## D33 · Accessibility, font size, small screens (Phase 14)
- Every icon-only control has a description; decorative icons are hidden from TalkBack.
- Touch targets ≥ 48dp in browse screens, ≥ 56dp on the ride: chips and segmented options draw their 44/40dp pill
  inset in a 48dp target.
- Largest font (200 %): numbers and the fixed-size labels (island tabs, stats island) are capped at 1.3×; list rows
  give the title its width and ellipsize the detail. Nothing overlaps.
- 360 × 640 dp: R4 numbers scale with the cell width, the grid shrinks to its rows, then the hero (down to 160dp),
  then cell padding; R5's profile gives way above its island; R8's track box shrinks so EXPORT / DONE stay on
  screen, button labels shrink to one line.
- "Remove animations": TrackZero's own animations check TzMotion; system ones (pager, sheet) follow the setting.

## D34 · Final checklist fixes (after Phase 14)
- **Share to TrackZero.** Android's MIME map has no `.gpx`: MediaStore and the Files app send GPX files as
  `application/octet-stream`, so the base SEND filter (GPX/KML types only) never offered TrackZero. The SEND
  filter is now GPX-only and claims `application/octet-stream`, `application/xml` and `text/xml` too (as the plan
  asked); TzImport already turns away files that are not GPX. KML/KMZ and SEND_MULTIPLE were dropped from it:
  TrackZero can't import those, so offering them only led to an error. "Open with" already had an octet-stream
  VIEW filter.
- **No "Head to start · 0 m" while the route loads.** The follow engine is built off-thread (seconds on a long
  route) and was published before it had seen a fix, so R2 showed its blank state. The service now keeps the last
  usable fix and places the rider with it the moment the engine is published; the turn card waits for
  `FollowEngine.placed()`, so a cold GPS shows the free-ride island instead of a made-up instruction.
- **R5 metrics fit their column.** 56sp in a third of the screen can't hold "14.9 km" or "+3,281 ft".
  `TzMetricView` got an opt-in `tzFitWidth`: number and unit shrink together, measured with the paint (no size
  flip-flop inside measure), the view keeps its full-size height and baseline so neighbours stay aligned and the
  profile card below does not jump.
- The engine's "V: Zoom | FPS" label seen on the emulator is compiled only into native debug builds (`DEBUG`).


## D35 · Map downloads on mobile data (first real phone)
On the Honor (LTE, no Wi-Fi) every map download showed "Download failed" at once. CoMaps' downloading policy
refuses cellular downloads (`IsDownloadingAllowed()` → immediate Failed, nothing logged in release) until the app
calls `nativeEnableDownloadOn3g()`. That consent lasts an hour and is CoMaps' own "Download over a cellular
network?" dialog, which TrackZero's maps screen (D14) had replaced without the consent step. The emulator counts as
Wi-Fi, so the checklist never saw it. Now `TzMaps.needsMobileDataConsent()` and `allowMobileData()` exist: on mobile
data the confirmation reads "Download X over mobile data?", and confirming grants it. Retry and update ask the same
way, since the consent may have lapsed. No setting: the question is asked when it matters.
Also, `TzMaps.downloaded()` listed a country whose regions had failed ("France · 94 maps") as a map on the phone.
Groups are now always opened into their regions.

## D36 · Heading-up really is heading-up (first real phone)
On the phone the route ride's map stayed north-up. There were two causes:
- **No compass reached the map.** MwmActivity forwarded CoMaps' compass readings (`Map.onCompassUpdated`) and
  TzActivity replaced it without doing so. Standing still, the core had no heading. CoMaps' location helper already
  runs the rotation-vector sensor while location is on, so TzActivity now forwards the readings (onStart to onStop)
  at no extra battery cost.
- **A refused request was never retried.** The core silently refuses FOLLOW → FOLLOW_AND_ROTATE until it has a
  heading. TzMap waited for a mode change that never came, so its request stayed stuck until the page was shown
  again. Every compass reading or fix with a bearing now retries a waiting request, at most once a second; a switch
  with no answer after 1 s counts as refused.
The emulator's fixed compass and replayed bearings hid both.

## D37 · Routing: CoMaps' bicycle router as a path service (Phase 15)
The plan suggests `RoutingController`. That session draws its own route, adds route marks, stops location follow,
zooms out to the whole route and runs its own off-route logic, which would take the ride away from TrackZero's
engine, card, voice and line. Instead `BicyclePath` (new JNI file) runs CoMaps' `IndexRouter` directly for a bicycle,
between two points, on a background thread, and returns only the path: its points, altitudes, and turns (CoMaps'
own announce list: direction, roundabout exit, street). Nothing is drawn and the map is left alone. One router is
kept for a ride, so its road graphs load once (seconds) rather than per path, and released at the ride's end.
- `follow/Guide` (pure, unit-tested) owns the route followed and a routed *leg* onto it:
  - a leg to the start when the track begins more than 200 m away;
  - after OFF_ROUTE, a leg back to the track's closest point 200 m to 2 km further on ("Recalculating…" meanwhile);
  - a leg left behind gets a new one; when the rider is back within 20 m of the track the leg goes away.
  - Requests are at least 10 s apart, and failures back off up to 5 min, so a ride without maps doesn't burn CPU.
- A **ride to a place** (search → card → NAVIGATE HERE → route preview → START RIDE) follows the routed path itself.
  Leaving it works the path out again from the rider. The journal keeps the destination, so a restored ride routes
  again from its first fix. The path's altitudes give its climbs (R5) the same way a GPX's do.
- CoMaps turns map onto the ManeuverGlyph set: roundabout exits use `ic_tz_roundabout` with the exit number under the
  glyph ("Take exit 2"; voice "At the roundabout, take exit 2"). The end of a leg is JOIN ("Join the route"). The
  router's street names are used as they are. CoMaps' TTS stays off.
- Route lines: the track (ring at its start), a place path (ring at the place), and the leg (no ring) as a second line.

## D38 · Battery pass (Phase 15)
Measured or read in the code, each without changing what the rider sees:
- **Map position once a second.** CoMaps polls location twice a second while the app is open, and every fix wakes
  the CPU and animates the map. It now matches the ride's 1 Hz.
- **No background GPS without a fix.** CoMaps kept GPS searching in the background while the map waited for a first
  fix (opening the app indoors and leaving kept GPS on indefinitely). TrackZero rides have their own service.
- **Compass.** D36 forwarded every reading, about 15 per second. In heading-up the engine turns and redraws the map
  for any change over 0.06°, so a still rider kept the GPU busy. Now only changes of 3° or more reach the map. The
  sensor sleeps at 4 m/s and up (the engine takes the heading from the GPS course there) and wakes below 3 m/s.
- **60 Hz during a ride.** The screen stays on for hours and the map moves at cycling speed; a 120 Hz panel costs
  noticeably more. The ride screen asks for the display's slowest mode of at least 59 Hz (browsing keeps the
  system's choice).
- **Screen off:**
  - Fixes are batched (15 s) when nothing is announced (free ride, or voice off); recording stays at 1 Hz. The batch
    is flushed when the screen comes on.
  - The notification isn't refreshed while nobody can see it; it refreshes when the screen comes on.
  - A pause the rider ends themselves (a café stop) stops GPS and the barometer altogether.
- **Ride screen:** one render a second (the tick only covers gaps between fixes), and text set only when it changes,
  since an unchanged `setText` still costs a layout pass.
- Routing: the router is kept for the ride, and paths are not retried sooner than 10 s (backoff on failure).

## D39 · Map state belongs to the screen that set it
`TzNavigator` replaces screens with reordering allowed, so the next screen sets up before the previous one's
`onDestroyView`. The preview's teardown (`TzRouteLayer.hide()`, `setVisibleArea(0, 0)`) then ran after the ride
screen had drawn its line and set its 67 % rider point, leaving R2 with no line and the rider centred. Visible area
and route lines now carry their owner, and a screen going away clears only what it still owns.

## D40 · Tracing and measuring (Phase 16, plan v2)
- `TzTrace` keeps the last 2,000 events in memory; nothing is written until someone shares the trace (About). Every
  call site checks `TzTrace.on()` first, so with tracing off a trace point is one static read and no string is
  built. Release builds trace only after 7 quick taps on the version in About (the switch is kept in `tz_prefs`);
  the same taps show the hidden tools group, which holds "Share trace" (and the later debug switches of the plan).
- Debug builds also copy each event to logcat (tag `TzTrace`), so test scripts read traces with `adb logcat` instead
  of going through the share sheet.
- The map's fixes are traced in TzActivity's location listener (the one the compass logic already has), not in the
  SDK: it sees exactly what LocationHelper delivers, with provider, accuracy and age.
- Measurements use a `-PtzMeasure` Gradle property instead of editing the debug build type by hand: the debug
  package (with its downloaded maps) built non-debuggable, so the Java code runs at release speed. The native code
  is still the debug build; Phase 38 measures the same way, so the comparison holds. The runs are scripted
  (`tools/trackzero/measure.sh`, `baseline_run.sh`) so they can be repeated exactly.
- Appendix D is scripted too (`tools/trackzero/smoke.sh`): it drives the emulator and takes a screenshot per step;
  the screenshots are the check.

## D41 · Back closes the place card (Phase 16 smoke test)
Appendix D step 12 ends "Back twice → B1", but the second Back left the app: B1 let the system handle Back even with
a search result's card open. Back on B1 now closes the card and its pin first (`TzScreen.handlesBack`, the
navigator enables its Back callback on MAP only while the card is shown), through the existing
`TzSearch.clearShown()`.

## D42 · BUG-004: what made the ride start unstable, and the fixes (Phase 17)
Found with `TzTrace` on the emulator (`tools/trackzero/ride_start_check.sh`: cold start, B3 → route → START RIDE 2 s
after the preview appears, GPS moving): 3 of 3 starts failed before the fix. The causes, with their evidence:
- **Zoom added up (H3, confirmed).** The first auto-zoom scaled the map by 2^(16 − 13) while the follow animation was
  still flying in from the preview; one second later the "settle" read the zoom mid-animation (still 13) and scaled by
  8 again: the trace reads `setZoom current=13`, `current=13`, then `current=19`, `current=19`, then `current=13`. That
  is the map zooming in and out. Fix: the ride's first camera is one engine command that jumps to the rider at the zoom
  for the speed (no animation, so follow updates cannot cut it short) and follows. Every later zoom is a *level*
  given to the same command (animated for one or two levels), not a scale factor computed from a zoom read while the
  map may still be moving: the first version of this fix kept the factor and waited for the zoom to look settled, and
  1 start in 18 still went 13 -> 19 when the engine applied the camera after the second reading. A level asked twice
  is the same level. `follow/RideZoom` asks again only if the map is not at its level 3 s after asking (a cut-short
  animation). `Framework.nativeScale` (D24) is gone.
- **Follow toggled (not in the plan, confirmed).** TrackZero drove follow mode with the engine's only public command,
  "switch to the next mode" (D10), which is a toggle: NOT_FOLLOW → FOLLOW → FOLLOW_AND_ROTATE → FOLLOW. The ride screen
  asks for follow twice at start (`onStart` and the pager's first page callback); the engine answers about a second
  later, so two switches were in flight and the second turned heading-up back off and reset the view north-up:
  `onModeChanged FOLLOW_AND_ROTATE` then `FOLLOW causedByUs=false` with no touch. In another start no answer came for
  3.7 s and the request looked refused. Fix at the source: a new engine command `FollowMyPosition(rotate, zoom)`
  (drape `MyPositionController::FollowPosition`, a `ChangeMyPositionModeMessage` type, `Framework.nativeFollowMyPosition`)
  sets the mode whatever it was, so it can be repeated; asked before there is a position, it is applied with the first
  one. The step machine, the "refused" timeout and the heading retries of D10 / D36 are gone.
- **A late NOT_FOLLOW (H2).** Not seen in the traces as a cause, but the new follow request needs a rule for it: a
  NOT_FOLLOW cancels the request only while a finger is on the screen (or 300 ms after, a fling). Without a touch it
  answers an older command, and if the engine is really not following the request is sent again (`FollowRequest`).
- **The line went missing (H4, confirmed).** The preview removed its line in `onDestroyView` (+930 ms) and the ride
  screen drew its own only after reading the route's geometry (+2,033 ms): a second without a line, longer with a cold
  cache. Fix: START RIDE hands the preview's line to the ride (`TzRouteLayer.transfer` through `RIDE_START`), the ride
  screen adopts it, and showing the same geometry again only changes the owner.
- **The camera used the preview's area (found while fixing).** The ride screen sets its visible area (rider at 67 %)
  when it is laid out, about 0.7 s after `onStart`, after it had asked for follow: the engine placed the rider in the
  preview's area (34 %) first. The ride camera now goes out right after the ride's visible area, in the engine's
  message order.
- **Two positions (H1, confirmed in structure).** The trace shows the map fed by `fused` and the ride by `gps`, and
  `dumpsys location` two requests during a ride (TZ_BASELINE). The emulator has no network positions, so the hops the
  owner saw with mobile data cannot be reproduced here; with mobile data the fused provider reports network positions
  (hundreds of metres) until GPS locks, and from PENDING_POSITION the engine zooms to the accuracy circle. Fix: while a
  ride records, the map is fed the ride's accepted fixes (accuracy ≤ 30 m) through `LocationHelper.setExternalSource`
  / `pushLocation` (same path as a provider fix: core, saved location, listeners) and CoMaps' provider is stopped. Replay
  rides feed the map too (D26's split is gone). Field check in NEEDS OWNER.
- **Provider restarts (H5, ruled out).** `restartWithNewMode` is called on every mode change but returns at once when
  the interval is unchanged: no "update refresh interval" in any trace, and no map fix older than 1.1 s.
- **Emulator note.** The engine handles queued commands between frames, one per frame once a frame takes longer than
  60 ms. This emulator draws with SwiftShader (software GL on a busy host), so any queued command waits behind the
  tiles of the last camera move: CoMaps' own locate button took 4.7 s to answer on an idle B1, and the ride's follow
  1 to 10 s. An optimised engine build without AddressSanitizer did not change that (the GL is the bottleneck). Raising
  the follow command's priority was tried and dropped: high-priority messages are taken last-in-first-out and can
  overtake the visible area, so the rider was placed in the preview's area. The order is kept (the camera follows
  the area in the queue); the start check measures correctness with a longer window and the latency is a phone check
  (NEEDS OWNER N17).

## D43 · Immersive ride: touch targets stay above the system's gesture strip (Phase 18)
The plan sets the ride's bottom inset to 0 once the bars are hidden. Tested on the emulator (gesture and 3-button
navigation): with the bars hidden, touches in the bottom strip the system keeps for its gestures
(`mandatorySystemGestures`, 48dp here) are not reliably delivered. 4 of 4 taps on the page handle there were lost, and
the first hold on HOLD TO END did nothing. So the map and the pages still run to the bottom edge, but what the rider
touches (handle, RESUME, the controls sheet) sits above that strip (`TzNavigator.gestureInset()`: the larger of the
mandatory gesture inset and the navigation bar's height, read whether the bar shows or not). The top uses the status bar
height whether it shows or not, so a swipe that reveals the bars moves nothing.

Emulator note: the debug engine (AddressSanitizer, no optimisation) falls far behind on this emulator. Route line
changes were applied up to a minute late after an off-route detour, which looked like a line left on B1 after the ride.
The trace shows TrackZero's removals were sent in order; it is lag, not a lost line. Not chased further. Phone check N18.

## D44 · BUG-003: Material's sheet moved the handle into the island (Phase 19)
Measured on the emulator (view bounds from `dumpsys activity top`): the controls sheet's top sat 10 px above the
peek height TrackZero set. Material's `BottomSheetBehavior` raises the peek height to at least the system's mandatory
gesture inset plus 48dp unless `gestureInsetBottomIgnored` is set. TrackZero placed the stats island from its own peek
(handle + navigation bar inset), so on a gesture-navigation phone (mandatory inset about 48dp) the handle was pushed
about 70 px up into the island. The bind path (pages bound before the first insets) was not it: `onInsets` laid them
out again. Fix: the sheet ignores that adjustment, and one function (`applyBottomLayout`, arithmetic in
`ui/RideBottomLayout`) places the handle, islands, RESUME, the speed page and the sheet's content from one base. It runs
on insets, on page bind and on resize. The base is the gesture strip of D43, not 0. Handle 34dp, 4dp to the island,
grabber-to-dots gap 6dp. Also new (Flow board): a fast upward swipe starting in the bottom 120dp opens the sheet. It is
read from the activity's touch stream, so the pager keeps its horizontal swipes.

## D45 · Route line for daylight (Phase 20, FEAT-005)
Research and table in `TZ_RESEARCH_ROUTE_VISIBILITY.md`. In sun every line colour ends between 1.2:1 and 2.1:1 against
the dark map, ranked by luminance only, so the gains are width and a hard edge, not hue. Shipped **B**: an 8dp mint
line on a 12dp black casing; the 16dp glow is gone (at 18 % mint it is below the screen's reflection in sun). The four
candidates (A as before, B, C brighter, D wider) are one native function with a style argument, switchable in About's
hidden tools for the owner's outdoor check. When the owner has chosen, the switch and the losing styles go.

Departures from the plan's steps:
- The width table `kRouteHalfWidthInPixelTrack` stays 8 (the widest candidate's half-width) and each subroute is capped
  by `m_maxPixelWidth`. Setting it to 6 now would break A and D; it can be set to B's 6 when the switch goes.
- The colours `TrackCasing` (#000000) and, for C only, `TrackLineBright` are added; `TrackGlow` stays until the
  choice is made.
- The ring keeps its on-screen size: marker scales are relative to the line's half-width, so they are computed from
  each style's line width.

## D46 · Direction chevrons as route mesh, and wrong way (Phase 21, BUG-002)
**Chevrons: approach B (the plan's fallback), chosen after reading the engine's turn arrows, not after building A.**
The turn arrows (`CacheRouteArrows`) are textured quads sized from the line's half-width times
`kArrowHeightFactor`, i.e. wider than the line. Their borders are worked out again on the frontend at every zoom
change (`CalculateArrowBorders` → a recache on the backend), and they are merged where they overlap. A "›" inside an
8dp line needs a new texture region, a different size factor and Track-only exceptions in that path. Each zoom step would
also recache, which goes against the battery rule. Instead each chevron is two strokes added to the line's own mesh
when it is cached (`RouteShape::AddChevrons`), with the colour `TrackArrow` as a per-vertex colour. The route shader
already supports per-vertex colours (traffic): `RouteRenderer` turns them on for a line with chevrons. Nothing is
recomputed per frame or per zoom. The offsets are in half-width units, so a chevron keeps its 5dp × 2dp shape at every
zoom and in every style. The vertex order matches the line's own quads: drape culls back faces, and the first build
(opposite order) drew nothing.

Spacing is in metres along the line, from Java: 150 m on a ride (about 96dp at zoom 16–17). On a preview it is
max(150 m, length / 14), so an overview shows about a dozen. The first chevron is half a spacing in and none is within
25 m of either end (the ring). Dimmed lines get none. Changing only the spacing (preview → ride) redraws the line: the
new line is drawn before the old one is removed, so the route never blinks out (BUG-004). At lower zooms they come closer on
screen and nothing culls them: a 150 m spacing is about 12dp at zoom 13 and 6dp at zoom 12. The ride never zooms out
that far, and the preview's spacing grows with the route's length.

Cost: R2 82 % CPU and 125 frames in 2 min, against the Phase 16 baseline of 90 % and 130 (noise range 76–90 %)
(`shots/baseline/phase21_chevrons.md`).

**Wrong way.** It is a phase of `FollowEngine` (plan 21.2), between on route and off route. Two details beyond the plan:
- The route's direction at the rider's place is the bearing over ±10 m of it, so a corner does not read as a reversal.
- The 30 m back is counted from the furthest place reached since the course turned against the route, so progress with a
  bad course (slow, noisy) does not count as going back. The "remaining" frozen while wrong way is the value at that
  furthest place, the last one on route.
- The emulator's GPS reports a course of 0 on every fix (`geo fix` has no course and `geo nmea` is ignored). The
  emulator check (`tools/trackzero/wrong_way_check.sh`) therefore rides backwards up the route's one southbound
  stretch, where 0 is the true course. There R3W came 34 m after turning back and cleared 20 m after turning forward
  again. The 10 s window had already run during the forward ride, which on the emulator also reads as "against".

The line is not dimmed (the rider is on it; the chevrons show the way), the ETA shows `--:--`, and a buzz marks the
start as for off route. Guide treats wrong way as on the route: no leg is asked for and a leg is dropped as before.
The voice says "Wrong way" once per episode (a tone comes in Phase 32).

## D47 · Navigation profiles: the shared foundation (Phase 22)
`routes/Profile` (Road, Gravel, Walk, Hike; `available()` = Road only for now) goes everywhere a path is asked for.
- **One router at a time.** `BicyclePath.nativeBuild(lat[], lon[], profile, …)` keeps one `IndexRouter` and the
  profile it was built for. A path for another profile frees it first. All four profiles build the bicycle router
  until Phases 24–26 give them their own weights and the pedestrian router. `nativeRelease` as before. The idle check
  reads `nativeLoadedProfile()` through the debug-only `TzRideDebugActivity` ("router none").
- **Why no path.** `TzRouter.Callback.onPath(path, Reason)` replaces the `mapMissing` flag: OK, NO_MAP (incl.
  NeedMoreMaps, FileTooOld), NO_WAY (RouteNotFound, its redress variant, IntermediatePointNotFound), NO_START,
  NO_END, CANCELLED, ERROR (the rest, or no engine). The mapping lives in `Reason.of`, so it is testable without
  `TzRouter`'s main-thread Handler.
- **The ride keeps its profile.** It is a journal record `F,{id}` right after the header, not a sixth header field:
  the header's last field is the free-text name, and older journals (no `F`) restore as Road. `rides.json` gets
  `"profile"`; `routes.json` gets an optional `"profile"` (planned and proposed routes, Phase 28). Missing = Road.
- **Where the profile comes from.** The pill saves the choice to `TzPrefs.profile()` at once. START and a free ride
  both read it. `Guide.Paths` has no profile parameter: the service's implementation asks the ride's session. That
  is the same thing with one fewer argument through the pure guide.
- **Preview.** For a track, changing the profile changes nothing visible yet: legs, detours and time estimates follow it
  on the ride. For a place, the path is worked out again (the request in flight is cancelled first).
- **Tests.** Local unit tests have no real `org.json` (Android's stub), and adding a test library is against the
  no-new-libraries rule. So the JSON fields are one call each to `Profile.of`, which `ProfileTest` covers, and the
  journal round trip is tested in `RideSessionTest`.

## D48 · Surface preview (Phase 23, FEAT-006)
- **Reading.** `Framework.nativeRoadSurfaces` takes the closest road within 15 m of each sample and reads its
  `psurface` type. The plan said to copy `nativeRoadJunctions`' one query per point. Measured on the emulator's debug
  build, that took 9.5 s for the 70 km loop (1,411 samples). The map index answers with whole cells: about 560
  features read per 30 m query, the same ones again for the next sample. Now consecutive samples are read together:
  one query per stretch of up to 1 km or 32 samples, each sample taking its closest road from what was read. Same
  result, 1.6 s on the emulator's unoptimised native build. The country lookup (a border polygon test) is done only
  for samples with no road near, to tell 0 (map, no road) from -1 (no map).
- **Classes.** -1 and 0 are both "No data", its own class in the bar and legend, never merged into another surface
  by classification. Runs under 1 % of the route join their longer neighbour on the bar only. The legend's percentages
  are computed from all samples (largest remainder, summing to 100), and a class present on the route shows at least
  1 %.
- **Cache.** RouteGeometry format 3 appends the step, a maps stamp and one byte per sample. Version 2 caches are read
  as version 3 without surfaces, not rebuilt from the GPX. Surfaces are read again only when some samples had no map
  and the stamp has changed. The stamp is the map data version plus the number of downloaded maps, read on the main
  thread: CoMaps' storage checks its thread, and the first build aborted reading it from the IO thread.
- **Card.** The bar arrives after the card is drawn: the card grows over 180 ms (`TzMotion.GROW_MS`) and the route is
  framed again for the taller card, unless the rider has moved the map since.

## D49 · Gravel profile and quieter roads (Phase 24, FEAT-003)
`routing_common/tz_profile` holds the profile the next models are built for. `BicyclePath.cpp` sets it just before
it constructs a router. `BicycleModel`'s constructor applies it on top of the speeds a country model passes, so
country rules (no trunk in France, footway speeds…) stay. The factor tables are function-local statics, because the
base model keeps a reference to them. The surface tables are namespace-scope `initializer_list`s like the stock
one. A list returned from a function would dangle.

**Final weights** (the plan's starting point, kept after the check below):

| | Road | Gravel |
|---|---|---|
| Factor trunk (+link) | 0.6 | 0.45 |
| Factor primary (+link) | 0.75 | 0.55 |
| Factor secondary (+link) | 0.9 | 0.75 |
| Factor tertiary (+link) | 1.0 | 0.9 |
| Surface paved good / bad | 1.0 / 0.8 | 1.0 / 0.9 |
| Surface unpaved good / bad | 0.6 / 0.2 | 1.0 / 0.35 |
| `hwtag-nocycleway` | 0.95 (stock) | 1.0 |
| Weight speed track, path, bridleway, unclassified (in / out of town, km/h) | stock | 14/16, 11/12, 8/9, 15/17 |
| ETA speed track, path | stock | 14/15, 11/12 |

**Check** (About › Compare profiles, `tools/trackzero/profile_compare.md`), ten trips on the Puy-de-Dôme map. Shares
of the route's samples:

| Trip | Road km · track % · prim+sec % · gravel % | Gravel km · track % · prim+sec % · gravel % · rough % |
|---|---|---|
| Clermont – Orcines | 10.6 · 0 · 16 · 0 | 7.3 · 24 · 11 · 3 · 0 |
| Aubière – Lac d'Aydat | 20.9 · 22 · 27 · 1 | 19.7 · 14 · 21 · 1 · 0 |
| Royat – Col de Ceyssat | 11.7 · 0 · 37 · 0 | same route |
| Volvic – Pontgibaud | 28.2 · 2 · 4 · 0 | 21.6 · 28 · 10 · 14 · 0 |
| Riom – Châtel-Guyon | 5.9 · 0 · 40 · 0 | same route |
| Clermont – Gergovie | 12.7 · 0 · 47 · 0 | 11.1 · 27 · 13 · 1 · 4 |
| Beaumont – St-Genès-Champanelle | 10.0 · 0 · 24 · 0 | 8.4 · 25 · 1 · 5 · 0 |
| Thiers – Courpière | 14.4 · 0 · 4 · 0 | same route |
| Issoire – Saint-Nectaire | 25.7 · 2 · 25 · 0 | 25.4 · 33 · 7 · 14 · 0 |
| Le Mont-Dore – La Bourboule | 5.8 · 0 · 93 · 0 | 5.8 · 0 · 66 · 0 · 0 (26 % path) |

Result against the plan's pass:
- Where tracks exist, Gravel takes them (24–33 % track against 0–2 %). It has more gravel surface where the tracks
  are tagged (up to 14 %; most tracks here have no surface tag, so "no data" grows too). Primary and secondary share
  is lower on every differing trip but Volvic–Pontgibaud (10 % against 4 %), where Gravel swaps 65 points of tertiary
  for track and minor roads.
- Rough: at most 4 %.
- Length: no Gravel route is longer than Road's; most are shorter, because Road's detours around primaries are not
  needed on tracks.
- Trunk: none in any Road route.
- Three trips give the same route with both profiles: there is no other way.

So the starting numbers are kept. **Contraflow** (24.5): Rue Rameau, Clermont-Ferrand (one-way westwards,
`oneway:bicycle=no`), routed eastwards against the one-way. Both profiles ride the street itself (0.3 km, no detour):
`hwtag-bidir_bicycle` is honoured as before.

**Limit.** On long routes across map regions the router first uses crossings between regions computed in advance
with the stock weights, so near region borders the profile applies a little less strictly.

## D50 · Walking profile (Phase 25, FEAT-012)
- **Router.** Walk (and later Hike) builds CoMaps' pedestrian `IndexRouter` with its stock weights. Each turn now
  also carries its `PedestrianDirection`. `TzRouter.Turns.pedestrian` maps straight, right, left and destination; the
  car direction is used when the pedestrian one is none. The mappings sit in a nested class so they load in unit
  tests without the router's main-thread Handler.
- **On foot** is one flag (`Profile.foot`) carried into existing classes, no forks:
  - `RideSession`: pause after 10 s under 1 km/h, resume above 2 km/h.
  - `FollowEngine.setFoot`: ETA fallback 4.8 km/h. The GPS course counts from 1 m/s instead of 2, and the wrong-way
    check reads the same course, so its minimum speed is 1 m/s too.
  - `Guide`: passes the flag to every engine it makes (paths to a place, legs).
  - `RideZoom.setFoot`: z17 at any speed.
  - A free walk is saved as "Walk {date}". Off route 40 / 20 m, voice and the R4 units are unchanged.
- **Walking time** (`routes/FootTime`): 4.8 km/h plus one hour per 600 m climbed (distance only without altitudes),
  H:MM. Boards B5/N2 have no time field. So that no layout changes, the place preview's label line carries it: "On
  foot · about 0:49". The planner (Phase 28) will use the same function.
- **Check.** About › Compare profiles now runs every profile switched on. Walk takes footpaths Road does not: path share
  9–47 % against Road's 0–21 % on the ten trips, track share up to 64 %. NAVIGATE HERE to La table du Puy with Walk:
  3.4 km, "On foot · about 0:49".
- **Battery** (`shots/baseline/phase25_walk.md`, 2 minutes on R2 at 5 km/h, not the plan's 30: the emulator's numbers
  do not need longer). CPU 86 %, 122 view frames, 50 threads, one GPS request at 1 s. The Road ride of Phase 22 had
  83 %, 131 and 52. Sensors: the barometer and also the rotation vector. Below 3 m/s the compass stays awake (D38),
  as intended for walking (the map turns with the walker). Its battery cost on a phone is part of N25.
- Seen on the emulator, with Walk and with Road: on the smoke test's detour, which rides 80 m beside the track (off any
  road), the router can start the way back on a road over 60 m away. The leg's card then reads "Head to start" with
  an arrow towards the leg's first point. That is the leg engine's behaviour since Phase 15, and the wording ("start")
  fits a leg back onto the track badly. It is in the backlog for Phase 38, not changed here.
