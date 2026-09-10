# v3.6.7 changes

## Flight map / world aircraft
- Bundled themed world map is now a guaranteed fallback and is never faded to blank while local OSM tiles are unavailable.
- Local real-map tile loading continues in the background and only replaces the fallback after a completed stable mosaic exists.
- Reduced the local tile request batch to a fixed 5x3 neighbourhood to make completion much more reliable on legacy Fire hardware.
- Global WORLD traffic still attempts ADSB.lol first and falls back to OpenSky when ADSB.lol cannot provide a usable worldwide set.
- WORLD traffic failures are now shown explicitly in the Flight status line rather than silently showing zero contacts.
- WORLD traffic source and contact count remain visible when data is available.

## OpenSky status
- Settings -> DATA retains OpenSky API Client ID / Client Secret inputs.
- OpenSky credentials are validated at app launch and immediately after saving DATA settings.
- Top service strip now shows:
  - OPENSKY ✓ accepted
  - OPENSKY ✕ rejected
  - OPENSKY … checking
  - OPENSKY -- not configured
- Saving new OpenSky credentials immediately triggers a new world-traffic request.

## Weather
- Weather remains launch + hourly refresh with cache reuse between live updates.
- Weather header now shows LAST UPDATE HH:MM.
- Manual Refresh retains forced live refresh behaviour.

## Lunar phase
- Moon moved upward within its image area.
- Moon data panel increased from 72dp to 92dp.
- Upcoming Lunar Phases area increased to 110dp and remains scrollable.
- No cyan moon ring or text overlay is reintroduced.

## Bottom clipping
- Added 10dp bottom safe padding to the persistent root layout so page content is no longer pressed against the Fire OS lower screen boundary.

Version 3.6.7 / versionCode 67.
