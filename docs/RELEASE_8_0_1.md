# Mimi Reader 8.0.1 publication record

The owner authorized building, signing with the existing private identity and
publishing **8.0.1 / versionCode 232** on GitHub on 16 November 2026. Publication
completed at **22:46:44 UTC / 23:46:44 Europe/Madrid**. This is the stable/latest
[v8.0.1 release](https://github.com/jagumiel/mimi-reader/releases/tag/v8.0.1),
ID `447709815`, tagged at `c246ed245a12a0038e6050cfa3c482c35a6c06b7`.

## Published artifact

- [Signed APK](https://github.com/jagumiel/mimi-reader/releases/download/v8.0.1/mimi-reader-8.0.1.apk):
  **41,540,484 bytes**, SHA-256
  `525ed8f429752bff4c5e2c7ea4d38dc7d0d96d119ef61682a1c44b0481a133f0`.
- Same approved RSA-4096 certificate as 8.0; APK signature verifies with v1/v2/v3,
  including explicit API 29/31 compatibility. Source revision embedded in the APK
  matches the tag. It is a non-debuggable release, minimum API 23 / target API 36.
- [Android CI 56](https://github.com/jagumiel/mimi-reader/actions/runs/43970718424)
  passes all six jobs and [SBOM security 16](https://github.com/jagumiel/mimi-reader/actions/runs/43970718227)
  both jobs, including SARIF, for the exact release source commit. Scan coverage
  is 149/149 Maven packages, zero known findings and no exceptions.
- All six uploaded assets have matching remote SHA-256 digests. The public APK
  was downloaded after publication and its hash/signature reverified. The
  published 8.0 APK/SBOM digests remain unchanged.
- Exact unsigned/signed artifacts, mapping/seeds and evidence are archived under
  ignored `build/release-candidate/8.0.1-c246ed2/`. An identical signed APK and
  checksum are also available locally as `E:\mimi-reader-8.0.1.apk` / `.apk.sha256`.
  No private material is distributed, and no phone installation/data is touched.

The preparation requirements below were enforced for publication. Physical/manual
acceptance limits and key-backup responsibilities remain explicitly open.

## Scope and immutable inputs

- Release installation ID: `io.github.jagumiel.mimireader`; code namespace and
  storage behavior remain unchanged. Minimum API 23, target API 36, four ABIs.
- Security fix: `43a34e3`, selecting Guava **33.7.2-android** and its reviewed
  transitive inputs. No additional application feature or toolchain upgrade.
- Release metadata, root Markdown and bundled English changelog use **8.0.1 / 232**.
  Regenerate the matching CycloneDX SBOM; runtime inputs remain 151 components /
  149 Maven packages / 72 direct declarations.
- Reuse the existing signing certificate, SHA-256
  `0094200e09a51bfd201a3926d9cd499c90d4d0e49204cfdaf80b7ef54d4bd4ce`.
  Password entry stays in the owner's visible native console, never in chat,
  arguments, environment, CI, repository files or release assets.
- Do not overwrite the historical 8.0 tag, APK, mapping, checksums or SBOM.
  Keep an ignored per-commit archive of the new unsigned/signed APK, AAB, exact
  R8 mapping/seeds, SBOM and verification evidence before uploading public assets.

## Acceptance gates

The security source fix passes [Android CI 55](https://github.com/jagumiel/mimi-reader/actions/runs/43966563766)
(six jobs) and [SBOM security 15](https://github.com/jagumiel/mimi-reader/actions/runs/43966563925)
(two jobs, 149/149 Maven packages, zero known findings, no exceptions).
The subsequent release-metadata commit `c246ed2` also passes its own workflows
56 and 16 above. Source acceptance is not acceptance of every manual flow in
the signed artifact.

For the exact signed APK, verify install identity/version, non-debuggable state,
signature and approved certificate, explicit API 29/31 signature compatibility,
16 KB ZIP/native alignment, SHA-256 and embedded source revision. Match the
public SBOM to the versioned snapshot and ensure uploads have matching hashes.
After publication, download the public APK and verify its hash and signature.

## Update policy and disclosed limits

The higher version code and unchanged package/certificate permit a compatible
fork-to-fork update from 8.0. Do not uninstall, clear storage or delete public
`Mimi` / `Pictures/Mimi` downloads to perform it. This is not migration from
original Mimi or differently signed earlier internal APKs. No user-data import,
schema change or download deletion is part of this release.

No new physical-device acceptance is claimed by this packaging step. Preserve
the existing Android 10/12 manual/gallery/SAF/CAPTCHA coverage limits and old
WebView 74 Ruffle incompatibility. SWF support remains partial. Native/WASM and
build-tool vulnerability coverage gaps remain; see [Guava scope](GUAVA_SECURITY.md).
The signature is an integrity/identity check, not a security certification.
Private-key backup confirmation remains an owner responsibility. Gradle remains
8.13; its separately scoped update stays deferred to 8.0.2.

## Public release assets

Upload only the fixed allowlist below, then verify remote digests before making
the draft public:

- `mimi-reader-8.0.1.apk`
- `mimi-reader-8.0.1.apk.sha256`
- `mimi-reader-8.0.1.cdx.json`
- `mimi-reader-release.certificate.pem` (public certificate only)
- `CHANGELOG.md`
- `SHA256SUMS.txt`

Never upload the `.p12`, password, an unsigned APK/AAB or private device data.
