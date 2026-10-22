# Dependency inventory and cleanup plan

Baseline: 18 October 2026, following the SWF playback correction. Refreshed on
20 October after the bounded cleanups, single-app consolidation and AndroidX UI
migration. The fourth block aligned declarations without changing shipped versions;
the fifth deliberately replaces the old AppCompat/Fragment alpha selections.
The sixth replaces direct legacy umbrellas with explicit consumer declarations,
without changing the selected component set or versions.
The seventh updates GridLayout alone and records the native-library release risk.

The eighth block replaces PDF/GIF native artifacts and adds strict alignment/runtime checks.
The ninth removes the Novoda browser adapter in favour of the existing AndroidX Browser API.

The canonical release SBOM has **150 top-level components**: 148 Maven components,
the local `chanlib` module and Ruffle. Ruffle contains seven individually hashed
files. The inventory records **76 direct declarations** across the two modules;
repeated declarations and dependency constraints are not 76 distinct libraries.
See [SBOM generation and scope](SBOM.md) for commands and limitations.

## First cleanup: removed declarations

| Declaration | Evidence and scope |
| --- | --- |
| `implementation commons-codec:commons-codec:1.12` | No imports/usages in either module or generated application code. Gradle dependency insight and the previous release graph showed only the application's direct declaration as its incoming runtime path. Removed from the app runtime, not from build tooling. |
| `implementation androidx.room:room-guava:2.8.5` | DAOs/generated implementations use Room's Rx integration, not `GuavaRoom`, Guava `Optional`, or `ListenableFuture`. Retain `room-rxjava2`; removing this optional adapter also removes its now-unreachable `kotlinx-coroutines-guava:1.9.0` runtime dependency. |
| `kapt androidx.annotation:annotation:1.1.0` | The inspected annotation JAR has no annotation-processor service registration. Keep the normal `implementation` annotation declaration and the actual Room/Glide processors. Annotation artifacts still occur transitively in the processor graph. |

The release graph loses exactly three components, from 157 to 154, without
changing any retained release component's version/Package URL. Direct
declarations drop from 77 to 74. Guava remains required by Media3; do not exclude
it globally. Commons Codec also remains in the separate build/processor
inventory at versions resolved by those tools. That is not evidence that the
removed 1.12 runtime declaration is still shipped.

This change does not remove source/assets/resources, change database schemas or
raise the Android minimum version. Source/resource cleanup is a separate block.
The obsolete `-dontwarn org.apache.**` R8 rule, previously justified by Commons
Codec's optional APIs, is also removed so release builds cannot hide missing
Apache references behind that broad application-level suppression.

### Validation of this cleanup

- 80 application unit tests passed. The existing `chanlib` arithmetic smoke
  test also passed, but is not counted as functional regression coverage.
- 21 CI-helper/contract tests passed.
- Full Motorola Edge 20 / Android 12 suite: 32 cases, 30 passed, 2 expected
  opt-in skips, no failures/errors; includes Room migrations, WorkManager,
  startup/restoration and gallery playback. The temporary stay-awake setting
  was restored to its original value after the run.
- Debug APK/test APK, normal debug lint and minified release APK/AAB builds
  passed. The final release build passed with the broad Apache suppression
  removed, and its generated R8 configuration no longer contains that rule.
- SBOM schema/freshness/negative checks passed; generated source has no
  `GuavaRoom`, `ListenableFuture` or Commons Codec references.
- Local release artifacts decreased from 59,508,566 to 59,384,414 bytes (APK)
  and from 36,577,045 to 36,450,099 bytes (AAB). These are local observations,
  not a startup benchmark or an assertion of byte-identical builds.

The first combined build hit an internal Kotlin FIR exception while lint
analyzed an unchanged instrumentation test. A separate normal lint invocation
passed without code changes, disabled detectors or relaxed error handling. The
cause of that analyzer exception is not established; do not hide it with retries
if it recurs in CI. Run 15 was the initial remote baseline; the later
`eeb2e31` run confirmed by the user covers these changes cumulatively, as noted
under the third block. New cleanup changes still require their own matrix run.

## Findings from the resolved graph

| Area | Observed state | Follow-up and verification |
| --- | --- | --- |
| AppCompat / Fragment | Previously resolved **1.3.0-alpha02 / 1.3.0-alpha08**; the fifth block explicitly selects **1.8.0 / 1.9.1**. | The alpha path is traced below. Keep testing themes, dialogs, startup, system insets and R8 when changing the remaining legacy adapters. |
| RxJava | Both modules now declare the already-selected **2.2.21** through one shared version; RxAndroid 2.1.1 and RxKotlin 2.4.0 remain. | Declaration alignment is complete in the fourth block; broader reactive-code replacement remains a separate refactor. |
| PDF / GIF | The eighth block replaces the PDF fork with `io.github.oothp:android-pdf-viewer:3.2.0-beta06` / Pdfium 1.9.5-beta01, and updates GIF to 1.2.32. | Local rendering and ELF/ZIP checks pass; confirm the dedicated 16 KB runtime CI job before release. These are wrapper/artifact versions, not a complete audit of embedded PDFium/GIFLib security. |
| UI libraries | Preference is now **1.2.1**. Material 1.2.1, Glide 4.11.0, ConstraintLayout 2.0.1 and Browser 1.2.0 remain declared. | Modernize in small groups with UI, image/cache and navigation regression checks. Confirm Android 10/12 compatibility for each proposed replacement. |
| Kotlin | stdlib 2.3.21 coexists with jdk7/jdk8 compatibility artifacts 1.9.10 and kotlin-bom 1.8.0. | Inspect dependency paths and distinguish compatibility/constraint artifacts from duplicate executable code before removing anything. |
| Ruffle | Vendored 0.7.1 JS/WASM, MIT OR Apache-2.0. | Update its manifest, provenance and hashes together. Retain isolation, animation/pause tests and opt-in live SWF checks. |

