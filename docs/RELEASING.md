# Release guide

Mimi Reader can produce a minified unsigned release without access to signing secrets. A local `keystore.properties` file enables signing for both APK and App Bundle outputs. The keystore and its properties are ignored by Git and must never be committed.

## Single free application

There are no separate free/paid products. Both modules use only standard
`debug` and `release` variants; the old `free`, `paid` and library-only `amazon`
flavors have been removed. Release keeps `com.emogoth.android.phone.mimi` and
debug keeps `com.emogoth.android.phone.mimi.beta`, with the same signing setup.
`./gradlew :mimi-app:verifyApplicationVariants` checks both application IDs
against the actual AGP variants and also runs through `check` and Android CI.

Removing a flavor does not require uninstalling the existing free application.
Use an in-place update with the same certificate; do not clear application data.

## Versioning

The source defaults are `versionCode 230` and `versionName 7.0.1`. Override both values for a release without editing the build file:

```powershell
.\gradlew.bat :mimi-app:assembleRelease :mimi-app:bundleRelease -PmimiVersionCode=231 -PmimiVersionName=7.0.2
```

`mimiVersionCode` must be a positive integer and must increase for every published update. `mimiVersionName` is the user-facing release number.

## Unsigned artifacts

When `keystore.properties` is absent, the release tasks generate minified, resource-shrunk artifacts suitable for CI verification or later signing:

- `mimi-app/build/outputs/apk/release/`
- `mimi-app/build/outputs/bundle/release/`
- `mimi-app/build/outputs/mapping/release/mapping.txt`

GitHub Actions builds these artifacts on every run, includes their SHA-256 checksums, and retains them for 14 days. CI artifacts are unsigned and must not be distributed as an installable release.

## Signed artifacts

1. Keep the release keystore outside the repository, or use the ignored `release.keystore` path at the repository root.
2. Copy `keystore.properties.example` to `keystore.properties` and replace all placeholders. Relative `storeFile` paths are resolved from the repository root; absolute paths are also accepted.
3. Run the release command from the repository root.
4. Preserve the generated R8 `mapping.txt` with the matching release. It is required to decode obfuscated stack traces.

The build fails early if the properties file exists but any required signing value is empty. Verify a signed APK with the Android SDK tool before distribution:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs mimi-app\build\outputs\apk\release\mimi-app-release.apk
```

Install the signed APK as an update on a device that already contains the previous release. Android will reject it if the signing certificate differs, which protects existing installations from an unintended key change.

## Next release gate

The next public version is **8.0**, reflecting the unusually large scope of the modernization. Its changelog must cover the complete history since **7.0.1**, where this fork began. Do not start drafting that changelog until the project owner has been consulted and has agreed its scope and format.

## Release checklist

- Agree and complete the 8.0 changelog before preparing public artifacts.
- Run unit tests, lint, and both release artifact tasks.
- Resolve the PDF/GIF native alignment findings in [the dependency audit](DEPENDENCIES.md#native-audit-a-separate-pre-release-requirement).
  Check both ELF load-segment and APK ZIP alignment and exercise playback/rendering
  in a 16 KB environment; a green API 36 job or ZIP check alone is insufficient.
  Retain Android 10/12 and 32-bit compatibility while replacing native integrations.
- Verify the dependency snapshots with `verifySbom testSbomValidation` and generate
  a release-version SBOM as described in [the SBOM guide](SBOM.md).
- Confirm the version code and version name in the built artifact.
- Verify the APK signature and record a SHA-256 checksum.
- Smoke-test startup, tabs, gallery playback, cache clearing, and a SAF download.
- Test an in-place update to confirm that preferences and the Room database remain intact.
- Archive the APK/AAB, `mapping.txt`, checksum, matching SBOM, changelog, and signing-key backup securely.
