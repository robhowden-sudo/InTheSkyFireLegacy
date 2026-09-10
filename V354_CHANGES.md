# In The Sky Fire Legacy v3.5.4

Baseline: v3.5.3_MERGED_BASELINE only.

## Sky View data / horizon fix
- Restored the previously inert asteroid/comet layer using NASA/JPL Horizons observer ephemerides.
- Uses a restrained curated set of notable asteroids/comets and a 2-hour local JSON cache to avoid repeatedly hitting Horizons when Sky View is reopened.
- Horizons results are calculated for the saved user latitude/longitude and current time, returning topocentric azimuth/elevation.
- Keeps the panorama physically correct: objects below the display horizon (-5 degree tolerance) are not drawn in front of the user.
- Adds a Sky View status report showing major bodies below the display horizon instead of making them appear to be missing/broken.
- Adds counts for satellites and small bodies that are valid but below the display horizon.
- Satellite data remains SatNOGS with CelesTrak fallback/cache; planet/Sun/Moon/star positions remain locally calculated.
