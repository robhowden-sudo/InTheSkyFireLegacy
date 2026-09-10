# v3.6.6 changes

## Global Flight WORLD traffic
- WORLD view renders worldwide positioned aircraft as lightweight live targets.
- Global traffic refreshes once at app launch and every 60 seconds.
- Primary source: ADSB.lol global aircraft JSON when available.
- Fallback: authenticated OpenSky `/states/all` using OAuth2 API-client credentials.
- Short local caches and anonymous OpenSky remain last-resort fallbacks.
- OpenSky Client ID and Client Secret inputs are available in Settings → DATA.
- Bulk aircraft metadata lookups are deliberately avoided; detailed metadata remains selection-driven.
- FOLLOW TARGET, REMAINING PATH and FULL ROUTE retain the v3.6.5 route/camera behaviour.

## Weather
- Weather is warmed at app launch.
- Automatic live refresh is once per hour.
- Reopening Weather within the hour reuses cached data.
- Manual Refresh forces a live update.
- Open-Meteo → MET Norway → cache fallback remains.

## On This Day
- Loaded at app launch.
- Saved locally with the current local calendar date.
- Reopening Time on the same date reuses the stored result.
- A new Wikimedia fetch occurs only after the local date changes.

## Lunar panel retained
- Large Moon image.
- No cyan ring.
- No text over the Moon.
- Phase shadow retained.
- Phase / illumination / age and moonrise / moonset visible.
- Full Upcoming Lunar Phases schedule remains in its own scrollable section.

Version 3.6.6 / versionCode 66.
