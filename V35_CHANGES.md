# In The Sky Fire Legacy v3.5

This build uses the original Windows Store source and asset pack as the parity baseline.

## Mapping
- Removed all OpenStreetMap/network tile rendering from Radar and Flight.
- Restored the original lightweight bundled `world_map.png` architecture.
- Added compact GeoNames city data and OurAirports airport/runway data derived from the Windows assets.
- Radar geography is rendered once into a cached background bitmap, keeping the sweep independent and smooth.
- Radar overlays local place markers, airport crosses and runway geometry.
- Flight keeps the animated world-map crop/zoom system and overlays local city/airport detail when zoomed in.
- Map artwork is tinted to the active Fire Legacy theme.

## Visual assets
- Replaced the Fire copies with the original Windows assets for the world map, Moon, Sun, stars, Mercury, Venus, Mars, Jupiter and Saturn.
- Added the Windows sky-object atlas to the project for reference/fallback work.
- Restored the original Windows sonar asset to the Android resources.

## Detection
- Retains ADSB.lol `dbFlags` military classification and adds the Windows ICAO/callsign military heuristics as a fallback.
- Existing aircraft category symbols, military highlighting, alerts, Sky View tracks and route/flight behaviour are retained.

## Performance
- No map tile downloads, no periodic tile-cache rebuilds and no map-network work on the radar animation path.