These are inventory findings, not conclusions that a package has a known CVE.
No vulnerability database was queried in this phase.

## Candidates for incremental removal or consolidation

- The first three dependency candidates are resolved above. Continue reviewing
  indirect/generated usage rather than equating absent imports with safe removal.
- Direct legacy AndroidX umbrella declarations and the filter editor's legacy
  spacers are replaced in the sixth block. The seventh removes GridLayout's
  legacy path; the eighth removes PDFium's remaining umbrella path as well.
- JUnit alignment, the empty local file-tree/repositories and obsolete commented
  build declarations are addressed in the fourth block below.
- Keep the Serialization BOM: absence of application imports does not establish
  that a dependency constraint is unused. Lifecycle saved-state consumes its
  aligned core artifacts transitively; re-evaluate only with graph/migration tests.

Do not infer that a layout library is unused merely from Java/Kotlin imports:
`PercentRelativeLayout` remains in `filter_list_item.xml`, and AndroidX GridLayout
remains in `post_filter_view.xml`. Commons Lang `StringUtils` is also used in the
comment parser and post-option adapter.

## Second cleanup: confirmed orphaned source and layouts

Removed five files after checking both modules' source sets, XML resources,
manifest entries, R8 rules and generated view-binding consumers:

| Removed file | Evidence and preserved functionality |
| --- | --- |
| `interfaces/AudioSettingsHost.java` | No implementation or caller. Current video mute state lives in `GalleryAudioState` and is applied by `WebmPage`; its controls do not implement this interface. |
| `interfaces/MimiTab.java` | No implementation or caller. Current tabs use `TabInterface`, `TabEventListener` and `TabPagerAdapter`; those contracts remain. |
| `layout/activity_gallery.xml` | No inflation, include, binding consumer or alternate-qualified version. The registered `GalleryActivity2` inflates `ActivityGallery2Binding` from `activity_gallery2.xml`, which remains. |
| `layout/activity_video_ad.xml` | No activity, include or binding consumer. This was the abandoned advertisement screen, not a current playback control. |
| `layout/activity_youtube.xml` | No activity, include or binding consumer. `YoutubeLinkSpan` still opens `ACTION_VIEW` links and offers its existing copy-link dialog; neither needs this layout. |

No name-based resource lookup or reflective class lookup was found in either
module's application/test sources. The three generated binding classes were
outputs of the old layouts, not proof that the layouts had a consumer. Their
removal is checked in regenerated build outputs rather than editing generated
code. At the time of that cleanup, both `free` and `paid` flavors used these
shared main sources; the subsequent block below removes the duplicate flavors.

The `gallery_container` ID also exists in `activity_postitem_list.xml`, so that
layout and its ID are retained. Shared toolbar IDs, themes and strings are not
removed as a side effect. Dependency declarations, SBOM snapshots, minimum SDK,
database schemas, user data and third-party license files remain unchanged.

### Validation of the orphan-file cleanup

- 80 application unit tests and 21 CI-helper/contract tests passed; the existing
  `chanlib` arithmetic smoke test also passed.
- Debug APKs for both `free` and `paid`, plus the `free` instrumentation APK,
  compiled. The three removed binding classes and two removed interface classes
  are absent from regenerated debug outputs for both flavors; the current
  `ActivityGallery2Binding` remains.
- A separate normal `lintFreeDebug` run passed with no errors: 1,020 warnings
  and one hint, down from 1,024 warnings and one hint. `UnusedResources`
  warnings dropped from 490 to 487. Existing unrelated findings remain pending;
  no detector, baseline or warning suppression was added.
- `verifySbom` and `testSbomValidation` passed without regenerating the committed
  snapshots: 154 top-level components and 74 direct declarations, unchanged.
- Full Motorola Edge 20 / Android 12 suite passed: 32 cases, 30 passed, two
  expected opt-in skips, no failures or errors. Existing tests exercise gallery
  playback/autoplay/SWF, tab navigation, restoration, migrations and background
  downloads. The original phone stay-awake setting (`0`) was restored afterward.
- Minified `freeRelease` APK/AAB builds passed, including release vital lint;
  the obsolete binding classes are also absent from regenerated release sources.
  Local artifact sizes changed from 59,384,414 to 59,382,590 bytes (APK) and
  36,450,099 to 36,448,057 bytes (AAB). Such small differences are observations,
  not evidence of a measurable runtime/startup improvement: release shrinking
  already removes unused code/resources. This block primarily reduces obsolete
  source and maintenance overhead.

The later `eeb2e31` matrix run confirmed by the user covers these deletions
cumulatively. That is not validation of subsequent cleanup changes.

## Third cleanup: one free application, standard build types

The application no longer has separate free/paid editions. Remove all product
flavors in `mimi-app` and `chanlib`, including the library-only `amazon` flavor;
there are no flavor-specific source directories to merge. Both modules now use
standard `debug` and `release` variants. No dependency declaration, selected
version, SDK level, signing setup or database schema changes in this block.

- Release remains `com.emogoth.android.phone.mimi`; debug remains
  `com.emogoth.android.phone.mimi.beta`. This preserves the previous free app's
  update identity, preference keys and provider authority.
