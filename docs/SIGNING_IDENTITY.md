# Fork signing identity and first candidate

Initial signing prepared on 26–27 October 2026. **8.0 was published on 27 October
2026 (Europe/Madrid)** with the owner's authorization. Publication is not a claim
that every manual acceptance case passed. This record contains public metadata
only, never a private key or password.

## Published 8.0 artifact

- [Release v8.0](https://github.com/jagumiel/mimi-reader/releases/tag/v8.0), built
  from `4be402b076d7f39060fc4243c23bf14a26f307c0` (also embedded in the APK).
- [Signed APK](https://github.com/jagumiel/mimi-reader/releases/download/v8.0/mimi-reader-8.0.apk),
  41,540,484 bytes; SHA-256:
  `ae426ddcf07efba33f5c018a6eb5abff0c8b14fb0e4dd0da2574c05f229abfd5`.
- Distribution certificate remains the approved RSA-4096 identity below. The
  public key certificate, matching 150-component SBOM, checksum manifest and
  frozen English changelog are attached; no private key/password is uploaded.
- [Android CI 51](https://github.com/jagumiel/mimi-reader/actions/runs/40417876635)
  passes all six jobs, including API 29/31/34/36 and actual 16 KB native tests.
  [SBOM security 8](https://github.com/jagumiel/mimi-reader/actions/runs/40417876616)
  passes both jobs and SARIF, with 148/148 Maven packages recognized and no known
  findings. Coverage limitations remain, not a security certification.
- This final candidate is rebuilt after committing the source, then signed and
  installed over the first fork candidate on Android 12 with the compatible key.
  Cold start and retained sorting pass; installed APK bytes match the final hash.
  This is same-version candidate replacement, not a future-version migration.
- All 12 persistent legacy-beta files and 607 saved media files remain unchanged;
  original installations/timestamps are preserved and stay-awake restored to 0.

Local archive: `build/release-candidate/8.0-4be402b/`, including exact mapping,
seeds and verification reports. Only its `public/` assets were uploaded. The
earlier candidate `79d93d…` below is historical and is **not** the published APK.

## Installation and update policy

The owner approved a separate independent-fork identity:

- Release: `io.github.jagumiel.mimireader`.
- Debug: `io.github.jagumiel.mimireader.beta`.
- Code namespace remains `com.emogoth.android.phone.mimi`.
- Version: **8.0 / 231**; minimum API 23, target API 36, four packaged ABIs.

The first fork release coexists with original Mimi, the old beta and previous
internal packages. It does not import their databases, settings, sessions or
storage grants. Keep the existing apps and saved media. Future fork updates
require the compatible fork install identity/certificate and version code.
The public legacy test key must never sign a distribution APK.

## Key custody and public verification identity

The owner generated an encrypted PKCS12 key at **`E:\mimi-reader-release.p12`**,
outside Git, using a native interactive password prompt. The password was not
sent through chat, arguments, environment variables, a transcript or CI. Only the
new private file has restricted NTFS access (owner and SYSTEM); drive-root
permissions were not changed.

- Alias: `mimi-reader-release`.
- Public subject/issuer: `CN=jagumiel, O=Mimi Reader` (Git identity, not a legal-name claim).
- Key: RSA 4096; certificate signature SHA256withRSA; validity through 2056.
- Exported public certificate: `E:\mimi-reader-release.certificate.pem`.
- **Public certificate SHA-256**:

```text
0094200e09a51bfd201a3926d9cd499c90d4d0e49204cfdaf80b7ef54d4bd4ce
```

Do not confuse the certificate fingerprint with an APK checksum or the public-key
fingerprint. Anyone can use the public certificate to verify identity; it cannot
sign an APK or recover a lost private key.

**Owner action still required:** keep the password in a password manager and
confirm two protected key backups in separate locations, including one offline.
The local `E:` drive alone is not a backup. Do not upload the `.p12` or password
to the repository, GitHub Actions or a release. Never regenerate/overwrite the
key to fix an installation problem. See the [interactive signing guide](RELEASING.md#signed-artifacts).

## Historical frozen first candidate

Local ignored archive: `build/release-candidate/8.0-signing/`.

`mimi-reader-8.0.apk` is the minified, resource-shrunk release signed with the
certificate above. Its SHA-256 is:

```text
79d93d919faa9870e9f3e5d1dfb2276b1ec55a8880608fba56d636f788e731b9
```

The adjacent `.apk.sha256`, unsigned APK/AAB, exact R8 `mapping.txt`, matching
CycloneDX SBOM and debug/test APKs are retained locally. The AAB is unsigned and
is not an installable release asset. Source is baseline `2f551ab` plus this
fork-identity/signing preparation; **not** an APK built from baseline alone. Any
future source change requires a newly built and verified candidate; do not
modify the frozen signed APK or reuse this checksum for another build.

## Verification completed

- Full APK signature verification passes: one approved RSA-4096 signer,
  v1/v2/v3 signatures; explicit API 29 and API 31 verification also passes.
- Signed and unsigned APKs pass 16 KB ZIP alignment and static ELF/JNI checks:
  24 native libraries across four ABIs. This is not a physical 16 KB-device test.
- Host verification passes: variant guards, 128 unit tests, lint (zero errors,
  existing warnings retained), debug/test builds, minified release APK/AAB,
  SBOM verification and negative validation tests.
- All 111 offline CI-helper tests pass locally. Three PowerShell preflight
  guards run on Windows and are explicitly skipped on other systems.
- The new fork debug package completes 93 methods on Edge 20 / Android 12:
  **91 passed, two optional skips, zero failures**, about 97 seconds. Local
  keyboard, rotation and original-WebView compatibility checks are enabled;
  no real CAPTCHA is solved and no public post is sent.
- Remote baseline [Android CI 50](https://github.com/jagumiel/mimi-reader/actions/runs/40407874021)
  passes all six jobs, including API 29/31/34/36 and actual 16 KB native tests.
  [SBOM security 7](https://github.com/jagumiel/mimi-reader/actions/runs/40407873987)
  passes both jobs, including SARIF; 148/148 Maven packages are recognized with
  no known findings. Generic/local and Ruffle/native/WASM coverage limits remain.
  These runs cover `2f551ab`, **not the subsequent fork-ID changes**; new CI is
  required for the final source revision.

Local reports are under `build/release-identity/2026-10-26/`. Debug tests are not
acceptance of every interaction in the exact minified signed APK.

## Signed APK smoke check on Android 12

On 27 October, the exact candidate above installs fresh on the Edge 20 / API 31
under the new release ID, with no replacement flag. Its installed `base.apk`
SHA-256 equals the archived signed candidate. A cold launch reaches
`TabsActivity` successfully (`Status: ok`, approximately 840 ms in this single
run); the board list and local release notes are visible, and no crash is present
in the new process's crash buffer. This is a smoke check, not a benchmark or full
gallery/SAF/CAPTCHA acceptance.

Before/after comparisons covering the debug suite and signed installation retain
all **12 legacy-beta persistent files** and **607 `Pictures/Mimi` files** with
identical paths and SHA-256 values. Their contents and individual hashes are not
exported into the repository. Existing legacy release/beta version and install
timestamps remain unchanged. No old app is uninstalled, cleared or overwritten;
no public media folder is renamed or deleted. Temporary stay-awake is restored
to its original **0**. The owner unlocks the secure device manually.

## Remaining validation and custody tasks

- Confirm private-key/password backups with the owner.
- Complete physical/manual acceptance of the exact signed APK on Android 10/12,
  keeping the [release checklist](RELEASE_ACCEPTANCE.md) explicit. The old
  Android 10 WebView 74 Ruffle failure is not erased by Android 12 success.
- Validate compatible fork-to-fork updates without claiming migration from
  original Mimi or another certificate.
- Require fresh Android and security workflows for future release commits; the
  published 8.0 source is accepted by runs 51 and 8 above.
- Retain the disclosed manual limits in future release notes. The owner authorized
  publication despite incomplete manual coverage and will arrange protected key
  backups; those backups have not been confirmed and are not silently marked done.

No upload, release, tag or push is performed by the local signing helpers.
