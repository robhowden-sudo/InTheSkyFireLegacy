# v3.6.2 changes

- Replaced the close-range Flight synthetic map with cached OpenStreetMap raster tiles.
- Map tiles are recoloured at render time to match the active In The Sky theme.
- Flight map tiles are cached on-device for up to 14 days and loaded asynchronously.
- Suppressed the old synthetic town/airport label network at close zoom to eliminate gibberish/duplicate letters.
- Added OpenStreetMap contributor attribution to the close Flight map.
- Increased Weather trend values again for Temperature, Humidity, Wind Speed, Pressure and Rainfall.
- Expanded the Weather hero box with feels-like, humidity, wind, gust, pressure, sunrise and sunset information.
- Increased Local Conditions label and value sizes again.
- Enlarged the Space lunar image area substantially and made the lunar text area scrollable.
- Removed the cyan outline around the Moon.
- Removed the illumination/waxing text overlay from on top of the Moon image; that information remains in the lunar text panel.
- Restored several pixels of vertical content by tightening the nav/footer heights, fixing the slight bottom clipping introduced by the v3.6.0 readability pass.
- Version 3.6.2 / versionCode 62.
