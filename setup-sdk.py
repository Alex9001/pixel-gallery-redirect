#!/usr/bin/env python3
"""Download pinned official SDK components to a task-specific cache."""
import concurrent.futures
import hashlib
import io
from pathlib import Path
import urllib.request
import zipfile

BASE = Path.home() / '.cache/pixel-gallery-android'
PACKAGES = [
    ('platform-36_r02.zip', '2c1a80dd4d9f7d0e6dd336ec603d9b5c55a6f576', 'platform'),
    ('build-tools_r36_linux.zip', 'b0b6376977657e8ad9b969bacf4093601da2c6fb', 'tools'),
]

def fetch(spec):
    name, expected, folder = spec
    data = urllib.request.urlopen('https://dl.google.com/android/repository/' + name).read()
    # Checksums published in Google's repository2-1.xml; transport uses HTTPS.
    if hashlib.sha1(data).hexdigest() != expected:
        raise RuntimeError('SDK checksum mismatch: ' + name)
    destination = BASE / folder
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for item in archive.infolist():
            path = (destination / item.filename).resolve()
            if not path.is_relative_to(destination.resolve()):
                raise RuntimeError('Unsafe SDK archive path')
        archive.extractall(destination)
        for item in archive.infolist():
            mode = item.external_attr >> 16
            if mode:
                (destination / item.filename).chmod(mode & 0o777)
    print(folder + ' ready')

if __name__ == '__main__':
    BASE.mkdir(parents=True, exist_ok=True)
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as executor:
        list(executor.map(fetch, PACKAGES))
