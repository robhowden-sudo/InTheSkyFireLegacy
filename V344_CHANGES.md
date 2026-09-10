# In The Sky Fire Legacy v3.4.4

- Radar OSM backdrop is now monochrome-tinted to the active app theme/accent and darkened beneath radar graphics.
- Radar local map requests enough tiles to cover the complete scope at every supported range.
- Flight local OSM view no longer stops after 48 tiles; every tile intersecting the visible camera rectangle is requested/drawn.
- Flight map slightly prefetches beyond the current camera frame to avoid blank strips during zoom animations.
