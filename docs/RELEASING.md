# Release guide

Mimi Reader can produce a minified unsigned release without access to signing secrets. A local `keystore.properties` file enables signing for both APK and App Bundle outputs. The keystore and its properties are ignored by Git and must never be committed.

## Single free application

There are no separate free/paid products. Both modules use only standard
`debug` and `release` variants; the old `free`, `paid` and library-only `amazon`
flavors have been removed. On 26 October the owner approves a separate fork
identity: release uses `io.github.jagumiel.mimireader`, debug uses
`io.github.jagumiel.mimireader.beta`. Java/Kotlin classes and the AGP namespace
remain `com.emogoth.android.phone.mimi`; do not mass-rename them as install IDs.
`./gradlew :mimi-app:verifyApplicationVariants` checks the two new application IDs
against the actual AGP variants and also runs through `check` and Android CI.

The first public fork installs alongside original Mimi and the previous internal
release/beta packages. It does not update or import their private databases,
preferences, sessions or grants. Keep those installations and public media.
Subsequent fork updates require the compatible fork identity/certificate and
version code; never uninstall/clear a legacy app to resolve a signature conflict.

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

### Recommended: local interactive signing, no stored password

The owner selects **`E:\mimi-reader-release.p12`** as the private key location.
This is outside the repository. Use a unique strong password kept in the owner's
password manager and two protected key backups in different locations, including
one offline. A local drive alone is not a backup. Losing the key/password can
prevent compatible future updates; a public certificate cannot recover the key.

`scripts/release/New-MimiSigningKey.ps1` creates an encrypted PKCS12 store with
an RSA-4096 key, SHA256withRSA certificate, alias `mimi-reader-release`, validity
10950 days and public subject `CN=jagumiel, O=Mimi Reader`. It invokes JDK keytool
in the owner's **visible console**, without password arguments/environment/logs.
The owner enters the password twice for creation and once to export the public
`E:\mimi-reader-release.certificate.pem`. No password should be sent through chat.

The helper refuses existing output files and destinations inside the repository.
It generates into unique adjacent files, restricts only the new NTFS private file
to its owner/SYSTEM, then moves without overwrite. On an export failure, retain
the already generated key and resume export; **do not generate a replacement**.
Never change drive-root permissions or delete private material as an error fix.

```powershell
& .\scripts\release\New-MimiSigningKey.ps1 `
  -KeyStorePath 'E:\mimi-reader-release.p12' `
  -KeytoolPath (Join-Path $env:JAVA_HOME 'bin\keytool.exe')
```

Build the unsigned minified APK normally, with no `keystore.properties` file.
Inspect the exported **public** certificate with `keytool -printcert -file`,
record its SHA-256 fingerprint, then run `scripts/release/Sign-MimiApk.ps1` in the
owner's visible console. Supply absolute input/output/key/SDK paths, the approved
`-ExpectedCertificateSha256` and `-ExpectedVersionCode 231`. The SDK prompts for
the password directly; no plaintext-password properties file is needed.

The signer rejects the wrong install ID/version, debug APKs and existing outputs;
checks 16 KB ZIP alignment before signing; verifies the signature, approved
certificate and explicit API 29/31 compatibility; then rechecks alignment and
finalizes a separate APK plus its SHA-256 sidecar. It never overwrites the unsigned
source, installs, uploads, tags or publishes an APK. Retain its exact mapping,
SBOM and source revision. Do not modify the signed APK afterward.

Only the signed APK, public certificate/fingerprint, checksum, SBOM and notes are
distribution material. **The .p12 and its password are private.** Keep the key
outside Git/GitHub/CI; `.gitignore` protection is additional, not a custody policy.
See [Android signing guidance](https://developer.android.com/studio/publish/app-signing),
[apksigner](https://developer.android.com/tools/apksigner) and
[JDK keytool](https://docs.oracle.com/en/java/javase/17/docs/specs/man/keytool.html).

### Optional existing Gradle signing configuration

1. Keep the release keystore outside the repository, or use the ignored `release.keystore` path at the repository root.
2. Copy `keystore.properties.example` to `keystore.properties` and replace all placeholders. Relative `storeFile` paths are resolved from the repository root; absolute paths are also accepted.
3. Run the release command from the repository root.
4. Preserve the generated R8 `mapping.txt` with the matching release. It is required to decode obfuscated stack traces.

The build fails early if the properties file exists but any required signing value is empty. Verify a signed APK with the Android SDK tool before distribution:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs mimi-app\build\outputs\apk\release\mimi-app-release.apk
```

The first fork APK is a fresh, separate installation. For later fork updates,
test an in-place update over a fork build with the compatible certificate; do
not try it over original Mimi or an internal package with another signing key.

## Next release gate

The archived internal 7.0.1 / 230 test package and per-device checklist are in
[RELEASE_ACCEPTANCE.md](RELEASE_ACCEPTANCE.md). It passed fresh installation and
cold start on Android 12, preserving the existing `Pictures/Mimi` folder, but
does not validate the new 8.0 / 231 artifacts or a signed in-place update. The
subsequent isolated debug tests on Android 10 and Android 12 are recorded in
[ANDROID10_VALIDATION.md](ANDROID10_VALIDATION.md) and
[ANDROID12_VALIDATION.md](ANDROID12_VALIDATION.md). They precede the new fork-ID
change and do not accept its exact signed minified APK. Keep visual/manual release
and compatible-fork-update checks explicit.

The owner subsequently approved a new fork identity and generated its private
distribution key outside the repository. The first signed 8.0 candidate, public
certificate fingerprint and remaining acceptance gates are recorded in
[SIGNING_IDENTITY.md](SIGNING_IDENTITY.md). The internal public test key remains
unsuitable for distribution. Unsigned output must retain its exact mapping/SBOM;
never uninstall or clear a legacy app to force a certificate-incompatible update.
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
- Before publication, confirm protected backups of the approved distribution key
  (not the public internal test key) and provide the signed installable APK, checksum, certificate fingerprint
  and update/signature caveats with the release. Never label an unsigned CI APK
  as an installable download. Keep the [README installation guide](../README.md#download-and-install)
  aligned with the actual asset; replace its preparation notice only when the
  release is really published. Unknown-source permission does not bypass signing.
- Smoke-test startup, tabs, gallery playback, cache clearing, and a SAF download.
- Test a compatible fork-to-fork in-place update to confirm that preferences and
  the Room database remain intact. The first fork installation is separate from
  original Mimi; this check does not promise importing its private data.
- Archive the APK/AAB, `mapping.txt`, checksum, matching SBOM, changelog, and signing-key backup securely.
