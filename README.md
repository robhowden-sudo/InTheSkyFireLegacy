# In The Sky — Fire HD Legacy

A lightweight Android 5.1 / Fire OS 5 edition of **In The Sky**, built specifically for older hardware such as the Amazon Fire HD 10 (7th generation).

This is a separate legacy project. The modern Android/Fold application remains in `InTheSkyNative`.

## Pages

The legacy edition intentionally contains only four pages:

- **Radar** — live ADSB.lol aircraft around the selected location, range rings, selectable contacts and aircraft details.
- **Weather** — live MET Norway forecast data.
- **Time** — local clock/date plus UTC, New York and Tokyo reference clocks.
- **Settings** — location, radar range, refresh interval and 12/24-hour clock.

## Aircraft reference panel

Selecting an aircraft on Radar shows callsign, ICAO address, registration, aircraft type, altitude, speed, heading, range and bearing.

The panel also reuses the useful reference concept from the modern app's Sky View: ADSBDB metadata plus an aircraft-type image and description from Wikipedia/Wikimedia when a known type match is available. Images are cached locally after download.

## Compatibility

- Minimum Android: **5.1 / API 22**
- Target SDK: **28**
- Compile SDK: **36**
- UI: classic Android Views, no Jetpack Compose
- No AndroidX runtime dependencies
- Java 8 language level
- TLS 1.2 explicitly enabled for legacy HTTPS compatibility

The first acceptance target is **Amazon Fire HD 10 7th generation running Fire OS 5.7.1.0**.

## Build

GitHub Actions builds a debug APK on every push to `main`.

Open the latest successful **Build Fire HD 10 Android 5 APK** workflow run and download:

`InTheSky-FireHD10-Android5-debug`

The APK inside the artifact is `app-debug.apk`.

## Status

This is an early legacy build. First milestone: install, launch and prove live Radar/Weather HTTPS access on the Fire HD 10. Layout and feature polish follow after device acceptance.
