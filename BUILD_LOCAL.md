# Local build (no GitHub required)

This project targets Fire OS 5 / Android 5.1 (API 22) and is landscape-only.

## Android Studio
1. Open this folder in Android Studio.
2. Allow Gradle sync to finish.
3. Build > Build APK(s).
4. Install `app/build/outputs/apk/debug/app-debug.apk` on the Fire HD 10.

## Command line
If Gradle 8.13+ and Android SDK API 36 are installed:

```text
gradle :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.


## v2 functionality pass
This revision wires the first batch of Windows-parity interactions:
- Radar range changes apply immediately.
- Radar selected contacts hand off to Flight and Sky View.
- Flight Follow / Return to World / Show in Sky controls are active.
- Sky View direction/reset/briefing/sector controls are active and selected aircraft are highlighted.
- Sky View can load the selected aircraft reference image.
- ISS live data uses a recent cache fallback.
- Launch countdown continues updating after mission selection.
- Startup page setting is honoured.
- Space map HOME marker uses the saved location.
