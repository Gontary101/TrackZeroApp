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
