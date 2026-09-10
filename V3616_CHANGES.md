# v3.6.16 Flight map hierarchy cleanup

- Selected aircraft is now a large amber target with halo and larger bold callsign.
- FOLLOW TARGET gets a clear amber dashed HOME-to-aircraft track.
- Selected aircraft remains drawn last so it cannot disappear beneath labels.
- Removed the dense synthetic two-neighbour city network over real OSM.
- Synthetic network is now faint and only used while OSM is unavailable.
- City/town labels are population-prioritised and collision-aware.
- Reduced label count substantially at regional zoom.
- Airport labels now accept only recognisable three-letter codes.
- GB-xxxx / local airport identifiers are suppressed from the regional map.
- Airport labels also participate in collision avoidance.
- Existing hybrid OSM renderer, OpenSky traffic and lunar scroll changes retained.
- Version 3.6.16 / versionCode 76.
