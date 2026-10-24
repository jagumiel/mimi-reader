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
and reachable dependency edges, exact application-to-direct-dependency edges,
versions/Package URLs, required manual components,
vendored file hashes, and equality with the committed SBOM and inventory.
New JavaScript, WebAssembly, local JAR/AAR or `jniLibs` binaries must be inventoried
explicitly; silently adding a bundled executable asset fails verification.

Both the raw CycloneDX task and canonical generation refresh on each invocation.
An unchanged artifact set can still have changed direct/transitive edges; a cached
raw report must not hide declaration-only cleanup. The sixth cleanup keeps all
158 component records unchanged while replacing three application root edges
with five explicit consumer edges; the inventory now has 77 declarations.

The seventh cleanup updates only GridLayout from 1.0.0 to 1.1.0 and its outgoing
edges. Counts remain 158 components / 77 declarations. PDF/GIF native artifacts
and hashes are unchanged: the [native page-size audit](DEPENDENCIES.md#native-audit-a-separate-pre-release-requirement)
does not imply a binary update or a CVE scan.

On 21 October the eighth cleanup replaces the two PDF artifacts with the oothp
Maven Central fork/Pdfium and updates GIF to 1.2.32 and its ReLinker dependency.
Those snapshots contained **151 components / 77 declarations**: seven legacy
components become unreachable without global exclusions. Ruffle and unrelated
retained components are unchanged. Native binaries are covered by their AAR
artifact hashes as before; this does not establish the embedded PDFium/GIFLib
source revision or absence of CVEs. Separate ELF/ZIP reports and actual 16 KB
runtime tests are documented in [the dependency audit](DEPENDENCIES.md#eighth-cleanup-pdfgif-native-replacement-and-resource-lifecycle).

On 22 October the ninth cleanup removes Novoda's Custom Tabs adapter, using the
already-resolved AndroidX Browser API directly. That block's snapshots contain
**150 components / 76 declarations**. Only Novoda disappears; every retained
component record and all Ruffle file hashes remain unchanged. Its node/root
edge is removed without version changes, new dependencies or global exclusions.
See [browser cleanup evidence](DEPENDENCIES.md#ninth-cleanup-replace-the-novoda-browser-adapter).

The tenth cleanup replaces `com.quiph.ui:recyclerviewfastscroller:0.2.3` with
`me.zhanghai.android.fastscroll:library:1.3.0`. Totals remain **150 components /
76 declarations**. No retained component version/metadata/hash or retained graph
edge outside the two scroller nodes changes; Ruffle is untouched. The new
artifact requests already-selected AndroidX dependencies and adds no native
code. [Fast-scroll integration and tests](DEPENDENCIES.md#tenth-cleanup-thread-fast-scrolling-with-variable-height-posts)
cover its variable-height adaptation and lifecycle/refresh behaviour. This is
not a CVE scan or a claim that all remaining UI libraries have been updated.

The eleventh cleanup updates Material to 1.14.0 and its required ConstraintLayout
baseline to 2.2.1. Current totals are **153 components / 76 declarations**:
six retained version changes (including a Kotlin BOM constraint), four additions
and the old ConstraintLayout solver's removal. All other retained records and
Ruffle hashes remain unchanged. See the [complete reviewed diff and UI scope](DEPENDENCIES.md#eleventh-cleanup-material-components-and-its-required-gallery-baseline).
This is still inventory validation, not a CVE scan or proof of native source provenance.

The twelfth cleanup updates five runtime artifact records from Glide 4.11.0
to 5.0.7: core, OkHttp integration, annotations, gifdecoder and disklrucache.
Totals remain **153 components / 76 declarations**; all other retained records,
Ruffle hashes and PDF/native GIF artifacts are unchanged. The separate build
inventory also updates Glide's compiler while retaining its build-only tools.
See [compatibility selection and image/cache tests](DEPENDENCIES.md#twelfth-cleanup-glide-image-loading-fallback-and-caching).
Do not confuse Glide's Java GIF decoder with the application's separate native
GIF player, or this inventory refresh with a CVE scan.

The thirteenth cleanup removes the sole PercentLayout consumer and its direct
declaration. Current totals are **152 components / 75 declarations**. Only the
PercentLayout component/node/root edge is removed; every retained component
record and every retained edge outside that node is unchanged, including Ruffle
and native media. See [the filter-row replacement and validation](DEPENDENCIES.md#thirteenth-cleanup-remove-percentlayout-from-filter-rows).
No replacement dependency, version drift or global exclusion is introduced.

The nineteenth cleanup replaces only `chanlib`'s unused Core KTX/RxAndroid
exports with its actual Annotation/OkHttp API dependencies. Totals stay **152
components / 75 declarations**. Every release component record/hash and every
edge outside `chanlib`'s outgoing set is unchanged. The separate library unit
graph changes from 23 to 20 entries and now selects OkHttp 4.12.0 instead of
3.14.9; application/test/build graphs outside that library configuration are
unchanged. See [the standalone library boundary and tests](DEPENDENCIES.md#nineteenth-cleanup-standalone-chanlib-dependency-boundary).
This remains inventory validation, not a vulnerability scan.

The twenty-second cleanup removes unused Jsoup only from `chanlib`, moves its
internal Retrofit factories to implementation scope, uses implementation scope
for the application's local module and aligns its Annotation request to the
already-selected 1.10.0. Totals are now **152 components / 74 declarations**.
Every release component record/hash and every edge except `chanlib`'s Jsoup edge
is identical. The standalone library unit graph loses Jsoup (20 -> 19 entries);
all other inventoried selected configurations remain unchanged. The scope and
version-request corrections are still recorded in direct declarations. See
[D02 ownership and tests](DEPENDENCIES.md#twenty-second-cleanup-explicit-dependency-ownership-d02).
Do not describe this as removing Jsoup from the app or completing CVE scanning.

The twenty-third cleanup removes three redundant/unused direct KTX declarations.
Totals become **150 components / 71 declarations**: only Preference KTX and
Work KTX disappear from the runtime component set. Room KTX 2.8.5 has no runtime
classes but stays reachable through WorkManager; its direct app edge is removed,
not its transitive node. Every retained record/version/hash and every other
retained edge is identical. Only the release/app-unit selected inventories change
(two removed nodes and Room KTX's direct flag). See the
[D03 artifact/consumer review](DEPENDENCIES.md#twenty-third-cleanup-optional-ktx-artifacts-and-actual-api-owners-d03).
Runtime/Rx/coroutine support and the separate build/test inventory remain.

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
edges, missing Ruffle components, incorrect extra/missing application root edges
(six malformed-document cases in total), and wrong asset hashes. It also verifies that
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
