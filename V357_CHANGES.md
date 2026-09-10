# v3.5.7 — Readability + Selected Object Intelligence

- Increased Flight local-map city, town, airport, HOME and aircraft label sizes for Fire HD readability.
- Removed a duplicate city-label draw inherited from v3.5.6.
- Added a collapsible OBJECT DETAILS panel to Sky View.
- Selected planets, Sun, Moon and major stars now show local cached reference specifications plus current azimuth/altitude and horizon status.
- Satellites show current azimuth/elevation/range from cached TLE propagation.
- Asteroids/comets show JPL Horizons ephemeris values and the existing 2-hour cache policy.
- Aircraft show live telemetry immediately and append ADSBDB manufacturer/model/registration/owner metadata using the existing 7-day local cache.
- Static astronomy reference data is bundled in code and therefore works offline without extra API calls.
- Build identity updated to v3.5.7 / versionCode 57.
