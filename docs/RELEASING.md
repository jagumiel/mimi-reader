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

The approved 8.0 preparation uses `versionCode 231` and `versionName 8.0`.
Both defaults live in root `gradle.properties` and are read by the app and SBOM
without separate fallbacks. This identity is not a publication or tag. Build it
with the defaults, or override both properties together when deliberately
preparing a different artifact identity:

```powershell
.\gradlew.bat :mimi-app:assembleRelease :mimi-app:bundleRelease
# Equivalent explicit identity (use these same properties for generateSbom):
.\gradlew.bat generateSbom '-PmimiVersionCode=231' '-PmimiVersionName=8.0'
```

`mimiVersionCode` must be a positive integer and must increase for every published update. `231` exceeds the inherited `230`; no greater code appears in the reviewed fork history. `mimiVersionName` is the user-facing release number. Publication must also account for any independently distributed artifact not represented by this repository.

The owner approved the English [CHANGELOG.md](../CHANGELOG.md) and matching
in-app HTML on 25 October 2026, covering all modernization since the inherited
7.0.1 baseline. Both say **Unreleased**. See the [history audit](CHANGELOG_AUDIT.md).
CI's offline helper tests compare their sections, shared identity and tracked
SBOM, while document instrumentation checks current and historical notes.

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

The archived internal 7.0.1 / 230 test package and per-device checklist are in
[RELEASE_ACCEPTANCE.md](RELEASE_ACCEPTANCE.md). It passed fresh installation and
cold start on Android 12, preserving the existing `Pictures/Mimi` folder, but
does not validate the new 8.0 / 231 artifacts or a signed in-place update. The
owner has deferred physical Android 10 testing, not marked it passed. Keep
visual/manual release and update/migration checks explicit.

No public signing key has been configured or approved by this versioning task.
The internal public test key is not a distribution identity. Unsigned output
must retain its own exact mapping/SBOM; signing requires an explicit key choice
and certificate-compatible installation without uninstalling or clearing data.
Do not delete or rename the owner's `Pictures/Mimi` folder for release checks.

## Release checklist

- Review the approved 8.0 changelog against any further changes before publication; assign an actual release date only when publishing.
- Run unit tests, lint, and both release artifact tasks.
- Require native ELF/APK checks and the dedicated six-case API 36 ps16k job to
  pass from the release commit, with actual 16384-byte pages and backcompat disabled.
  For application source `c3824a7`, Android CI run 46 passes the dedicated
  six-case 16 KB job. Require the same gate again for the eventual release commit;
  an ordinary API 36 job or ZIP check is insufficient.
  Retain physical Android 10/12 and 32-bit acceptance checks; the four current
  Android ABIs remain packaged (the replacement no longer ships obsolete MIPS).
  See [the native-media audit and limits](DEPENDENCIES.md#eighth-cleanup-pdfgif-native-replacement-and-resource-lifecycle).
- Verify the dependency snapshots with `verifySbom testSbomValidation` and generate
  a release-version SBOM as described in [the SBOM guide](SBOM.md).
- Confirm the version code and version name in the built artifact.
- Verify the APK signature and record a SHA-256 checksum.
- Smoke-test startup, tabs, gallery playback, cache clearing, and a SAF download.
- Test an in-place update to confirm that preferences and the Room database remain intact.
- Archive the APK/AAB, `mapping.txt`, checksum, matching SBOM, changelog, and signing-key backup securely.
