# v3.6.11

- Fixed close Flight map letterboxing/strip rendering by expanding the stable OSM mosaic from 5x3 to 5x5 tiles.
- Increased the in-memory tile threshold enough for a complete 5x5 mosaic to assemble before trimming.
- Kept the single map-loading worker and stable-mosaic swap to avoid the earlier flicker/crash behaviour.
- Increased WORLD aircraft dot size so the OpenSky global layer is clearly visible on the full map.
- WORLD camera button now refreshes/labels the global traffic state explicitly.
- Fixed persistent bottom clipping by increasing the footer itself to 31dp/30dp child rows, rather than adding bottom padding that only reduced usable content.
- Restored root bottom padding to a normal 3dp.
- OpenSky embedded credentials and OPENSKY ✓ status from v3.6.10 are unchanged.
- Version 3.6.11 / versionCode 71.
