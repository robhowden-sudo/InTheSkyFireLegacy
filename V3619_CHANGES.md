# v3.6.19 Map Geometry Correction

- Keeps the v3.6.18 pre-rendered offline aviation basemap.
- FOLLOW TARGET centres strictly on the selected live aircraft.
- HOME remains a nearby reference marker and never controls the Follow camera.
- Added a single aspect-correct Mercator display camera.
- Raster basemap and all live overlays now use the exact same corrected camera bounds.
- Eliminates horizontal/vertical stretching of baked city labels and coastlines.
- Prevents aircraft/HOME/route overlays drifting away from the underlying map.
- REMAINING PATH and FULL ROUTE retain their existing route framing, but are displayed without geometric stretching.
- WORLD/OpenSky and lunar behaviour unchanged.
- Version 3.6.19 / versionCode 79.
