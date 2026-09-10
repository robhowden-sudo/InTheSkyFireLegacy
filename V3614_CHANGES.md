# v3.6.14

## Flight local map rebuild
- Removed the stable-mosaic crop path that kept falling back to the enlarged bundled world map.
- Local OSM tiles now render directly into screen space using the current Flight camera bounds.
- Rendering is all-or-nothing: no tile appears until the full required set is ready, eliminating progressive tile waves/flicker.
- Detailed local zoom levels increased to OSM z7-z10 so town names, city names, roads and geographic detail are actually readable.
- FOLLOW TARGET regional framing widened slightly to better match the Windows reference.
- HOME, selected aircraft and route overlays remain custom In The Sky graphics above the real map.
- FULL ROUTE and WORLD continue to use the themed bundled global map / OpenSky global traffic layer.
- Tile memory cache remains bounded for Fire HD stability.

## Lunar
- v3.6.13 scrollable Upcoming Primary Phases section retained.

Version 3.6.14 / versionCode 74.
