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
