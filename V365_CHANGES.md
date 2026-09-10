# v3.6.5 changes

Flight route/camera rethink:

- FOLLOW TARGET stays centred tightly on the selected live aircraft.
- REMAINING PATH now frames the live aircraft -> destination segment, so the aircraft is actually on the path shown.
- FULL ROUTE frames the true origin -> destination route and still shows the live aircraft at its current position.
- In Follow/Remaining Path modes, the travelled origin -> aircraft section is dimmed and the remaining aircraft -> destination section is highlighted.
- Origin/destination markers are only forced into the FULL ROUTE view; Remaining Path shows the destination and selected aircraft.
- Fixes the previous bug where path dimensions were calculated from the full route and then recentered around the live aircraft, causing the route to appear nowhere near the selected target.
- Version 3.6.5 / versionCode 65.
