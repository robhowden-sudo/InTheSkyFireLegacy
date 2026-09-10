# In The Sky Fire Legacy v3.5.3

Baseline: uploaded v3.5.2 FLIGHT_MAP_SMOOTHING only.

## Merge verification
The uploaded v3.5.2 already contains the Windows asset-parity work, so it was preserved rather than re-applied destructively.

Verified retained:
- v3.5.2 Flight map smoothing: local geography refresh is frozen while automatic camera animation runs, then refreshed once at the end.
- No touch/pinch map actions.
- Bundled Windows `world_map.png`; no OpenStreetMap/network tile system.
- GeoNames local place data.
- OurAirports airport and runway data.
- Cached lightweight Radar geography background for smooth sweep animation.
- Themed Radar/Flight map artwork.
- Original Windows Moon, Sun, star and planet image assets.
- Windows sonar WAV.
- ADSB.lol military database flag plus Windows ICAO/callsign military fallback heuristics.
- Great-circle Flight route rendering.
- Sky View aircraft, satellite and celestial tracks.

This build is therefore the clean merged continuation of v3.5.2, not a rollback to an earlier baseline.
