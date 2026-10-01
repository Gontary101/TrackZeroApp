# TrackZero — Decisions

Choices not dictated by `TRACKZERO_BUILD_PLAN.md` / `design.html`, and every place we deliberately deviate from the
plan. Newest at the bottom.

## D1 · Base tag `v2026.08.31-14` (Phase 0)
Latest general CoMaps release. Newer tags (`v2026.09.08-3-apple`, `v2026.09.23-2-car`) are iOS / Android-Auto
releases. The repo is cloned shallow (`--depth 1`) with shallow submodules; `git fetch --unshallow` if history is needed.

## D2 · Build host tooling without root (Phase 0)
`configure.sh` needs `optipng` and Qt6 Svg/Positioning dev packages for the map symbol generator. They were
extracted from the distro `.deb`s into `~/.local/opt/` (no sudo). Only affects regenerating map styles/symbols.
