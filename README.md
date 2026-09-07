# In The Sky — Fire HD Legacy

A lightweight Android 5.1 / Fire OS 5 edition of **In The Sky**, built specifically for older hardware such as the Amazon Fire HD 10 (7th generation).

This is the separate legacy branch of the In The Sky project. The modern Android/Fold application remains in `InTheSkyNative`.

## Current version

**v3.5.9 — Page Cycle Selection**

The Fire Legacy edition has grown well beyond the original four-page proof-of-concept and now aims for practical feature and visual parity with the Windows edition while remaining suitable for Android 5-era Fire hardware.

## Pages

- **Radar** — live aircraft radar with selectable contacts, range controls, orientation, filters and aircraft details.
- **Flight** — selected-aircraft tracking, route/path display and hybrid world/regional map.
- **Weather** — current conditions, forecast and weather data with fallback/cache support.
- **Time** — local and reference clocks plus date/time information.
- **Space** — astronomical and orbital information.
- **Sky View** — local sky panorama for celestial objects, satellites, aircraft and small bodies.
- **Launches** — upcoming space launches and mission information.
- **Settings** — location, units, radar, Sky/Dock, page cycling and other application preferences.

## Radar and aircraft data

Radar combines **ADSB.lol** and **Airplanes.live** coverage and deduplicates contacts by ICAO address. **OpenSky** remains an emergency fallback, with local cached data available if live providers cannot be reached.

Ground-mode aircraft are retained when supplied by the receiver networks.

Aircraft reference information can include callsign, ICAO address, registration, aircraft type, altitude, speed, heading, range, bearing, manufacturer/model and owner/operator information where available. Reference metadata is cached locally to reduce unnecessary network traffic.

Radar orientation is stored independently from Sky View orientation and persists across page changes and app restarts.

## Flight map

The Flight page uses a hybrid map renderer:

- themed global raster map at world/continental scale;
- native vector-style regional view at closer zoom levels;
- bundled city and airport geographic data;
- airport/runway detail at close range;
- adaptive labels;
- HOME, aircraft, route and destination overlays;
- automatic Follow Target, Zoom to Path, Frame Route, World and +/- camera controls.

The regional renderer avoids magnifying the low-resolution world asset at close zoom levels.

## Sky View

Sky View combines locally calculated astronomy with live/cached orbital and aircraft data.

Supported object classes include:

- Sun and Moon;
- planets;
- major stars;
- satellites;
- aircraft;
- asteroids and comets.

Objects below the display horizon are reported rather than silently appearing to be missing. Satellite positions are propagated locally from cached TLE data, while small-body ephemerides use NASA/JPL Horizons with caching.

Selecting an object opens a collapsible **OBJECT DETAILS** panel. Depending on object type it can show physical/reference specifications, current azimuth/elevation, horizon state, satellite range, aircraft telemetry and cached aircraft metadata.

## Sky / Dock mode

Automatic page cycling has a configurable duration and per-page selection.

The following pages can independently be included or excluded from the cycle:

**Radar · Flight · Weather · Time · Space · Sky View · Launches**

All pages are enabled by default.

## Data resilience and caching

The legacy build deliberately uses caching and fallbacks to reduce network traffic and remain useful on older hardware.

Major data paths include:

- aircraft: ADSB.lol + Airplanes.live → OpenSky fallback → cache;
- aircraft metadata/routes: ADSBDB → cache;
- weather: Open-Meteo → MET Norway → cache;
- satellites: SatNOGS → CelesTrak → cached TLE;
- planets/Sun/Moon/stars: calculated locally;
- asteroids/comets: NASA/JPL Horizons → cache;
- launches: The Space Devs Launch Library 2.3 → RocketLaunch.Live fallback;
- ISS: WhereTheISS → cache;
- reference images: Wikimedia/Wikipedia/NASA sources with local caching/placeholders.

## Compatibility

- Minimum Android: **5.1 / API 22**
- Target SDK: **28**
- Compile SDK: **36**
- UI: classic Android Views, no Jetpack Compose
- No AndroidX runtime dependencies
- Java 8 language level
- TLS 1.2 explicitly enabled for legacy HTTPS compatibility

Primary legacy hardware target: **Amazon Fire HD 10 (7th generation), Fire OS 5.7.1.0**.

## Building

The project is intended to open directly in **Android Studio**.

The repository also contains a GitHub Actions workflow for producing a debug APK from `main`. Open the latest successful **Build Fire HD 10 Android 5 APK** workflow run and download the generated debug artifact.

## Development baseline

Current application baseline: **v3.5.9 / versionCode 59**.

The Windows `airspace_full.py` application and its assets are the parity reference for the Fire Legacy edition.
