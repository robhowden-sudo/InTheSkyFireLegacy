# v3.5.8 — Radar Orientation Persistence Fix

- Split the old shared `orientation` preference into independent `radarOrientation` and `skyOrientation` values.
- Added one-time migration from the legacy shared orientation value so existing user settings are preserved.
- Radar Settings now saves only Radar orientation.
- Sky / Dock Settings now saves only Sky View facing direction.
- `VIEW IN SKY`, `SHOW IN SKY`, Sky View direction buttons and automatic aircraft focus now change Sky View orientation only.
- Radar orientation now survives page changes, Sky View use and app restarts.
- Version bumped to 3.5.8 / versionCode 58.
