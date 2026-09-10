# In The Sky Fire Legacy v3.5.6

## Flight local-map parity rebuild

- Reworked the Flight `WorldMapView` to stop magnifying the low-resolution world raster at regional/local zoom.
- Added a smooth world-to-local crossfade.
- Added a dark vector-style regional chart using bundled city, airport and runway data.
- Added adaptive city/airport label density and local geographic network lines.
- Added runway rendering at close zoom.
- Tightened Follow Target framing to a few-hundred-kilometre local view, closer to the Windows reference.
- Preserved Follow Target / Zoom to Path / Frame Route / World / +/- controls and existing route/aircraft/home overlays.
- No new network mapping API added.
