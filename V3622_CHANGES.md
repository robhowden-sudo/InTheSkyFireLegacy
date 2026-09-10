# v3.6.22 Regression Cleanup

## Space
- Restored the themed world map underneath the Space ISS / Sun / Moon ground-track overlays.
- Space mode again receives the world-reference grid.
- FlightMapView v3.6.21 is untouched.

## Sky View
- Rebuilt LIVE SKY KEY as two compact rows instead of one oversized single line.
- Key text uses a direct 10sp size so the global readability scaler cannot inflate and clip it.
- Increased the key panel to 82dp so both rows remain fully visible.
- All existing Sky View object symbols and meanings are retained.

Version 3.6.22 / versionCode 82.
