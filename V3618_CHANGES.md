# v3.6.18 Pre-rendered Aviation Basemap

- Rebased on v3.6.16.
- Replaced live OSM and runtime synthetic geography in Flight FOLLOW/PATH/ROUTE with bundled pre-rendered aviation tiles.
- Tile pack is ~4.2 MB and is fully offline.
- Pre-rendered map includes:
  - land/coastlines and country borders
  - subtle lat/lon grid
  - major city labels baked into the raster
  - three-letter airport codes at closer scales
- Runtime only has to blit a few local JPEG tiles plus draw HOME/aircraft/route/destination overlays.
- No network map wait.
- No runtime city-label transforms, so no stretched/scrambled names during camera animations.
- z3-z5 cover the entire world; z6 provides higher-resolution Europe/North Africa detail and falls back to z5 elsewhere.
- Small 24-tile bitmap LRU cache keeps Fire memory use bounded.
- WORLD remains the existing themed global map with OpenSky worldwide aircraft.
- Existing lunar scrolling and OpenSky authentication retained.
- Version 3.6.18 / versionCode 78.
