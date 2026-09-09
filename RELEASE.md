# Release preparation

v1.1.0 uses version code 3. Keep the package/activity identity, supplied artwork,
ISC attribution, and the existing official signing key. The previous official
certificate SHA-256 is:

`b0a8dcc2d221d6dc91da4bf0a4c3d9bc61a3f412fb291608e76160f4cfd1dd51`

1. Run `python3 build.py`, then `python3 tests/run.py --device` on an unlocked
   test device. Review `VALIDATION.md` and record any remaining manual checks.
2. Run `python3 prepare-release.py`. This requires the retained key and verifies
   the APK certificate against the fingerprint above, manifest version,
   signature, ZIP alignment, permission declarations, and source artwork and packaged icon against v1.0.0.
   It stages the APK, SHA256SUMS, SIGNING-CERTIFICATE.txt, and release notes under
   `build/release/v1.1.0/`. It does not publish or create a Git tag.
3. Commit and push the reviewed source. Use that exact commit for the release:

   ```sh
   gh release create v1.1.0 --draft --target "$(git rev-parse HEAD)" \
     --title 'v1.1.0 — Remember your gallery' \
     --notes-file build/release/v1.1.0/RELEASE-NOTES.md \
     build/release/v1.1.0/pixel-gallery-redirect.apk \
     build/release/v1.1.0/SHA256SUMS \
     build/release/v1.1.0/SIGNING-CERTIFICATE.txt
   ```

4. Review the draft, source commit, validation limitations, and attached assets
   before publishing through GitHub. CI uses a temporary key; its APK cannot
   replace the separately signed official release artifact.

Do not commit signing material, APKs, or private device output. A self-built APK
with a different certificate cannot update the official release in place.
