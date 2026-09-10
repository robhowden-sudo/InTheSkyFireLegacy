# In The Sky — Fire HD Legacy

**In The Sky Fire Legacy** is the Android 5.1 / Fire OS 5 edition of the In The Sky dashboard, built for older tablets such as the Amazon Fire HD 10 (7th generation).

It brings live aircraft tracking, flight information, weather, clocks, astronomy, Sky View and launch tracking into a single landscape dashboard while staying compatible with legacy Android hardware.

## Current tested build

**v3.6.23 · versionCode 83**

Latest tested Android Studio source baseline: **Launches Selection & Image Fix**.

> The modern Android/Fold project is maintained separately. This repository is specifically for the lightweight Fire HD / Android 5 legacy edition.

## What it includes

### Radar
- Live aircraft contacts from ADSB.lol and Airplanes.live
- OpenSky fallback and local cache support
- Range controls and persistent radar orientation
- Selectable contacts with callsign, ICAO, registration, type, altitude, speed, heading, range and bearing
- Aircraft reference metadata and route information where available
- Ground-mode aircraft retained when supplied by receiver networks

### Flight
- Selected-aircraft tracking
- Hybrid world/regional map presentation
- HOME, aircraft, route and destination overlays
- Follow Target, Zoom to Path, Frame Route, World and zoom controls
- Bundled city, airport and runway data for closer regional views

### Weather
- Current conditions and forecast data
- Cached/fallback weather support for unreliable legacy connections
- Open-Meteo primary data with MET Norway fallback

### Time
- Local and reference clock information
- Date/time display designed for always-on dashboard use

### Space
- Sun, Moon, planetary and orbital information
- ISS information and reference imagery
- Local calculations where practical to minimise unnecessary network traffic

### Sky View
- Local sky panorama for the Sun, Moon, planets and major stars
- Satellite positions from cached TLE data
- Live aircraft overlay
- Asteroid and comet support
- Selectable objects with a collapsible details panel
- Below-horizon objects are reported instead of silently disappearing

### Launches
- Upcoming launch list and mission information
- Strict single-selection behaviour
- Initial mission selection is highlighted correctly
- Double-tap selection support
- Improved Launch Library image parsing
- Mission and rocket image fallbacks
- NASA Images fallback when the primary launch image cannot be loaded
- Late image responses are discarded when the user has selected another mission
- Visible unavailable state when no suitable image can be found

### Sky / Dock mode
Automatic page cycling can rotate through selected dashboard pages at a configurable interval.

Pages can be enabled or disabled individually:

**Radar · Flight · Weather · Time · Space · Sky View · Launches**

## Data sources and resilience

The Legacy build deliberately uses caching and fallbacks so it remains useful on older hardware and does not hammer public APIs like an over-caffeinated polling script.

- **Aircraft:** ADSB.lol + Airplanes.live → OpenSky fallback → cache
- **Aircraft metadata/routes:** ADSBDB → cache
- **Weather:** Open-Meteo → MET Norway → cache
- **Satellites:** SatNOGS → CelesTrak → cached TLE
- **Sun / Moon / planets / stars:** calculated locally where practical
- **Asteroids / comets:** NASA/JPL Horizons → cache
- **Launches:** The Space Devs Launch Library → RocketLaunch.Live fallback
- **ISS:** WhereTheISS → cache
- **Reference imagery:** Wikimedia / Wikipedia / NASA sources with local caching and placeholders

## Compatibility

- Minimum Android: **5.1 / API 22**
- Target SDK: **28**
- Compile SDK: **36**
- UI: classic Android Views
- No Jetpack Compose
- Java 8 language level
- TLS 1.2 explicitly enabled for legacy HTTPS compatibility
- Primary target: **Amazon Fire HD 10 (7th generation), Fire OS 5.7.1.0**

## Building from source

Open the project in **Android Studio** and build the `app` module, or use the included GitHub Actions workflow.

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

Additional local build notes are in [`BUILD_LOCAL.md`](BUILD_LOCAL.md).

## Changelog

Release highlights are consolidated in [`CHANGELOG.md`](CHANGELOG.md). Detailed development history remains available through the repository commit history.

## Project status

**Active legacy build · v3.6.23 / versionCode 83**

This build was developed from the Windows Store parity layout and then evolved specifically for touch input and legacy Fire hardware. The aim is to preserve a capable, information-dense In The Sky experience on hardware that modern Android frameworks increasingly pretend never existed.
