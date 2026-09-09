#!/usr/bin/env python3
"""Build the platform-only instrumentation APK; optionally run on an unlocked device.
Run build.py first. Uses the same local signing key; never creates a new key.
"""
import os
from pathlib import Path
import shutil
import subprocess
import sys
import zipfile

ROOT = Path(__file__).resolve().parent.parent
CACHE = Path.home() / '.cache/pixel-gallery-android'
TOOLS = Path(os.environ.get('ANDROID_BUILD_TOOLS', CACHE / 'tools/android-16'))
ANDROID = Path(os.environ.get('ANDROID_JAR', CACHE / 'platform/android-36/android.jar'))
java_home = os.environ.get('BUILD_JAVA_HOME') or os.environ.get('JAVA_HOME')
JAVA = Path(java_home) if java_home and (Path(java_home) / 'bin/java').is_file() else Path(shutil.which('java')).resolve().parent.parent
BUILD = ROOT / 'build/tests'
BUILD.mkdir(parents=True, exist_ok=True)
for name in ('classes', 'dex'):
    shutil.rmtree(BUILD / name, ignore_errors=True)
    (BUILD / name).mkdir()
env = dict(os.environ, JAVA_HOME=str(JAVA), PATH=str(JAVA / 'bin') + ':' + os.environ['PATH'])
def run(*args):
    subprocess.run([str(arg) for arg in args], cwd=ROOT, env=env, check=True)
run(TOOLS / 'aapt2', 'link', '-I', ANDROID, '--manifest', ROOT / 'tests/AndroidManifest.xml', '-o', BUILD / 'resources.apk')
run(JAVA / 'bin/javac', '--release', '8', '-classpath', str(ANDROID) + ':' + str(ROOT / 'build/classes'), '-d', BUILD / 'classes', *sorted((ROOT / 'tests/src').rglob('*.java')))
run(TOOLS / 'd8', '--min-api', '29', '--lib', ANDROID, '--classpath', ROOT / 'build/classes', '--output', BUILD / 'dex', *sorted((BUILD / 'classes').rglob('*.class')))
with zipfile.ZipFile(BUILD / 'resources.apk') as source, zipfile.ZipFile(BUILD / 'unsigned.apk', 'w') as target:
    for item in source.infolist():
        target.writestr(item, source.read(item.filename))
    target.write(BUILD / 'dex/classes.dex', 'classes.dex')
run(TOOLS / 'zipalign', '-f', '4', BUILD / 'unsigned.apk', BUILD / 'aligned.apk')
run(TOOLS / 'apksigner', 'sign', '--ks', ROOT / '.signing/redirect.jks', '--ks-key-alias', 'redirect', '--ks-pass', 'file:' + str(ROOT / '.signing/password'), '--out', BUILD / 'tests.apk', BUILD / 'aligned.apk')
if '--device' in sys.argv:
    run('adb', 'install', '-r', ROOT / 'pixel-gallery-redirect.apk')
    run('adb', 'install', '-r', BUILD / 'tests.apk')
    try:
        result = subprocess.check_output(['adb', 'shell', 'am', 'instrument', '-w', 'org.pixelgalleryredirect.tests/com.google.android.apps.photos.pager.GalleryTests'], text=True, timeout=180)
        print(result)
        if 'PASS:' not in result or 'FAIL' in result:
            raise SystemExit('Instrumentation tests failed')
    finally:
        run('adb', 'uninstall', 'org.pixelgalleryredirect.tests')
