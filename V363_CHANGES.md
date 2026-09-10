# v3.6.3 changes

- Flight close-range map tile loading changed to settle-then-swap. A new tile set is only displayed when every required tile is ready, eliminating the progressive section-by-section loading wave.
- Tile requests are paused while the automatic map camera is animating, preventing repeated loading caused by changing tile bounds.
- Replaced the aggressive inverse map recolour filter with a restrained monochrome theme tint so real map labels remain legible.
- Weather hero layout now uses its full available height so the added live condition text is no longer clipped at the bottom.
- Weather trend values remain at the larger v3.6.2 scale.
- Lunar phase panel rebuilt: large Moon gets the flexible majority of the panel, phase/rise/set text is always visible underneath, and the extra long schedule was removed from the panel.
- Lunar rendering now draws the real Moon image first and overlays a translucent unlit region, eliminating the black ring effect while preserving the phase.
- Removed all text and cyan outline from directly over/around the Moon image.
- Reclaimed additional vertical space from the navigation and footer strips to eliminate the remaining slight bottom clipping on every page.
- Version 3.6.3 / versionCode 63.
