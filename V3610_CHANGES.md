# v3.6.10

- Corrected OpenSky development credential handling.
- The supplied OpenSky Client ID and Client Secret are now forcibly written to SharedPreferences on app startup.
- This prevents stale credentials saved by earlier builds from overriding the intended values.
- Settings -> DATA continues to show the secret visibly.
- OpenSky validation remains enabled.
- Version 3.6.10 / versionCode 70.

Security: this development build contains embedded OpenSky credentials and should remain private.
