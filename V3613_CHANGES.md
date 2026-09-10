# v3.6.13

## Flight map renderer
- Real OSM map now renders in screen space, independent of the geographic overlay camera transform.
- FOLLOW TARGET and REMAINING PATH therefore fill the Flight viewport instead of collapsing into a small floating map rectangle.
- Tile requests are based on the current camera bounds with a bounded safety limit for legacy Fire hardware.
- Aircraft, HOME, route and destination overlays remain geographic and are drawn above the screen-space map.
- FULL ROUTE and WORLD behaviour retained.
- OpenSky global traffic remains on-demand/stale-gated.

## Lunar Phase
- Current Moon/phase/illumination/age/waxing-local-events area remains fixed in the reference-style panel.
- UPCOMING PRIMARY PHASES moved into its own 104dp scrollable section beneath the fixed instrument.
- Four upcoming primary phases remain available without being cut off.
- Existing Moon styling and rise/set calculations retained.

Version 3.6.13 / versionCode 73.
