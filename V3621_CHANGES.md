# v3.6.21 Flight Map Polish

- Clean-sheet v3.6.20 FlightMapView retained.
- Added a 4096x3072 high-resolution regional aviation basemap for UK/Europe/North Africa.
- FOLLOW/PATH/FULL automatically use the sharper regional map whenever the current camera is inside its coverage.
- Long-haul routes and WORLD fall back to the existing world basemap.
- No network or tile loading is introduced.
- FOLLOW TARGET still keeps the selected aircraft centred.
- FOLLOW TARGET now also shows the remaining aircraft -> destination route.
- HOME remains a small nearby reference marker.
- FOLLOW city text reduced to 8.5sp and remains collision controlled.
- Smooth cubic camera animations and zoom animations retained.
- Version 3.6.21 / versionCode 81.
