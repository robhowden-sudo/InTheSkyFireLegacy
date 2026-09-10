# v3.5.5 — Aircraft/API Resilience Audit

Baseline: v3.5.4 SKY_DATA_HORIZON_FIX only.

## Radar
- Radar now queries ADSB.lol and Airplanes.live on each normal radar refresh and merges unique positioned contacts by ICAO hex.
- This improves the chance of seeing airport surface traffic where one receiver network has weaker ground coverage.
- OpenSky remains a disaster-recovery fallback only when both readsb-compatible live feeds fail.
- Existing 10-minute caches remain available after live-provider failure.
- Radar status now includes a visible GROUND contact count and names the live sources used.
- Flight follow now tries ADSB.lol first, Airplanes.live second, then the last radar contact snapshot.

## Launches
- Updated The Space Devs Launch Library primary endpoint from unsupported API v2.2.0 to supported v2.3.0 `/launches/upcoming/`.
- RocketLaunch.Live `next/5` remains the independent fallback.

## Audit notes
- Ground aircraft are not filtered out by the Fire parser; readsb `alt_baro: "ground"` is mapped to `onGround=true`.
- OpenSky is not used as a completeness merger because its low-level/ground coverage depends heavily on receiver geometry and anonymous REST limits.
- Weather remains Open-Meteo -> MET Norway -> cache.
- Satellites remain SatNOGS -> CelesTrak -> cache.
- ISS remains WhereTheISS -> cache.
- Small bodies remain JPL Horizons -> cache.
