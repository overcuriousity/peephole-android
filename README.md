# peephole-android

Android companion app for [peephole](https://github.com/overcuriousity/peephole):
pair with a peephole node by QR code, browse its IP dataset, and run lookups,
probes and scans from your phone.

Status: under construction (v1). The screen design is in
[`docs/design/android-v1.md`](docs/design/android-v1.md); the server API is
[`docs/api-v1.md`](https://github.com/overcuriousity/peephole/blob/master/docs/api-v1.md)
in the peephole repo.

## Privacy

No analytics, no crash reporting, no trackers, no Google Play services. The
app talks only to the peephole instances you pair with. Device tokens are
encrypted with a non-exportable Android Keystore key, are never logged, and
are excluded from backup and device transfer.

## Pairing and TLS

On a peephole node, open **Admin → Devices → Pair a device** and scan the QR
code. The app shows the origin it is about to trust, then redeems the
single-use code with `POST /api/v1/pair`. Only `https://` origins are
accepted. When the QR carries `tls_spki_sha256`, every connection to that
instance must present a leaf certificate with that SubjectPublicKeyInfo, on
top of normal certificate validation; a mismatch stops before the pairing
code or token is sent.

## Dependencies

Runtime (all open source, from Google Maven and Maven Central):

| Library | Purpose |
|---|---|
| AndroidX Core KTX, Activity Compose, Navigation Compose, Lifecycle (ViewModel/Runtime Compose) | App shell |
| Jetpack Compose (BOM) UI and Material 3 | UI |
| CameraX (camera2, lifecycle, view) | Camera preview for QR scanning |
| ZXing core | On-device QR decoding (no ML Kit, no Play services) |
| OkHttp | HTTPS client, SPKI pinning hook |
| kotlinx.serialization JSON | API payloads |
| kotlinx.coroutines | Background work |

Test only: JUnit 4, OkHttp MockWebServer 3 and okhttp-tls.

Exact versions are in [`gradle/libs.versions.toml`](gradle/libs.versions.toml),
and every artifact's SHA-256 is checked against
[`gradle/verification-metadata.xml`](gradle/verification-metadata.xml).

## Building

Builds run in GitHub Actions; see `.github/workflows/`.

- **Pull requests**: build, lint, unit tests, and an unsigned debug APK as
  the `peephole-debug-apk` artifact.
- **Tags `vMAJOR.MINOR.PATCH`**: a release APK signed with the owner's key
  (repository secrets `ANDROID_KEYSTORE_B64`, `ANDROID_KEYSTORE_PASSWORD`,
  `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`), published with its SHA-256
  on a GitHub Release. If any secret is missing the workflow fails and
  publishes nothing.
- **After changing a dependency**: run the CI workflow manually with
  *write-verification-metadata*, review the uploaded file, and commit it.

Locally: `./gradlew assembleDebug testDebugUnitTest` with JDK 17+ and the
Android SDK (compileSdk 37). minSdk is 26 (Android 8.0).

## License

Licensed under either of [Apache License, Version 2.0](LICENSE-APACHE) or
[MIT License](LICENSE-MIT), at your option.
