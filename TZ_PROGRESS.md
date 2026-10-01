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
