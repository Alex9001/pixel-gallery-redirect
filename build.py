#!/usr/bin/env python3
"""Build a tiny dependency-free APK using official Android SDK build tools."""
import argparse
import hashlib
import json
import os
import platform
from pathlib import Path
import secrets
import shutil
import subprocess
import zipfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--unsigned', action='store_true', help='Build reproducible unsigned APK without accessing signing keys')
args = parser.parse_args()

ROOT = Path(__file__).resolve().parent
CACHE = Path.home() / '.cache/pixel-gallery-android'
TOOLS = Path(os.environ.get('ANDROID_BUILD_TOOLS', CACHE / 'tools/android-16'))
ANDROID = Path(os.environ.get('ANDROID_JAR', CACHE / 'platform/android-36/android.jar'))
java_home = os.environ.get('BUILD_JAVA_HOME') or os.environ.get('JAVA_HOME')
if java_home and (Path(java_home) / 'bin/java').is_file():
    JAVA = Path(java_home)
else:
    java_binary = shutil.which('java')
    if not java_binary:
        raise SystemExit('Install a JDK (21+ recommended) or set BUILD_JAVA_HOME.')
    JAVA = Path(java_binary).resolve().parent.parent
BUILD = ROOT / 'build'
KEYS = ROOT / '.signing'
for folder in (BUILD / 'classes', BUILD / 'dex', BUILD / 'res/mipmap-xxxhdpi'):
    if folder.exists():
        shutil.rmtree(folder)
    folder.mkdir(parents=True, exist_ok=True)
env = dict(os.environ, JAVA_HOME=str(JAVA), PATH=str(JAVA / 'bin') + ':' + os.environ['PATH'])

def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, env=env, check=True)

password = KEYS / 'password'
keystore = KEYS / 'redirect.jks'
if not args.unsigned:
    KEYS.mkdir(parents=True, exist_ok=True)
    KEYS.chmod(0o700)
if not args.unsigned and not keystore.exists():
    password.write_text(secrets.token_urlsafe(32))
    password.chmod(0o600)
    run(JAVA / 'bin/keytool', '-genkeypair', '-keystore', keystore,
        '-storepass:file', password, '-keypass:file', password,
        '-alias', 'redirect', '-dname', 'CN=Pixel Gallery Redirect Local Build',
        '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000')
    keystore.chmod(0o600)
shutil.copyfile(ROOT / 'images/pixel-gallery-redirect-icon.png',
                BUILD / 'res/mipmap-xxxhdpi/ic_launcher.png')
run(TOOLS / 'aapt2', 'compile', '--dir', BUILD / 'res', '-o', BUILD / 'compiled-res.zip')
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', ROOT / 'AndroidManifest.xml',
    '-o', BUILD / 'resources.apk', BUILD / 'compiled-res.zip')
run(JAVA / 'bin/javac', '--release', '8', '-encoding', 'UTF-8', '-classpath', ANDROID,
    '-d', BUILD / 'classes', *sorted((ROOT / 'src').rglob('*.java')))
run(TOOLS / 'd8', '--release', '--min-api', '29', '--lib', ANDROID,
    '--output', BUILD / 'dex', *sorted((BUILD / 'classes').rglob('*.class')))
with zipfile.ZipFile(BUILD / 'resources.apk') as source, \
        zipfile.ZipFile(BUILD / 'unsigned.apk', 'w') as target:
    entries = {name: source.read(name) for name in source.namelist()}
    entries['classes.dex'] = (BUILD / 'dex/classes.dex').read_bytes()
    for name, data in sorted(entries.items()):
        # Canonical metadata, ordering and storage avoid wall-clock timestamps,
        # filesystem permissions and zlib-version differences in APK bytes.
        item = zipfile.ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
        item.create_system = 3
        item.external_attr = 0o100644 << 16
        item.compress_type = zipfile.ZIP_STORED
        target.writestr(item, data)
run(TOOLS / 'zipalign', '-f', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
unsigned = BUILD / 'pixel-gallery-redirect-unsigned.apk'
shutil.copyfile(BUILD / 'aligned.apk', unsigned)
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
(BUILD / 'UNSIGNED-SHA256SUMS').write_text(digest(unsigned) + '  ' + unsigned.name + '\n')
toolchain = {
    'python': platform.python_version(),
    'javac': subprocess.check_output([str(JAVA / 'bin/javac'), '-version'], env=env, text=True).strip(),
    'sha256': {name: digest(path) for name, path in {
        'android.jar': ANDROID, 'aapt2': TOOLS / 'aapt2',
        'd8.jar': TOOLS / 'lib/d8.jar', 'zipalign': TOOLS / 'zipalign',
    }.items()},
    'unsignedApkSha256': digest(unsigned),
}
(BUILD / 'BUILD-INFO.json').write_text(json.dumps(toolchain, indent=2, sort_keys=True) + '\n')
if args.unsigned:
    run(TOOLS / 'zipalign', '-c', '4', unsigned)
    print(unsigned)
    raise SystemExit(0)
run(TOOLS / 'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', 'redirect',
    '--ks-pass', 'file:' + str(password),
    '--out', ROOT / 'pixel-gallery-redirect.apk', BUILD / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', '--verbose', ROOT / 'pixel-gallery-redirect.apk')
run(TOOLS / 'zipalign', '-c', '4', ROOT / 'pixel-gallery-redirect.apk')
print(ROOT / 'pixel-gallery-redirect.apk')
