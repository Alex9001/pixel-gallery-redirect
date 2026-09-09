#!/usr/bin/env python3
"""Verify and stage official release assets without publishing or changing Git."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
TOOLS = Path(os.environ.get('ANDROID_BUILD_TOOLS', Path.home() / '.cache/pixel-gallery-android/tools/android-16'))
CERT = 'b0a8dcc2d221d6dc91da4bf0a4c3d9bc61a3f412fb291608e76160f4cfd1dd51'
APK = ROOT / 'pixel-gallery-redirect.apk'
for required in (ROOT / '.signing/redirect.jks', ROOT / '.signing/password', APK):
    if not required.is_file():
        raise SystemExit('Missing retained signing key or built APK; do not generate a replacement release key.')
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
assert manifest.get(ns + 'versionName') == '1.1.0' and manifest.get(ns + 'versionCode') == '3'
assert not manifest.findall('uses-permission')
certs = subprocess.check_output([str(TOOLS / 'apksigner'), 'verify', '--verbose', '--print-certs', str(APK)], text=True)
assert 'Signer #1 certificate SHA-256 digest: ' + CERT in certs, 'Release certificate changed'
subprocess.run([str(TOOLS / 'zipalign'), '-c', '4', str(APK)], check=True)
badging = subprocess.check_output([str(TOOLS / 'aapt2'), 'dump', 'badging', str(APK)], text=True)
assert "versionCode='3'" in badging and "versionName='1.1.0'" in badging
assert "package: name='com.google.android.apps.photos'" in badging
assert "minSdkVersion:'29'" in badging
permissions = subprocess.check_output([str(TOOLS / 'aapt2'), 'dump', 'permissions', str(APK)], text=True)
assert 'uses-permission' not in permissions
with zipfile.ZipFile(APK) as apk:
    icon = next(name for name in apk.namelist() if name.startswith('res/') and name.endswith('/ic_launcher.png'))
    # aapt2 recompresses PNGs; compare packaged artwork with the v1.0.0 release.
    assert hashlib.sha256(apk.read(icon)).hexdigest() == 'fd85b6f37484b66fcc166e16b14c01b8330b0268a3cc222840faa8d6f7092249', 'Packaged artwork changed'
    assert hashlib.sha256((ROOT / 'images/pixel-gallery-redirect-icon.png').read_bytes()).hexdigest() == 'd02c73e33eaec46226937624350b2260d3dde29e13d5d2336d5282b351265d02', 'Source artwork changed'
folder = ROOT / 'build/release/v1.1.0'
folder.mkdir(parents=True, exist_ok=True)
shutil.copyfile(APK, folder / APK.name)
(folder / 'SHA256SUMS').write_text(hashlib.sha256(APK.read_bytes()).hexdigest() + '  ' + APK.name + '\n')
(folder / 'SIGNING-CERTIFICATE.txt').write_text('Pixel Gallery Redirect — official release signing certificate\n\nSHA-256: ' + CERT + '\n\nVerify with Android SDK build-tools:\n  apksigner verify --print-certs pixel-gallery-redirect.apk\n\nThis is the certificate fingerprint, not the APK file checksum.\nThe private key is retained locally and is not published.\n')
(folder / 'RELEASE-NOTES.md').write_text('''Choose your preferred gallery once; future Pixel Camera previews open it directly.

- Native picker with icons, photo/video categories, and the current choice.
- Open the redirect app icon to change your gallery or select Ask every time.
- In Ask every time mode, camera selections apply only to the current preview.
- New installations and upgrades from v1.0.0 ask on the next camera preview.
- Compatible installed viewers include GrapheneOS Gallery, Aves, and Fossify.
- Separate photo/video activity resolution; fallback to the chosen app’s main screen, then the picker if unavailable.
- Choice stays in the current Android profile and does not change global defaults.
- Android 10+, no declared permissions or third-party runtime dependencies.
- Same signing key, supplied artwork, package/activity identity, and ISC attribution.

Install the attached APK as an in-place update. Complete any first-run setup required by your chosen gallery.

See VALIDATION.md in the release source for completed checks and remaining device scenarios. Download SHA256SUMS beside the APK and run `sha256sum -c SHA256SUMS`. SIGNING-CERTIFICATE.txt identifies the retained official certificate.
''')
print('Verified release assets:', folder)