- `verifyApplicationVariants`, wired into `check` and CI, checks the actual AGP
  variant/application-ID map against exactly these two entries. It uses the
  [AGP Android Components API](https://developer.android.com/reference/tools/gradle-api/8.13/com/android/build/api/variant/AndroidComponentsExtension)
  with lazy application-ID providers rather than a deprecated variant API.
- Feedback diagnostics report `BUILD_TYPE` instead of the removed `FLAVOR`.
- CI, its WebView test wrapper, checksums and uploaded artifacts use unflavored
  task/output names. Release/test guides follow those names. Only the obsolete
  build command in the main README changes; its full rewrite remains deferred.
- Rename the tracked release SBOM to `sbom/mimi-reader-release.cdx.json` and
  regenerate both snapshots with the standard configuration names. Comparison
  against the previous commit confirms all 154 component records (including
  versions, hashes and licenses), dependency edges, 74 declarations and every
  resolved inventory graph are unchanged after the variant/configuration rename.

### Validation of variant consolidation

- The actual variant/ID contract and debug/test APK builds passed. All 80 app
  unit tests and the existing `chanlib` arithmetic smoke test passed.
- 22 CI-helper/contract tests passed, including the exact unflavored Gradle
  instrumentation command and workflow task/artifact paths.
- `adb install -r` successfully updated the previous free-debug installation
  on the Motorola Edge 20 / Android 12. All nine existing database/preference
  file SHA-256 values were identical immediately before and after installation;
  launcher startup then reached `TabsActivity` successfully. No app data was
  cleared and no application was uninstalled.
- Full Motorola suite passed through `connectedDebugAndroidTest`: 32 cases,
  30 passed, two expected opt-in skips, no failures/errors. The temporary
  stay-awake setting was restored to its original value (`0`).
- Normal `lintDebug` and SBOM freshness/schema/negative checks passed. Lint has
  no errors and retains the previous 1,020 warnings, one hint and 487
  `UnusedResources` findings; no baseline or suppression was introduced.
- Minified `assembleRelease` and `bundleRelease` passed, including release
  vital lint. Packaged APK metadata confirms the original release ID, version
  code/name (230 / 7.0.1), `minSdk 23` and `targetSdk 36`. APK/AAB and R8 mapping
  exist at the new paths used by CI. Local APK size remains 59,382,590 bytes;
  AAB changes from 36,448,057 to 36,448,009 bytes. This is not a performance
  benchmark or a claim of byte-identical artifacts.

The user confirmed the full GitHub Actions run for `eeb2e31` passed, including
the unflavored Android 10/12/14/16 matrix and artifact uploads. This is the
remote baseline for the next cleanup, not validation of subsequent changes.

## Fourth cleanup: build declarations and testable legacy utilities

The user confirmed `eeb2e31` passed GitHub Actions and requested continued
dependency cleanup/refactoring before vulnerability scanning. Obsolete
duplicate-edition migration notes are removed; no edition-specific migration
implementation remains in Java/Kotlin sources and no installed package or
application data is removed.

- Remove the two empty `flatDir` repositories and the empty `chanlib/libs`
  `fileTree` dependency. None of the root/app/library `libs` directories exists;
  the only tracked JAR is the Gradle wrapper. `chanlib` inherits the central
  repositories instead of redundantly declaring Google/Maven Central again.
- Remove the unused LeakCanary version and commented-out LeakCanary, local-JAR
  and superseded PDF declarations. Keep the actual PDF dependency and licenses.
- Share RxJava **2.2.21** across modules, matching the previously selected
  artifact, and JUnit **4.13.2**, replacing `chanlib`'s test-only 4.12 declaration.
- Retain Kotlin Serialization BOM **1.8.1** and document its indirect consumer.
  `dependencyInsight --configuration releaseRuntimeClasspath --dependency
  kotlinx-serialization --single-path` shows Lifecycle saved-state requests core
  1.7.3, with the BOM selecting 1.8.1. Removing it is not a version-neutral
  cleanup even though there are no direct application Serialization imports.
- Replace the arithmetic example with eight real `ChanUtil` unit tests and
  remove the `ApplicationTestCase` template, which contained no test methods.
  CI now executes `:chanlib:testDebugUnitTest` explicitly as well as app tests.
- Refactor `calculateColorBase` from string formatting/Android color parsing to
  equivalent ARGB bit packing, and remove its unused null-to-empty helper. The
  thread adapter remains its real consumer. Unit coverage includes negative/
  overflowing hashes, fixed alpha, collisions, supplementary Unicode and null
  failure semantics; an instrumented test compares 136 deterministic inputs
  with the legacy Android parser on every matrix API.

### Validation of this block

- 80 app unit tests, eight actual library unit tests and 22 CI-helper/contract
  tests passed. Debug APK/test APK and the application variant-ID contract passed.
- The runtime SBOM snapshot is byte-for-byte unchanged: 154 components, 152
  Maven components and 74 direct declarations. The dependency inventory changes
  only the two RxJava requests, the library JUnit request and its test resolution
  from 4.12 to 4.13.2; no release, processor or build-tool graph version changes.
- Full Motorola Edge 20 / Android 12 suite: 33 cases, 31 passed, two expected
  opt-in skips, no failures/errors, including the new color-compatibility test.
  The temporary phone stay-awake setting was restored to its original value (`0`).
- Normal debug lint passed with no errors and the unchanged 1,020 warnings,
  one hint and 487 `UnusedResources` findings. SBOM schema/freshness and negative
  tests passed; no lint baseline or detector suppression was added.
- Minified release APK/AAB and release vital lint passed. Packaged metadata
  retains the original release ID, version 230 / 7.0.1, `minSdk 23` and target
  API 36. Local APK size remains 59,382,590 bytes; AAB changes from 36,448,009
  to 36,447,955 bytes. These are artifact observations, not startup/performance
  measurements. The later remote run 21 for `6541737` confirms this block and
  the posting changes cumulatively on Android 10/12/14/16.

## Fifth cleanup: a deliberate AndroidX UI baseline

GitHub Actions [run 21 for `6541737`](https://github.com/jagumiel/mimi-reader/actions/runs/39409405146)
passed the build/lint/unit/SBOM job and all four instrumented jobs. This confirms
the previous cleanup and posting regression coverage, not the new migration below.
Human CAPTCHA acceptance on a physical Android 10 device remains pending; only
the Android 12 phone is attached. No further public messages are sent for this block.

Dependency insight and the upstream POM identify the unintended alpha path:
`recyclerviewfastscroller:0.2.3` requests AppCompat `1.3.0-alpha02`, whose POM
requests Fragment `1.3.0-alpha08`. Preference KTX also requested Fragment KTX
`1.2.4`. Pin an explicit shared baseline rather than inheriting those versions:

| Library | Previous selected version | New selected version / source |
| --- | --- | --- |
| AppCompat + resources | 1.3.0-alpha02 | [1.8.0 stable](https://developer.android.com/jetpack/androidx/releases/appcompat), released 12 August 2026 |
| Fragment + compatibility KTX artifact | 1.3.0-alpha08 / 1.2.4 | [1.9.1 stable](https://developer.android.com/jetpack/androidx/releases/fragment), released 23 September 2026 |
| Preference + KTX | 1.1.1 | [1.2.1 stable](https://developer.android.com/jetpack/androidx/releases/preference), released 26 July 2023 |

The app directly declares Fragment and shares the three versions from the root
build. No global force, exclusion or SDK increase is used. Existing fast-scroller
and Novoda integrations remain: this changes their selected AndroidX dependencies,
not their implementations. Material, PDF and image-loader modernization are separate.

The stricter nullability contracts initially caused compilation errors. Adapt the
Preference callback's non-null parameters and nine preference accesses to attached
fragment `requireContext()` calls; do not suppress nullability checks. Remove the
obsolete settings target-fragment link, which has no consumer. Preference's own
internal dialog linkage is left to the library. Install the root settings fragment
only on a fresh activity: replacing it on every `onCreate` discarded the restored
nested screen/back stack and could strand a restored preference dialog.

The reviewed runtime graph grows from 154 to 158 components. Five components are
added (Emoji2 and its views helper, ResourceInspection annotation, Android tracing,
Window); the old Collection KTX node disappears as its functionality is merged
upstream. Fifteen retained Maven components change version, including CustomView,
SlidingPaneLayout, Transition, Annotation, Collection and Tracing alongside the
three primary groups. The inventory grows from 74 to 75 declarations due to the
explicit Fragment baseline. Ruffle's component record and seven vendored hashes
remain unchanged. This is a maintenance/compatibility change, not a size reduction.

Legacy umbrella dependencies are **not** proven unused: `post_filter_view.xml`
still contains five `androidx.legacy.widget.Space` views, and DrawerLayout,
SlidingPaneLayout and SwipeRefreshLayout have code/XML consumers. Replace or
declare those consumers explicitly in a separate tested cleanup before removing
the umbrella artifacts; do not delete them on the basis of absent imports alone.

### Validation of the AndroidX UI migration

- Debug APK/test APK, 94 app unit tests, eight library unit tests and 22 CI-helper
  tests passed. The existing debug/release application-ID contract also passed.
- Both new device tests passed in isolation, then the complete Motorola Edge 20 /
  Android 12 / WebView 155 suite passed: 42 cases, 39 passed, three expected
  opt-in skips, no failures/errors. This includes posting fixtures, gallery/SWF,
  tab and legacy-panel restoration, back navigation, insets, migrations and
  downloads. No public messages were sent. The temporary stay-awake setting was
  restored to its original value (`0`).
- Dedicated `lintDebug` passed: zero errors, 1,019 warnings, one hint and 485
  `UnusedResources` findings. No detector, baseline or suppression was added;
  these existing warnings are not evidence that every flagged resource is unused.
- Reviewed and regenerated both SBOM/inventory snapshots. Schema, freshness,
  negative-document, bundled-hash and canonical-output checks passed. Processor,
  desugaring, root build-tool and library unit-test graphs remain unchanged;
  application runtime/unit/instrumentation resolution reflects the UI update.
- Minified release APK/AAB and release vital lint passed, without new R8
  suppressions. Packaged metadata retains `com.emogoth.android.phone.mimi`,
  version 230 / 7.0.1, `minSdk 23` and target API 36. Compared with the posting
  baseline, local APK size grows from 59,400,570 to 59,452,652 bytes; AAB grows
  from 36,459,792 to 36,642,531 bytes. These are local artifact observations,
  not startup/performance measurements or a byte-identical-build guarantee.
- [Run 22 for `04b3a42`](https://github.com/jagumiel/mimi-reader/actions/runs/39469404001)
  passed all five jobs, confirming this migration on Android 10/12/14/16 as well
  as build/lint/unit/SBOM. Physical Android 10 human-interaction acceptance remains
  pending; emulator success does not establish live CAPTCHA provider acceptance.

## Sixth cleanup: explicit legacy consumers and platform spacers

Remove the app's three direct `androidx.legacy` umbrella declarations rather than
using them to supply unrelated APIs implicitly. The source/XML audit found five
actual consumer groups; declare them explicitly at their **previously selected**
versions. This is not an upgrade to those libraries' newest releases.

| Direct dependency | Version retained | Actual consumer |
| --- | --- | --- |
| CoordinatorLayout | 1.1.0 | Main/detail/gallery XML, FAB offset/scroll behaviours |
| DocumentFile | 1.0.0 | SAF downloads, attachments, gallery and folder preferences |
| DrawerLayout | 1.0.0 | Main/list/detail/sliding-panel screens and system insets |
| SlidingPaneLayout | 1.2.0 | Legacy two-panel activity |
| SwipeRefreshLayout | 1.0.0 | Catalog/thread refresh layouts and fragments |

Replace all five `androidx.legacy.widget.Space` instances in the filter editor
with fully qualified `android.widget.Space`, retaining their dimensions, row/
column spans and order. [Platform Space](https://developer.android.com/reference/android/widget/Space)
exists since API 14, below this application's unchanged minimum API 23; a backport
is unnecessary. The offline regression test checks all five invisible spacer
heights, the ten-column layout, controls at 320/480dp, preserved text and edit/
cancel callbacks across light/dark/black themes. It keeps the editor detached,
does not load boards, and never presses Save or writes user filters.

**Runtime removal is not claimed.** The selected graph still includes the legacy
umbrellas: PDFium 1.9.0 requests `legacy-support-v4`, and GridLayout 1.0.0 requests
`legacy-support-core-ui`. Their downstream Loader/Print/LocalBroadcastManager/Media
artifacts are still reachable. Do not exclude them globally merely because the
app has no corresponding imports. Review PDF/native compatibility and GridLayout
separately, including their own bytecode/resource consumers. Fast-scroller and
Novoda remain unchanged in this block too.

There are still 158 components. Every component record (versions, licenses,
artifact and Ruffle hashes) and non-application dependency edge is unchanged.
Only the application's root edges replace three umbrellas with five actual
consumers. All seven inventoried configurations retain the same selected IDs/
versions; direct/transitive flags change where appropriate. The declaration
count grows from 75 to 77 because three broad declarations become five precise
ones. This improves ownership and auditability, not necessarily APK size.

### SBOM graph freshness discovered during this cleanup

The first generation reused `cyclonedxDirectBom` as up-to-date: the artifact set
was identical, but its cached application edges still described the previous
declarations. Regenerating only the canonical wrapper cannot fix a stale input.
Always refresh the raw graph alongside the wrapper and validate its application
edges against current resolved runtime/desugaring **direct** dependencies plus
manual components. This catches a same-components/different-edges stale report.
Two additional negative cases reject an extra transitive-as-direct edge and a
missing AppCompat root edge, even when the rest of the document remains valid
and its components are reachable. The verifier now rejects six malformed cases,
in addition to the existing bundled-hash and canonical-ordering checks.

### Validation of explicit consumers and SBOM edges

- Debug APK/test APK compiled; 94 app unit tests, eight library tests and 22
  CI-helper tests passed. The application variant-ID contract passed.
- Dedicated debug lint passed with zero errors, the unchanged 1,019 warnings,
  one hint and 485 `UnusedResources` findings. No new suppression or baseline.
- Regenerated raw/canonical SBOM reports, reviewed the exact three-removed/five-
  added root edges, and passed all six malformed-document cases plus hash and
  normalization checks. A separate `verifySbom testSbomValidation` invocation
  refreshed the raw report again and passed without updating the snapshots.
  All component records and non-root edges are unchanged.
- Minified release APK/AAB and release vital lint passed without new R8 rules.
  Packaged metadata retains the release ID, version 230 / 7.0.1, minimum API 23
  and target API 36. Local APK size changes from 59,452,652 to 59,452,644 bytes;
  AAB changes from 36,642,531 to 36,640,635 bytes. No runtime performance or
  startup improvement is inferred from these small artifact differences.
- After the owner unlocked the phone, the complete Edge 20 / Android 12 suite
  passed: 43 cases, 40 passed, three expected opt-in skips, no failures/errors.
  The new three-theme/two-width filter case passed alongside navigation,
  restoration, insets, gallery/SWF, posting fixtures, migrations and downloads.
  No filters were saved and no public posts were made. The temporary stay-awake
  setting was restored to its original value (`0`).
- [Run 23 for `e485a71`](https://github.com/jagumiel/mimi-reader/actions/runs/39473553462)
  passed all five jobs, confirming this cleanup and SBOM edge verification on
  API 29/31/34/36. This does not validate subsequent GridLayout changes.

## Seventh cleanup: GridLayout and native compatibility findings

Update the only GridLayout declaration from 1.0.0 to
[stable 1.1.0](https://developer.android.com/jetpack/androidx/releases/gridlayout#1.1.0).
The release removes the legacy library dependency and expects platform `Space`,
which the previous block already installed. Upstream puts this library in
maintenance mode; this bounded update does not imply an application-wide Compose
migration. The filter editor remains its single production consumer.

Strengthen its existing offline three-theme/two-width test to check horizontal
bounds, non-overlapping inputs, actions below the inputs, a shared action row,
non-overlapping buttons and vertical containment. Run the stronger test against
the old 1.0.0 baseline first, then against 1.1.0 with the complete suite. No
filter/preference write or public post is part of this validation.

The reviewed SBOM still has 158 components and 77 declarations. Only GridLayout's
component record changes version/hash; its outgoing dependencies are now Core,
Annotation and JSpecify, all already selected elsewhere. Other component records,
including native PDF/GIF artifacts and Ruffle, are unchanged. PDFium still requests
`legacy-support-v4`, retaining the old umbrella subtree. Do not claim those legacy
components have disappeared or add exclusions to force that appearance.

### Native audit: a separate pre-release requirement

Inspect the existing release APK and its stripped native build inputs with
`readelf -lW`, and cross-check AAR ownership. The following are ELF **PT_LOAD
alignment** observations, not results from a 16 KB runtime:

| Origin / library | arm64-v8a alignment | x86_64 alignment |
| --- | --- | --- |
| PDFium 1.9.0: `libc++_shared.so`, `libjniPdfium.so` | 0x10000 (64 KB) | 0x1000 (4 KB) |
| PDFium 1.9.0: `libmodft2.so`, `libmodpdfium.so`, `libmodpng.so` | 0x1000 (4 KB) | 0x1000 (4 KB) |
| android-gif-drawable 1.2.21: `libpl_droidsonroids_gif.so` | 0x1000 (4 KB) | 0x1000 (4 KB) |

All twelve entries exist in the APK, with SHA-256 values matching the inspected
stripped build inputs. The PDFium AAR owns its five libraries per
ABI; the GIF AAR owns its decoder. The Edge 20 reports `getconf PAGE_SIZE = 4096`.
Its successful tests, and CI on API 36 alone, do not establish 16 KB compatibility.
The current APK passes `zipalign -c -P 16 4`; ZIP packaging alignment is a separate
check and cannot repair ELF load segments. Even a sufficiently aligned PT_LOAD
entry alone is not full native compatibility certification.

Following the [Android native page-size guidance](https://developer.android.com/guide/practices/page-sizes),
replace or rebuild the affected PDF and GIF integrations and explicitly test in
a 16 KB environment before release. This is a compatibility finding, not a CVE
scan and not a change to native binaries in this block. Preserve API 23 support,
32-bit Android 10 installations and existing user data. Do not silently remove
PDF/GIF support or drop device ABIs to hide the problem.

The PDF follow-up must cover local multipage fixtures, scrolling/zoom, rotation,
gallery page changes, cancellation/resource release, malformed/encrypted files
and visible load errors. Audit `PdfPage`'s asynchronous ready/error handling and
the apparently orphaned `gallery_image_pdf.xml` before any removal. The GIF
follow-up must preserve animation, pause/resume and autoplay's one-cycle advance.

### GridLayout validation

- The stronger filter test passed on the Edge 20 with GridLayout 1.0.0 before
  upgrading (one isolated case). After upgrading, the complete Android 12 suite
  passed: 43 cases, 40 passed, three expected opt-in skips and zero failures/
  errors, including the same geometry assertions. The filter fixture never saves
  filters or changes theme preferences; no public messages were posted.
  Stay-awake was restored to 0.
- Debug APK/test APK, 94 app unit tests, eight library tests, 22 CI-helper tests
  and the application variant-ID check passed. The minimum SDK remains 23.
- Dedicated debug lint passed with zero errors, the unchanged 1,019 warnings,
  one hint and 485 `UnusedResources` findings; no suppression or baseline added.
- Regenerated and reviewed both snapshots; SBOM schema, exact root-edge,
  freshness, six negative-document, bundled-hash and canonical-order checks passed.
- Minified release APK/AAB, R8 and release vital lint passed without new rules.
  Packaged metadata retains the release ID, version 230 / 7.0.1, minimum API 23
  and target API 36. APK size stays at 59,452,644 bytes; AAB changes from
  36,640,635 to 36,641,843 bytes. These local sizes are not runtime performance
  measurements. ZIP alignment passes but the documented ELF/native risk remains.
- [Run 24 for `3c5357f`](https://github.com/jagumiel/mimi-reader/actions/runs/39480315229)
  passed all five jobs, confirming GridLayout on Android 10/12/14/16. It does not
  cover the native replacements below or certify native 16 KB behaviour.

## Eighth cleanup: PDF/GIF native replacement and resource lifecycle

Use the API-compatible Maven Central [PDF viewer fork 3.2.0-beta06](https://central.sonatype.com/artifact/io.github.oothp/android-pdf-viewer/3.2.0-beta06),
which selects `io.github.oothp:pdfium-android:1.9.5-beta01`. Its library minimum is
API 21, so the app remains at API 23; a candidate requiring API 24 was not selected.
The viewer is still a beta-versioned dependency, not presented as a stable release.
Update [android-gif-drawable to 1.2.32](https://github.com/koral--/android-gif-drawable/releases/tag/v1.2.32).
Inspect the actual AAR/APK binaries instead of accepting upstream alignment claims
or treating compressed libraries as an ELF fix.

All 24 libraries across `arm64-v8a`, `x86_64`, `armeabi-v7a` and `x86` remain
packaged. The twelve 64-bit libraries now have PT_LOAD alignment of at least
16 KB. Both ordinary 32-bit Android ABIs remain supported; the fork no longer
ships obsolete MIPS/MIPS64 binaries. That upstream ABI change is disclosed, not
hidden behind an application `abiFilters` exclusion. Physical 32-bit Android 10
PDF/GIF acceptance remains pending; the attached Edge 20 runs 64-bit Android 12.

The graph changes from 158 to 151 components with the same 77 declarations:
replace the two old PDF coordinates and old `pl.droidsonroids:relinker:1.3.1`
with the two oothp coordinates and `com.getkeepsafe.relinker:relinker:1.4.5`.
Only GIF changes version among retained coordinates. Seven obsolete components
become unreachable: all three `androidx.legacy` umbrellas, AsyncLayoutInflater,
LocalBroadcastManager, old Media and Print. Loader/CursorAdapter/ViewPager still
have other consumers and remain; no global exclusion is used. Ruffle is unchanged.

`PdfPage` now reports readiness only after native rendering, not immediately after
starting an asynchronous load. It displays a local error/retry panel for malformed,
password-protected or unsupported documents and download errors. It does not add
a password-entry flow; protected documents remain unsupported in the gallery but
now show a useful error. Rebinding, leaving a page, host pause, detach and gallery
destruction invalidate old callbacks and recycle native resources. Restore page
and zoom per post with bounded, normalized ViewModel/Bundle state (64 entries).
PDF gestures no longer share the generic image tap-to-close listener.

GIF callbacks are scoped to the current drawable, selected page and resumed host.
Pause/deselect/detach stop animation; gallery destruction explicitly recycles it.
Keep loop notifications for autoplay. Remove the obsolete app-level GIF constructor
keep rules and rely on the current library's consumer rule plus the existing JNI
entry-point rule. Remove the unused `gallery_image_pdf.xml` after checking source,
XML and generated-binding consumers; the gallery creates `PdfPage` directly.
Update in-app native component attribution without deleting existing license text.

The replacement AARs do not supply Pdfium JNI constructor keep rules. Binary
strings and source inspection identify `PdfPasswordException` and `util.Size` as
name-resolved JNI types; the first minified mapping had removed the exception.
Add narrow class/constructor keep rules (not a whole-library keep or warning
suppression), and require their entries in release `seeds.txt` through the native
verifier. This checks static R8 preservation, not minified runtime execution.

### Native alignment and runtime regression protection

`scripts/ci/android_native.py` validates ELF class/machine/program-header bounds,
64-bit PT_LOAD alignment/congruence and uncompressed APK entry alignment. It
requires all four retained ABIs, so compression or silently dropping 32-bit code
cannot satisfy the check. This is not a complete ELF/RELRO/security audit; actual
native runtime tests remain required. CI checks debug and minified release APKs,
also runs SDK `zipalign`, and retains reports for 14 days.

The new sixth CI job uses the distinct API 36 `google_apis_ps16k` image and runs
the six mandatory `NativeMediaCompatibilityTest` cases. Its launcher refuses a
physical serial, non-emulator, wrong API or actual page size other than 16384
**before** changing settings. Only then does it root that disposable emulator,
disable and verify [16 KB backcompat](https://developer.android.com/guide/practices/page-sizes#16-kb-backcompat-mode),
and run Gradle scoped to that serial. Each native test also asserts the requested
page size with `sysconf`; a 4 KB job cannot silently pass this gate. Test failures
are not retried. SDK image preparation alone retains its bounded retry policy.

Six local cases cover two-page colour rendering, page changes/zoom, pause/detach/
selection resource release, activity + model/Bundle recreation, damaged/encrypted
files and explicit retry, stale-load cancellation, GIF loop/pause/resume/recycle,
and bounded/invalid viewport state. The encrypted fixture is an original one-page
Standard Security R2 document: opening it with the known test password proves that
the password error is not simply malformed bytes. No live website or public post
is involved.

Local checks: all six cases and the complete Edge 20 / Android 12 suite passed
(49 cases: 46 passed, three opt-in skips, no failures/errors). The original phone
stay-awake value (0) was restored. 94 app + eight library unit tests and 40 CI-helper
tests passed; actionlint 1.7.12 validated the workflow. Debug and minified release
APK ELF/ZIP checks pass for all 24 libraries; SDK `zipalign -c -P 16 4` also passes.
Release APK/AAB, R8, vital lint, SBOM schema/freshness and its six negative cases
passed. The unused PDF binding is absent from regenerated debug/release outputs.
Release metadata retains the package ID, version 230 / 7.0.1, minSdk 23 and target
36. Local release APK size changes from 59,452,644 to 40,797,669 bytes; AAB from
36,641,843 to 30,630,024 bytes, including the fork's MIPS removal and other binary
changes. These are size observations, not startup/performance measurements.
Dedicated lint passed with zero errors, 1,012 warnings, one hint
and 484 `UnusedResources` findings, without a new baseline or suppression.

**Runtime 16 KB is confirmed by green run 27 for `fed11d2`.** All six native
cases passed with `PAGE_SIZE=16384` and backcompat disabled. A local
API 36 ps16k image/AVD was installed, but Windows emulator 37.1.11 with AEHD 2.2
did not reach ADB (one launch exited, another stalled); only this task's emulator
processes were stopped. No local 16 KB runtime success is claimed. Do not use the
old five-job run 24 as evidence for this new six-job commit. Keep the release gate,
physical Android 10 checks, CVE scanning and the owner-approved changelog pending;
each new cleanup must pass the six-job workflow again.

Run 26 initially passed the build/release/native guards and reached a real
16 KB emulator, with four of six native cases passing. Two fixture issues
(zoom/position call order and startup cache cleanup deleting a test PDF) prevented
full acceptance. [CI diagnosis and fixture corrections](CI.md#run-26-native-pdf-fixture-isolation-and-viewport-positioning)
record the evidence; they do not change production code, dependency versions,
ABI coverage or the requirement for a green six-job workflow.

## Ninth cleanup: replace the Novoda browser adapter

Remove `com.github.novoda:simple-chrome-custom-tabs:0.1.6`. Its only navigation
consumer was `LinkSpan`; the application initialized a global instance and
TabsActivity, SlidingPanelActivity and PostItemDetailActivity connected and
disconnected its browser service. Replace that with a stateless first-party
`BrowserNavigator` using the already-declared/resolved `androidx.browser:browser:1.2.0`.
Do not update Browser or unrelated AndroidX libraries in this bounded removal.
The [AndroidX CustomTabsIntent API](https://developer.android.com/reference/androidx/browser/customtabs/CustomTabsIntent)
builds a regular view intent carrying the Custom Tab extras; no application-wide
service binding or installed-package query is needed for this navigation path.

Preserve LinkSpan's external-opening preference, underline/link colour and long
press open/share/copy menu. Copy the toolbar colour when its background is a
ColorDrawable, without the old unsafe cast. Keep HTTP(S) URLs intact and let
non-web schemes use their normal ACTION_VIEW handler. A missing/blocked custom
tab falls back to a fresh ordinary view intent; if neither starts, show the
existing opening-error message rather than crash. Activity-backed contexts,
including wrappers, keep the existing task; application contexts get NEW_TASK.
No activity/session is stored and browser warm-up is no longer requested.

Five mandatory `BrowserNavigatorTest` cases intercept all outgoing intents:
AndroidX extras/colour/URL, actual LinkSpan preference dispatch and styling,
non-web/external modes, missing/blocked-handler recovery, and Activity/wrapped
context flags. Preference overrides exist only in the recording test context;
the user's stored preference is checked unchanged. No real browser is opened,
no test URL is fetched and no public message is posted by these cases. The five
cases passed separately on the Edge 20 / Android 12; real browser-provider UI
acceptance is distinct from this intercepted-intent coverage.

Regenerated snapshots lose exactly Novoda: **151 -> 150 components** and
**77 -> 76 declarations**. Every retained component record (version, Package URL,
hashes and metadata) matches the preceding snapshot, including Ruffle and all
native artifacts. Runtime relationships lose the Novoda node/edge; no global
exclusion or substitute dependency is added. Keep fast-scroller for the next
separate review, physical Android 10 acceptance as a release gate, and CVE
scanning/changelog/README sequencing unchanged.

### Ninth-block validation (22 October 2026)

- Five new cases passed separately, then the complete Edge 20 / Android 12 suite
  passed: **54 cases, 51 passed, three existing opt-in skips**, no failures/errors.
  The original stay-awake setting (0) was restored. Provider UI/real browser
  hand-off is not claimed by the intercepted-intent tests.
- 96 app unit tests, eight chanlib unit tests and 40 CI-helper tests passed.
  Debug/test APK compilation, variant-ID contract and actionlint also passed.
- Debug lint: zero errors, 1,012 warnings, one hint and 484 UnusedResources
  findings, with no new baseline or suppression. Release APK/AAB and vital lint
  passed with R8 and no new keep/dontwarn rules.
- SBOM freshness and six malformed-document/hash/canonical-output checks passed.
  Compared every retained component record and every retained dependency edge
  against the preceding snapshot: only Novoda's node/edge disappears.
- Both APKs passed the 24-library/four-ABI native ELF/ZIP guard. Release JNI seeds
  and SDK `zipalign -c -P 16 4` passed. Native binaries are unchanged; this does
  not replace the new commit's six-job remote run.
- Local release APK size is unchanged at **40,797,669 bytes**; the AAB changed
  from **30,629,989 to 30,623,925 bytes**. Padding/resource shrinkage can obscure
  small removals in APK size. These are artifact observations, not a startup or
  browser-performance benchmark. Release identity/version/minSdk/target remain
  `com.emogoth.android.phone.mimi`, 230 / 7.0.1, 23 and 36 respectively.

## Remaining legacy-file review

A Git last-touch audit found 534 tracked files last changed in 2016 and 100 in
2019. Age alone is not a deletion criterion: `BoardConverter`, `PostConverter`,
`CodeSpan`, `DelegatingSocketFactory`, `BetterViewAnimator` and `RequestQueueUtil`
still have source or layout consumers, and license notices remain necessary.

The five confirmed candidates above are removed in this block. Before removal,
the debug lint report contained 490 `UnusedResources` warnings; that number is
not a count of safe-to-delete files. Other findings still require manual
confirmation against view binding, manifest entries, themes, variants and
indirect resource usage. Keep migration assets, font assets, third-party
licenses and CAPTCHA paths until their actual consumers and upgrade requirements
have been checked. The two `chanlib` test templates have now been replaced or
removed with meaningful unit/device coverage, as described above.

The remaining `openMarketLink` method is not an orphan: the More Info rating
preference still calls it. Its legacy Amazon/Play Store routes need a separate
settings/link review given the app's withdrawal from Play Store; do not delete
only the method and leave a live caller behind. This block does not change that UI.

## Sequence for subsequent iterations

1. The fourth cleanup (`faaf5a7`) and posting changes (`5ff224a`, `6541737`)
   are confirmed remotely by green run 21 on Android 10/12/14/16.
2. Retain the pending physical Android 10 CAPTCHA/keyboard/browser acceptance
   checks. On Android 12, human resolution and one separately authorized text
   reply succeeded; do not repeat public submissions for automated regression.
   [Posting contracts and validation limits](POSTING.md) record that scope.
3. Runs 22–24 confirm the preceding UI cleanup. Run 27 confirms all **six** jobs
   for `fed11d2`, including six native cases on actual 16 KB pages without
   backcompat. Every subsequent cleanup needs its own green six-job workflow.
4. Novoda is replaced in the ninth block; review the still-used fast-scroller
   next, separately. Physical Android 10/32-bit media and CAPTCHA acceptance
   remains required before release. Keep embedded native source revision/security
   provenance distinct from wrapper Maven versions.
5. After posting recovery and the agreed cleanup/refactoring gates, add CVE/GHSA scanning of the
   cleaned SBOM to GitHub Actions, with scheduled runs, SARIF output and reviewed
   time-limited exceptions. Do not treat the scanner as the automatic next task
   while these cleanup blocks remain outstanding.

The 8.0 changelog still requires agreement with the project owner; the main
README remains the final documentation task.
