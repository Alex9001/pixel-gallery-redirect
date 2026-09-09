#!/usr/bin/env python3
"""Compare unsigned builds in different paths, timezones and input timestamps."""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
hashes = []
with tempfile.TemporaryDirectory(prefix='gallery-reproducible-') as temporary:
    for index, timezone in enumerate(('UTC', 'Pacific/Honolulu')):
        checkout = Path(temporary) / ('first' if index == 0 else 'different/second')
        checkout.mkdir(parents=True)
        for name in ('build.py', 'AndroidManifest.xml'):
            shutil.copyfile(root / name, checkout / name)
        shutil.copytree(root / 'src', checkout / 'src')
        (checkout / 'images').mkdir()
        shutil.copyfile(root / 'images/pixel-gallery-redirect-icon.png',
                        checkout / 'images/pixel-gallery-redirect-icon.png')
        for path in checkout.rglob('*'):
            os.utime(path, (946684800 + index * 86400, 946684800 + index * 86400))
        subprocess.run([sys.executable, 'build.py', '--unsigned'], cwd=checkout,
                       env=dict(os.environ, TZ=timezone), check=True)
        assert not (checkout / '.signing').exists(), 'Unsigned builds must not create keys'
        apk = checkout / 'build/pixel-gallery-redirect-unsigned.apk'
        hashes.append(hashlib.sha256(apk.read_bytes()).hexdigest())
    assert hashes[0] == hashes[1], f'Builds differ: {hashes}'
print('PASS: reproducible unsigned APK across paths, input timestamps and timezones:', hashes[0])
