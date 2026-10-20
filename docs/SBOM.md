# Software bill of materials

The repository tracks a CycloneDX 1.6 JSON SBOM for **release** and a separate
dependency inventory. They describe the selected Gradle dependency graph before
R8 shrinking, not a claim that every class in every artifact survives in the APK.
There is a single free application with standard debug/release build types;
Android's platform-provided libraries are outside this SBOM.

## Files and coverage

- `sbom/mimi-reader-release.cdx.json`: release runtime dependencies, the local
  `chanlib` module, the desugaring runtime, and bundled Ruffle files. It includes
  Package URLs, dependency edges, reported licenses, and SHA-256 artifact hashes
  where an artifact exists. BOM/platform nodes can have no binary hash.
- `sbom/dependency-inventory.json`: direct declarations in both modules and
  selected direct/transitive versions for release, unit tests, instrumentation
  test additions, annotation processors, desugaring, and the root buildscript.
  Test/build dependencies are kept separate from the shipped application's SBOM.
- `sbom/manual-components.json`: reviewed Ruffle version, GitHub Package URL,
  upstream release/archive checksum, license expression, and expected hashes of
  the seven files bundled from upstream. The application-owned `player.js` shell
  is explicitly identified as first-party code.
- `gradle/sbom.gradle`: generation, normalization, verification and negative tests.

The Gradle generator is CycloneDX Gradle Plugin **3.5.1**; schema validation uses
CycloneDX Core Java **13.2.0**. Both run only as build tooling. Plugin behavior is
documented in its [versioned upstream README](https://github.com/CycloneDX/cyclonedx-gradle-plugin/blob/cyclonedx-gradle-plugin-3.5.1/README.md).

Native libraries embedded in third-party AARs are attributed to their containing
Maven component. This does not reconstruct PDFium/libpng's C/C++ dependency tree,
or the Rust/npm build dependencies of the upstream Ruffle WebAssembly release.
Further upstream SBOMs or binary analysis would be needed for that additional coverage.

## Generate and verify

On 20 October 2026, the legacy product flavors were removed. The previous
`mimi-reader-free-release.cdx.json` snapshot is now named
`mimi-reader-release.cdx.json`; the application Package URL qualifier and
inventory configuration names now use `release`/`debug` without the old flavor.
This is a build-variant/metadata change, not a dependency version update.

The subsequent AndroidX UI migration deliberately changes the resolved runtime
graph from 154 to 158 components and the direct inventory from 74 to 75
declarations (explicit Fragment baseline). Fifteen retained components change
version, five are added and one is removed. Ruffle's record and its seven hashes
are unchanged. See the [reviewed graph changes](DEPENDENCIES.md#fifth-cleanup-a-deliberate-androidx-ui-baseline)
before comparing these snapshots with the pre-migration baseline.

Use JDK 17 and the Android SDK required by the project. On Windows replace
`./gradlew` with `.\gradlew.bat`.

```sh
./gradlew generateSbom
./gradlew verifySbom testSbomValidation
```

Generation writes to `build/reports/sbom/` and does not modify tracked snapshots.
The verifier checks the CycloneDX schema, unique component references, complete
and reachable dependency edges, versions/Package URLs, required manual components,
vendored file hashes, and equality with the committed SBOM and inventory.
New JavaScript, WebAssembly, local JAR/AAR or `jniLibs` binaries must be inventoried
explicitly; silently adding a bundled executable asset fails verification.

After a deliberate dependency or vendored runtime update:

```sh
./gradlew updateSbom
./gradlew verifySbom testSbomValidation
git diff -- sbom/
```

Review the diff and commit the source changes, manual manifest/provenance when
applicable, and both generated snapshots together. Normal application builds do
not rewrite them. Dependency selection drift also requires review: snapshots
detect drift but are not Gradle dependency lockfiles.

Normalization removes timestamps and random serial numbers, uses stable local
project identifiers, sorts map keys and set-like arrays, and retains only SHA-256
of the generator's artifact hashes. Git attributes preserve vendored bytes and
LF snapshot formatting across Windows/Linux checkouts. Reproducibility is scoped
to the same version, dependency resolution and artifact bytes, not the entire APK.

`testSbomValidation` rejects malformed schemas, duplicate references, dangling
edges, missing Ruffle components, and wrong asset hashes. It also verifies that
timestamp/serial-number/ordering changes do not change canonical output.

## CI and release artifacts

Android CI runs `verifySbom testSbomValidation` before building the application.
A stale snapshot or failed validation blocks that job. Generated JSON and the
manual manifest are uploaded for 14 days, including on failure for diagnosis.

The tracked SBOM uses the build's default version, currently 7.0.1. For an actual
release built with version overrides, generate an artifact SBOM using those same
overrides and archive the JSON alongside its APK/AAB and R8 mapping:

```sh
./gradlew generateSbom -PmimiVersionName=VERSION -PmimiVersionCode=CODE
```

Known-vulnerability scanning, SARIF reporting and expiring exceptions remain
deferred until posting recovery and the agreed dependency cleanup. Schema/freshness
checks do **not** constitute a CVE scan. The posting CAPTCHA loads external provider
scripts at runtime; these are not bundled assets or Gradle components, so this
integration does not change the packaged-component snapshot. See
[the posting boundaries](POSTING.md),
[the dependency audit](DEPENDENCIES.md) and [the roadmap](../roadmap-2026.md).
