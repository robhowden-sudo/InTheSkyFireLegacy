# Changelog

This file replaces the collection of per-build `V*_CHANGES.md` notes that accumulated during development. Git history remains the detailed record of individual iterations.

## v3.6.23

Current tested Fire HD Legacy baseline.

- Fixed Launches single-selection highlighting.
- Correctly highlights the initially selected mission.
- Preserved double-tap mission selection and image-request behaviour.
- Improved Launch Library image parsing for direct URLs and nested image objects.
- Added mission and rocket-configuration image fallbacks.
- Added NASA Images fallback when primary launch imagery is unavailable.
- Discards late image responses after the user changes mission selection.
- Shows a visible unavailable state when no suitable image can be found.
- Retains the established Radar, Flight, Weather, Time, Space, Sky View and Launches feature set.

## v3.6.x

The 3.6 series focused on stabilising the Windows Store parity layout for legacy Android and Fire hardware, improving page behaviour, data fallbacks, maps, Sky View, space information, launch tracking and always-on dashboard use.

## v3.5.x

The 3.5 series established the main Fire Legacy feature baseline, including live aircraft tracking, selected-flight information, weather, time, space, Sky View, launches and the GitHub Actions Android build workflow.

## Earlier development

Earlier 2.x and 3.x builds were rapid development iterations while the Fire Legacy interface and data architecture were taking shape. Their individual notes have been removed from the repository root to keep the project readable. The full commit history remains available in GitHub for detailed archaeology, should anyone feel unusually brave.
