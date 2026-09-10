# v3.6.8 changes

## Stability
- Removed the permanent 60-second global aircraft background refresh.
- Global traffic is now requested only when Flight/WORLD is used, and cached snapshots are reused for 5 minutes.
- Global aircraft are stored as lightweight latitude/longitude contacts rather than full Aircraft metadata objects.
- Map tile loading reduced to one worker thread.
- In-memory map tile cache reduced substantially to lower heap pressure on the Fire HD.

## Global WORLD traffic
- ADSB.lol remains the local Radar source.
- ADSB.lol does not expose a documented unrestricted public whole-network snapshot suitable for this WORLD mode.
- WORLD mode therefore uses authenticated OpenSky /states/all as the practical fallback.
- OpenSky status remains visible in the top bar.
- WORLD view shows aircraft count and data source when a snapshot succeeds, or an explicit failure message when it does not.
- No per-aircraft metadata lookups are performed for the global layer.

## Existing fixes retained
- Weather refresh once per hour with LAST UPDATE time.
- On This Day once per local calendar day.
- Lunar phase layout, larger data area and restored upcoming phase schedule.
- Bottom safe-area padding.
- Flight route camera modes from v3.6.5.

Version 3.6.8 / versionCode 68.
