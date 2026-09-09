# Contributing

Small, focused improvements are welcome. Open an issue with your use case before
adding a new dependency, permission, or service.

Follow the build instructions in the README. Keep routing explicit, avoid
logging media URIs or camera extras, and preserve the lock-screen boundary.
For behavior changes, test the real Pixel Camera thumbnail as well as launching
the app icon. Include Android, Pixel Camera, and Gallery versions in your PR;
identify checks you could not run. Run `python3 tests/run.py --device` on an
unlocked test device after building to check picker and routing behavior.

Never commit `.signing/`, APKs, device logs, screenshots of private photos, or
local account information. Local builds generate a private signing key; they
cannot update the official release unless signed by the same maintainer key.

By contributing, you agree to license your contributions under the ISC license.
