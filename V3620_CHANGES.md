# v3.6.20 Clean-Sheet Flight Map

- Dedicated FlightMapView. Flight no longer reuses WorldMapView.
- Single offline 2048x2048 Mercator aviation basemap.
- No OSM network map loading and no legacy synthetic Flight geography.
- One shared projection for basemap and all Flight overlays.
- FOLLOW TARGET centres on selected aircraft.
- REMAINING PATH frames aircraft -> destination.
- FULL ROUTE frames origin -> destination.
- Short routes no longer inherit giant fixed continental camera widths.
- WORLD keeps the OpenSky global traffic view.
- Smooth cubic camera animations retained for all view changes and zoom buttons.
- Dynamic city labels are screen-space, collision controlled, and smaller in FOLLOW TARGET.
- Selected aircraft is drawn last with amber halo and heading-aware symbol.
- HOME is only a reference marker.
- Version 3.6.20 / versionCode 80.
