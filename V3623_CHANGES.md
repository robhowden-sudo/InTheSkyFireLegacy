# v3.6.23 Launches Fix

Built only from the protected v3.6.22 baseline.

- Launch list is now strictly single-select.
- Selecting another mission clears the previous highlight.
- Initial automatically selected mission is highlighted correctly.
- Double-tap still selects the mission and requests its image.
- Launch Library image parsing now accepts both string URLs and nested image objects.
- Added mission and rocket-configuration image fallbacks from the launch payload.
- If the primary launch image cannot load, NASA Images is used as a fallback.
- Late image responses are discarded if the user has since selected another mission.
- A visible unavailable state replaces silent image disappearance when no image can be found.
- Flight, Space, Sky View, Weather and all other pages are unchanged.

Version 3.6.23 / versionCode 83.
