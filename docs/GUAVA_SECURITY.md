# Guava security update: CVE-2026-102554

## Detection and scope

Weekly `SBOM security` runs [11](https://github.com/jagumiel/mimi-reader/actions/runs/41489223195),
[12](https://github.com/jagumiel/mimi-reader/actions/runs/42667005908) and
[13](https://github.com/jagumiel/mimi-reader/actions/runs/43915512255) found
**CVE-2026-102554 / GHSA-xxph-c9ww-hj94** in
`com.google.guava:guava:33.3.1-android`. Run 14 reproduced the finding on the
documentation-only `765e50f` commit. The first weekly failure was 2 November
2026; the previous 27 October local-date scan returned no known findings.
The scanner, complete package-coverage guard and SARIF upload succeeded; the
HIGH-finding policy correctly failed with exit 1. This is not an Android build
failure or an observed compromise.

The [reviewed advisory](https://github.com/advisories/GHSA-xxph-c9ww-hj94)
describes excessive memory allocation during Java object deserialization.
The [upstream patch](https://github.com/google/guava/commit/b931fe9) bounds the
initial allocation in compact collections and MapMaker. A vulnerable package
match does not demonstrate an attacker-controlled route to those operations in
Mimi. A textual search found only the declaration/self-references of the legacy
`SerializableHttpCookie` helper; this is not a complete reachability audit.
The archived 8.0 R8 mapping retains CompactHashMap/readObject, so shrinking is
not grounds to dismiss the finding either.

## Bounded remediation and trade-offs

Media3 `1.10.1` brings Guava through `media3-common`. Declare
`implementation 'com.google.guava:guava:33.7.2-android'` in the application to own
the corrected Android baseline. The [33.7.2 release](https://github.com/google/guava/releases/tag/v33.7.2)
publishes this Android artifact with the fix. `dependencyInsight` verifies that
Media3's `33.3.1-android` request resolves to the selected `33.7.2-android`.

- Advantage: fixes the identified library issue without upgrading Media3, AGP,
  Kotlin, Gradle, SDK/minSdk or changing application behavior and stored data.
- Advantage: an ordinary declaration is visible to the existing inventory and
  does not force/reject a future newer selection. No CVE suppression, broad
  exclusion, policy downgrade or exception is introduced.
- Cost: the application now owns an explicit baseline for a transitive library;
  review it again when updating Media3. This is a minimum selected baseline,
  not a strict lock to one version.
- Risk: 33.3.1 to 33.7.2 spans intervening releases, not just the final upstream
  patch. Binary/API behavior, annotation metadata and transitive inputs need
  validation. A constraint would avoid a direct declaration but would require
  extending the current direct-declaration inventory to describe it faithfully.
  A blanket force would unnecessarily prevent later compatible upgrades.
- Limit: this fixes the application runtime graph, not every tool on the build
  machine. `kaptRelease` still selects Guava `33.2.1-jre`, and the root buildscript
  `33.7.1-jre`; both remain recorded in the separate inventory, outside this
  release SBOM scan. Review those build-tool graphs separately rather than
  applying an untested cross-project force or claiming they are cleared.

## Generated inventory delta

Run `updateSbom`; do not edit version/hash fields by hand. The result is
**151 components / 149 Maven packages / 72 direct declarations**, previously
150 / 148 / 71. Runtime component changes are limited to:

| Component | Before | After |
| --- | --- | --- |
| Guava | 33.3.1-android | 33.7.2-android |
| failureaccess | 1.0.2 | 1.0.3 |
| error_prone_annotations | 2.48.0 | 2.50.0 |
| jspecify | 1.0.0 | 1.0.1 |
| j2objc-annotations | absent | 3.1 |

The extra annotation component accounts for the increased coverage denominator;
it is not reduced coverage. Review selected hashes and edges as well as versions.
Application unit tests select the new Android Guava; tool-only JRE selections
are unchanged. Native/Ruffle inputs and the empty exception file are unchanged.

## Verification and acceptance

Four host-JVM `GuavaDeserializationContractTest` cases exercise the real selected
library. Compact map/set test-only subclasses observe initial capacity;
MapMaker verifies bounded segment allocation. Synthetic streams declare only
4096 entries, and baseline runs use a 64 MB JVM heap: a regression fails an
assertion instead of deliberately exhausting memory. A legitimate 1024-element
map/set round-trip checks growth beyond the initial cap. Tests are not shipped
in the APK and are not an exploit demonstration against Mimi or a remote site.

Unchanged tests run against cached `33.3.1-android`: three security assertions
fail and the valid-data case passes. With `33.7.2-android`, all four pass.
Local app/library unit suites pass **132 tests**; offline CI helpers pass **111**.
Debug APK, minified release APK/AAB and lint complete successfully; existing
deprecation/configuration warnings are not suppressed by this change.
SBOM freshness/schema/negative validation passes, including six malformed inputs.
The real local Trivy 0.76.1 scan uses an updated database and reports **149/149**
Maven packages, **zero known findings and zero blockers**, without exceptions.
Its ignored evidence is under `build/reports/guava-vulnerabilities/`.

The source commit still requires its own remote Android CI (debug/release,
lint, unit tests, API 29/31/34/36 instrumentation and native 16 KB job) and
`SBOM security`, including SARIF publication. Local results do not stand in for
that remote acceptance or physical-device acceptance. Preserve the documented
native/WASM and build-tool coverage limitations.

This is an unreleased source correction. Default version metadata stays 8.0/231
until the next release is explicitly prepared. The published `v8.0` tag, APK,
checksums and SBOM asset remain historical and unchanged; updating this branch
does not fix installed 8.0 APKs. Prepare and validate a separately signed update
before describing the fix as distributed. The Gradle Wrapper update remains
scheduled for 8.0.2.
