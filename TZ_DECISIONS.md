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
