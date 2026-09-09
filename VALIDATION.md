# Validation — 2026-09-08

## Initial private build (version code 1)

- Device: Pixel 9a, GrapheneOS, Android API 37, owner profile.
- Pixel Camera: 10.4.117.936816638.14.
- Target gallery: `com.android.gallery3d/.app.GalleryActivity` (installed and
  registered for image viewing); videos resolve to its movie activity.
- Reproduced the camera's original **Photos required** dialog through UI hierarchy.
- Built a 13 KB APK with Android SDK 36, no third-party runtime dependencies.
- `apksigner verify --verbose`: passed, v3 signature.
- `zipalign -c 4`: passed.
- `aapt2 dump permissions`: no declared permissions. GrapheneOS adds its own
  `OTHER_SENSORS` permission at installation; no network or media permission.
- `adb install`: success.
- Package-specific `android.provider.action.REVIEW` with a content URI and
  `image/jpeg` resolves to `.pager.HostPhotoPagerActivity`.
- Started the redirect while the phone was locked. Keyguard remained visible;
  Gallery was not resumed, and the app logged no launch or crash.
- Python build/setup scripts compile successfully.

Pending: actual thumbnail-to-Gallery test after the user unlocks the phone;
selected-photo preview, video preview, newly processing photo behavior, and
back-navigation behavior have not yet been validated.

Installed APK SHA-256:
`d13c985a3a8aad0b71da02a5e173046de27433248ad3f74fca35361e56a74e47`


## Public release 1.0.0 (version code 2)

- Supplied icon compiled into `res/mipmap-xxxhdpi-v4/ic_launcher.png`;
  `aapt2 dump badging` confirms it is the application icon.
- Build completed; APK v3 signature and ZIP alignment verified.
- Manifest declares no permissions; no runtime Java changes since the initial build.
- Signing key retained from the initial installed build, allowing in-place updates.
- APK size: 852,454 bytes (approximately 833 KiB, including artwork).
- Phone disconnected during release preparation; this icon update has not been
  installed on the initial device. Earlier pending behavior checks remain pending.

Release APK SHA-256: `12f5c7eece88e5fc350e3e86660142ca9cfa3e317f5841cdf90f1ec1b5b5c295`

## v1.1.0 preparation (version code 3)

Tested on the connected Pixel 9a, GrapheneOS API 37, owner profile, on
2026-09-08. The update installed in place over the existing redirect.

### Automated device tests

`python3 tests/run.py --device` passed **86 assertions** on the final production
build. The platform-only instrumentation APK adds a temporary viewer with separate
photo/video activities; it restores preferences and removes itself afterward.
It covers:

- Discovery, package deduplication, media-category merging, sorting, and excluding
  the redirect itself.
- No silent default, first camera picker, selection persistence, launcher-only
  confirmation, and cancellation preserving both unset and existing choices.
- Activity recreation while selecting, retaining the pending video, and one launch.
- Photo/video-specific activity resolution, SEND streams, panorama MIME
  normalization, URI/MIME preservation, ClipData and read grants, and dropping
  camera extras/task flags.
- Missing URI/provider, unsupported MIME, non-content URI, rejected direct launch,
  rejected home fallback, and an unavailable saved package.
- Ask every time, two consecutive camera selections without saving a fixed
  package, cancellation preserving ask mode, and switching back to a fixed gallery.

Launch rejection tests inject an Android SecurityException through an
Instrumentation activity monitor. They verify redirect behavior, not a gallery’s
response after Android has already accepted a launch. A gallery that accepts a URI
but then fails to decode it controls its own error UI.

CI retains signature, alignment, permission, and icon checks and now runs these
tests on an Android 10 emulator. The new CI workflow has not yet run remotely;
local execution used the physical API 37 device.

### Real app checks

| App | Version | Results |
|---|---|---|
| GrapheneOS Gallery | 1.1.40030 | Listed once with photos/videos; synthetic PNG opened in GalleryActivity; MP4 launched MovieActivity (one-second clip finishes quickly). Real Pixel Camera thumbnail opened Gallery; Back returned to Camera. |
| Aves Libre | 1.15.0 | Listed once with photos/videos; synthetic PNG and MP4 opened. Actual Camera selection in ask mode opened media; Back returned to Camera. |
| Fossify Gallery | 1.13.1 | Listed once with photos/videos; synthetic PNG and MP4 opened in ViewPagerActivity. |

Aves Libre and Fossify were installed from their official GitHub release APKs for
these checks. Complete any onboarding required by a chosen gallery. Discovery is
based on advertised intents, not a hardcoded app list. Source manifests:
[Aves](https://github.com/deckerst/aves/blob/develop/android/app/src/main/AndroidManifest.xml),
[Fossify](https://github.com/FossifyOrg/Gallery/blob/main/app/src/main/AndroidManifest.xml).
Android’s [package visibility guidance](https://developer.android.com/training/package-visibility/declaring)
describes the narrowly scoped intent queries used here.

The saved Aves selection survived force-stopping and restarting the redirect.
The real Camera thumbnail showed the ask-mode picker; Cancel returned to Camera;
choosing Aves and returning with Back preserved Ask every time.

Temporarily disabling the selected Aves app excluded it from discovery and showed
the recovery picker with an explanation. Cancel preserved the selection; restoring
the app to its original enabled state restored its current-choice marker.

On the final build, a secure-review request while locked left keyguard showing,
input restricted, and no gallery or picker visible. The test request was cancelled.
Synthetic media and the temporary instrumentation fixture were removed. Aves Libre
and Fossify remain installed; the redirect is left in Ask every time mode.

### Release verification

- APK v3 signature and ZIP alignment passed.
- Official certificate matches v1.0.0:
  `b0a8dcc2d221d6dc91da4bf0a4c3d9bc61a3f412fb291608e76160f4cfd1dd51`.
- APK package/activity identity and Android 10 minimum preserved; version 1.1.0,
  version code 3.
- No declared permissions, services, or third-party runtime dependencies added.
- Source artwork unchanged; compiled icon checked against the v1.0.0 APK.
- ISC attribution retained.
- `prepare-release.py` stages the APK, checksums, certificate, and release notes
  in `build/release/v1.1.0/`. No GitHub release has been published for this work.

Remaining manual coverage: an environment with no compatible viewers; broad
format/RAW/HDR/processing compatibility; Pixel Camera’s actual video thumbnail
and Fossify Back behavior; background/task switching and process death while the picker is open;
other Android user profiles; Android 10 runtime until CI runs. Rotation/recreation
is covered on device; the device tests are not a complete format compatibility
suite.

Prepared APK SHA-256:
`21ca9cf8d0c0565e8fa97b76f3857db2d41d06ef053d7f18e12ec70cb514a2d8`
