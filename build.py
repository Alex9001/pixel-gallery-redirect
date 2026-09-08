#!/usr/bin/env python3
"""Build a tiny dependency-free APK using official Android SDK build tools."""
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import zipfile

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
KEYS.mkdir(parents=True, exist_ok=True)
KEYS.chmod(0o700)
env = dict(os.environ, JAVA_HOME=str(JAVA), PATH=str(JAVA / 'bin') + ':' + os.environ['PATH'])

def run(*args):
    subprocess.run([str(a) for a in args], cwd=ROOT, env=env, check=True)

password = KEYS / 'password'
keystore = KEYS / 'redirect.jks'
if not keystore.exists():
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
run(JAVA / 'bin/javac', '--release', '8', '-classpath', ANDROID,
    '-d', BUILD / 'classes', *sorted((ROOT / 'src').rglob('*.java')))
run(TOOLS / 'd8', '--release', '--min-api', '29', '--lib', ANDROID,
    '--output', BUILD / 'dex', *sorted((BUILD / 'classes').rglob('*.class')))
with zipfile.ZipFile(BUILD / 'resources.apk') as source, \
        zipfile.ZipFile(BUILD / 'unsigned.apk', 'w', zipfile.ZIP_DEFLATED) as target:
    for item in source.infolist():
        target.writestr(item, source.read(item.filename))
    target.write(BUILD / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-f', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', keystore, '--ks-key-alias', 'redirect',
    '--ks-pass', 'file:' + str(password),
    '--out', ROOT / 'pixel-gallery-redirect.apk', BUILD / 'aligned.apk')
run(TOOLS / 'apksigner', 'verify', '--verbose', ROOT / 'pixel-gallery-redirect.apk')
run(TOOLS / 'zipalign', '-c', '4', ROOT / 'pixel-gallery-redirect.apk')
print(ROOT / 'pixel-gallery-redirect.apk')
