# In The Sky Fire Legacy v2.5

## Functionality convergence pass

Target: Windows Store visual layout + InTheSkyNative behaviour on Fire OS 5 / Android 5.1.

### Radar
- Working Traffic, Level, Labels and Vector controls.
- Native-style filtering of displayed contacts while retaining the unfiltered feed for other pages.
- Nearest-contact table text enlarged.
- Automatic selection when an aircraft crosses into the orange alert range, including aircraft reference imagery and route/metadata lookup.
- Double-tap radar target opens Flight and focuses the world map on that aircraft.
- Native-style label modes and selectable 0/1/2/5 minute heading vectors.
- Theme selector fixed to use the native accent semantics.

### Flight
- Live contact picker rows are individually selectable.
- Radar handoff focuses the selected aircraft on the map.
- Follow Target refreshes the selected ICAO position periodically.
- Return to World clears focus/route state.
- Route lookup remains tied to ADSBDB and route endpoints.

### Weather / Time / Space
- Existing native-provider weather, history, warning and cache logic retained.
- Home timezone now follows the saved Settings timezone rather than the Fire OS alias.
- ISS live/cache telemetry, lunar phase, ground track and heliocentric display retained.

### Sky View
- Aircraft in the panorama are tappable and populate the reference panel.
- Sun, Moon, Jupiter and bright-star targets can be tapped for local reference imagery.
- Radar/Flight selected contact handoff remains active.

### Launches
- Launch Library 2 primary feed, cache, and RocketLaunch.Live fallback retained.
- Mission selection continues to drive countdown/details/map.

### Settings
- Native theme behaviour restored: page/panel/text palette remains fixed; selected accent changes phosphor-highlighted controls/instruments only.
- Version: 2.5.
