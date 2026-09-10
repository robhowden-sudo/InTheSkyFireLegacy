# v3.6.15

## Hybrid Flight local map
- Local Flight view no longer shows an empty chart/grid while OSM tiles are loading.
- Windows-style local geographic network is rendered immediately as the fallback.
- Real OSM tiles load underneath in the background using Fire-friendly zoom levels.
- OSM zoom reduced to z5-z7 to keep tile counts small and reliable on 2017 Fire hardware.
- Tile loading uses two workers with a bounded request footprint.
- Large In The Sky place/airport labels stay above the real map for readability.
- HOME, aircraft and route overlays remain above all map layers.
- Bundled global world map is reserved for medium/full-route/world views rather than being magnified into local mode.
- Local place and airport label sizes increased.

## Lunar
- Scrollable Upcoming Primary Phases section from v3.6.13 retained.

Version 3.6.15 / versionCode 75.
