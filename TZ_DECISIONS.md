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
the intent reaches TzActivity, which imports it.

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
