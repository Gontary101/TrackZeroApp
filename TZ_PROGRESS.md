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
