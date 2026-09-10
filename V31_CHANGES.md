# In The Sky Fire Legacy v3.1

- Time: enlarged daylight timeline labels, hour ticks and colour legend to match the Windows reference.
- Sky View: planets/Moon/Sun use distinct vector symbols instead of generic white blobs; the live key now matches rendered symbols.
- Sky View: satellite feed is no longer a no-op. SatNOGS is primary, CelesTrak stations/visual is fallback, then local cache.
- Satellite reference: real image lookup remains primary; generated satellite diagram is only the fallback.
- Aircraft symbols: corrected to the same ADS-B emitter-category mapping as InTheSkyNative, including helicopter, glider/light, balloon, parachute, drone, fast aircraft and ground vehicles.
- Military aircraft: ADSB.lol dbFlags military detection retained and foreground notifications added, with settings toggles for normal and military alert notifications.
- Selected planets: Uranus/Neptune and other planets no longer fall back to a star image; planet schematic/image lookup is used.
