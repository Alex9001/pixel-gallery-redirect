#!/usr/bin/env python3
"""Start CI's AVD, report startup failures, and run tests on that emulator only."""
import os
from pathlib import Path
import subprocess
import sys
import time

sdk = Path(os.environ['ANDROID_HOME'])
env = dict(os.environ, ANDROID_SERIAL='emulator-5554')
log = Path('/tmp/redirect-emulator.log')
with log.open('w') as output:
    emulator = subprocess.Popen([
        str(sdk / 'emulator/emulator'), '-avd', 'redirect-test', '-port', '5554',
        '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot',
        '-gpu', 'swiftshader_indirect',
    ], stdout=output, stderr=subprocess.STDOUT, env=env)
    try:
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            if emulator.poll() is not None:
                raise RuntimeError(f'Emulator exited before boot: {emulator.returncode}')
            boot = subprocess.run(['adb', 'shell', 'getprop', 'sys.boot_completed'],
                                  env=env, capture_output=True, text=True, timeout=15)
            if boot.returncode == 0 and boot.stdout.strip() == '1':
                break
            time.sleep(2)
        else:
            raise TimeoutError('Emulator did not boot within 240 seconds')
        subprocess.run(['adb', 'shell', 'input', 'keyevent', '82'], env=env, check=True)
        subprocess.run([sys.executable, 'tests/run.py', '--device'], env=env, check=True)
    finally:
        if emulator.poll() is None:
            emulator.terminate()
            try:
                emulator.wait(timeout=15)
            except subprocess.TimeoutExpired:
                emulator.kill()
                emulator.wait()
        print(log.read_text(), flush=True)
