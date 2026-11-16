# Mimi Reader 8.0.1 release preparation

The owner authorized building, signing with the existing private identity and
publishing **8.0.1 / versionCode 232** on GitHub on 16 November 2026. This document
records preparation, not proof that publication or physical acceptance occurred.
The published release and its attached checksums identify the final artifact.

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
The subsequent release-metadata commit must pass its own Android and security
workflows before publication. Source acceptance is not acceptance of a signed
artifact or every manual flow.

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
