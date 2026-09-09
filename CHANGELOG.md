# Changelog

## 1.1.0 — prepared 2026-09-08

- Remember a preferred installed media viewer in the current Android profile.
- Add a native picker with app icons, media categories, and the current choice.
- Offer Ask every time from the app icon; camera selections then apply only to that preview.
- Always show the picker from the launcher; ask once on first Camera use, including upgrades from v1.0.0.
- Resolve photo/video activities separately and fall back to the chosen app’s main screen or picker.
- Preserve pending requests across activity recreation and retain the unlock requirement.
- Add on-device selection/routing regression tests and Android 10 emulator CI.
- Preserve package/activity identity, Android 10 minimum, supplied artwork, ISC attribution, and release signing key.
- Version code 3.

## 1.0.0 — 2026-09-08

- Initial public release under the ISC license.
- Redirect Pixel Camera photo-review requests to GrapheneOS Gallery.
- Forward image/video content URIs with temporary read access.
- Open Gallery home when no supported media URI is supplied or direct launch fails.
- Require unlocking before opening Gallery.
- Add the custom Pixel Gallery Redirect launcher icon.
- Include a standalone SDK build, signature verification, and build CI.
