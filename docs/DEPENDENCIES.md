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
The tenth replaces the archived Quiph fast-scroller and adapts scrolling to variable-height posts.
The eleventh updates Material Components and its required ConstraintLayout baseline without changing themes.
The twelfth updates Glide and removes first-party use of its deprecated generated API.
The thirteenth replaces PercentLayout's sole consumer with a platform weighted row.

The canonical release SBOM has **152 top-level components**: 150 Maven components,
the local `chanlib` module and Ruffle. Ruffle contains seven individually hashed
files. The inventory records **74 direct declarations** across the two modules;
repeated declarations and dependency constraints are not 74 distinct libraries.
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
| UI libraries | Preference is **1.2.1**, Material **1.14.0**, ConstraintLayout **2.2.1** and Glide **5.0.7**. PercentLayout is removed; Browser 1.2.0 still has consumers. | Continue bounded settings/link review. Confirm Android 10/12 compatibility for each replacement; retaining MaterialComponents themes is not a Material 3 redesign. |
| Kotlin | stdlib 2.3.21 coexists with jdk7/jdk8 compatibility artifacts 1.9.10 and kotlin-bom 1.8.22 (selected through Material's metadata). | Inspect dependency paths and distinguish compatibility/constraint artifacts from duplicate executable code before removing anything. |
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

## Tenth cleanup: thread fast-scrolling with variable-height posts

Replace `com.quiph.ui:recyclerviewfastscroller:0.2.3`: its
[upstream repository is archived](https://github.com/quiph/RecyclerView-FastScroller).
Use the official Maven Central artifact `me.zhanghai.android.fastscroll:library:1.3.0`
from [AndroidFastScroll](https://github.com/zhanghai/AndroidFastScroll). The pinned
AAR's manifest requires API 21, below this app's unchanged minimum of 23. Its
AppCompat/RecyclerView requests resolve to the existing app selections; no
unrelated upgrade, repository, force or global exclusion is added.

The upstream default helper assumes equally tall rows. Thread posts are not
uniform, so the first-party `ThreadFastScroll` implements index-based progress,
including partial first-row progress and last-post targeting. It targets
posts with `LinearLayoutManager.scrollToPositionWithOffset`, rather than using
the first visible row as a pixel-height estimate. Retain the `position / total`
popup and accent background. The library supplies standard thumb/track drawing
and RTL placement. The old adapter popup interface is removed; counts come from
the current adapter rather than a captured thread list.

Remove the obsolete outer layout so `MimiRecyclerView` is SwipeRefreshLayout's
direct child. Reserve only the scrollbar touch edge from refresh interception;
normal content pulls still refresh. Attach once per view via a keyed tag, cancel
active drags on data changes/replacement/detach, and unregister the observed
adapter on detach. This is necessary because the library's disabled-range path
ignores CANCEL once an emptied list no longer has a scrollbar. The initial device
test reproduced that pending-drag state; the integration cancels before the
disabled-state update. Reattachment retains one set of hooks and resumes observing
the current adapter without cancelling a fresh first gesture.

Six pure unit cases cover labels, short/unmeasured lists, both endpoints,
intermediate indices, bounds and overflow. Four mandatory instrumented cases
inflate the production layout and dispatch synthetic MotionEvents through its refresh
container, using local variable-height text rows: index targets, dragging/popups
in three themes/two widths/LTR-RTL, content refresh after cancellation, and empty,
short, replaced and reattached adapters. They also check popup bounds, no duplicate
controller/decorations, cancellation when data empties mid-drag and recovery on
replacement. All four cases passed separately on the Edge 20 / Android 12.
No real thread/media fixture, preference write or public post is used by them.

Remove five verified orphan thumb/track drawables, including their now-unresolved
reference to the former library's `corner_radius`; do not add an unused replacement
dimension to mask resource-linking errors. Keep the live popup drawable/dimension.
Add AndroidFastScroll's Google LLC/Apache-2.0 attribution without removing other
notices. Deleted tracked resources remain recoverable from Git history.

SBOM/inventory remain **150 components / 76 declarations**: one scroller artifact
replaces another. Every retained component record and retained dependency edge
outside the two replaced nodes is unchanged, including Ruffle and native media.
Wrapper removal is not a claim that all old UI dependencies are modernized or
that component versions identify embedded native source revisions. Keep the
physical Android 10, remote six-job and later CVE/changelog/README gates intact.

### Tenth-block validation (22 October 2026)

- All four new device cases passed separately after the cancellation/attach
  corrections, then the full Edge 20 / Android 12 suite passed: **58 cases,
  55 passed, three existing opt-in skips**, zero failures/errors. The expanded
  final run also checks successful dragging after adapter replacement and
  cancellation when replacing an adapter mid-drag. The
  original phone stay-awake setting (0) was restored. No public posts were made.
- 102 app + eight chanlib unit tests and 40 CI-helper tests passed. Debug/test
  APK compilation, application-ID contract and actionlint passed.
- Lint passed with zero errors, 1,012 warnings, one hint and 484 UnusedResources
  findings, without adding suppressions/baselines. The initial resource-link
  failure is resolved by removing genuinely unused legacy drawables, not by
  restoring their dependency on the removed library's dimension.
- SBOM freshness/six negative/hash/canonical checks and minified release APK/AAB
  generation passed. Release mapping contains no Quiph scroller classes. Both
  APKs passed the native guard (24 libraries, four ABIs); release JNI seeds and
  SDK 16 KB zipalign passed. No new keep/dontwarn rules were needed.
- Release identity remains `com.emogoth.android.phone.mimi`, version 230 / 7.0.1,
  minSdk 23 and target 36. Local APK changed from **40,797,669 to 40,794,579 bytes**;
  AAB changed from **30,623,925 to 30,624,238 bytes**. These small mixed changes
  are artifact observations, not evidence of faster startup, less memory or a
  smaller artifact in every format. Native 16 KB execution and the ordinary API
  matrix for this new cleanup still require its own remote six-job run.

## Eleventh cleanup: Material Components and its required gallery baseline

On 22 October 2026, replace Material **1.2.1 with 1.14.0**, the latest stable
release in Google's Maven metadata at review time. Its published AAR manifest
requires minSdk **23**, matching the application. Keep compile/target 36, the
existing IDs/version, MaterialComponents light/dark/black/gallery themes and
dialog overlays. Do not switch to Material 3/Expressive or opt into a visual
redesign, dynamic colours or different system-inset handling.

Primary sources:

- [Material 1.14.0 release and dependency changes](https://github.com/material-components/material-components-android/releases/tag/1.14.0).
- [Published Material POM](https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.14.0/material-1.14.0.pom)
  and [AAR](https://dl.google.com/dl/android/maven2/com/google/android/material/material/1.14.0/material-1.14.0.aar).

Material requires ConstraintLayout **2.2.1**, so update the application's direct
gallery declaration from 2.0.1 to that actual baseline in the same block rather
than pinning the old solver or suppressing transitive updates. This dependency
requirement is why the two families are reviewed together. DrawerLayout remains
declared at 1.0.0 but resolves to Material's 1.1.1; record that distinction instead
of claiming every direct declaration now matches its resolution.

Reviewed release snapshot changes (**150 → 153 components**, still **76 direct
declarations**):

| Component | Previous → selected |
| --- | --- |
| Material | 1.2.1 → 1.14.0 |
| ConstraintLayout | 2.0.1 → 2.2.1 |
| CustomView | 1.1.0 → 1.2.0 |
| DrawerLayout | 1.0.0 → 1.1.1 |
| Transition | 1.4.1 → 1.5.0 |
| Kotlin BOM (constraints, not a second stdlib) | 1.8.0 → 1.8.22 |
| ConstraintLayout solver | Remove 2.0.1; add constraintlayout-core 1.1.1 |
| DynamicAnimation | Add 1.1.0 |
| graphics-shapes / graphics-shapes-android | Add 1.0.1 POM / Android artifact |

The six retained version changes, four additions and one removal are the complete
component-record diff. Other retained records, including Ruffle's seven file
hashes and PDF/GIF artifacts, are unchanged. Incoming/outgoing graph edges update
with these selections; no global exclusions or new R8 keep/dontwarn rules are
introduced. The higher component count is not proof of bloat or a security result.

Three mandatory offline `MaterialComponentsCompatibilityTest` cases cover:

- The production tabs layout with synthetic pages: bidirectional TabLayout/
  ViewPager2 selection, chrome/FAB bounds, clickable FAB, and its movement and
  restoration when collapsing/expanding AppBarLayout, in three themes with
  320dp LTR / 480dp RTL layouts.
- Single-choice dialog and confirmation callbacks, plus visible Snackbar action
  and dismissal, in all three themes. AlertDialog's positive callback is queued
  on the main looper: an initial baseline-test assertion incorrectly checked it
  synchronously. Wait for dispatch, then assert exactly one callback; the app
  itself is not changed to accommodate the fixture.
- Both production ConstraintLayout gallery containers, checking their child
  fills the available bounds without clipping at those widths/directions/themes.
  Release each fixture's media player explicitly.

All three cases passed on the Edge 20 / Android 12 **before** the upgrade with
Material 1.2.1 / ConstraintLayout 2.0.1. The complete post-upgrade suite also
retains startup/insets/recreation, settings, drawer/back navigation, gallery
playback/autoplay and native media tests. These checks do not replace human
keyboard/CAPTCHA/browser acceptance or a physical Android 10/32-bit test.

### Eleventh-block validation (22 October 2026)

- Full Edge 20 / Android 12 suite: **61 cases, 58 passed, three existing opt-in
  skips**, zero failures/errors, including all three new cases and the six
  native tests. The old-version baseline's three corrected fixtures also passed.
  No public posts were made; the original stay-awake setting was restored to **0**.
- **102 app + eight chanlib unit tests**, **40 CI-helper tests**, actionlint,
  application-ID contract, debug/test APK builds and minified release APK/AAB
  generation all passed. SBOM freshness/six negative/hash/canonical checks pass
  with the reviewed 153 components and 76 declarations.
- Lint passes with **zero errors, 1,014 warnings, one hint** and 482
  UnusedResources findings (previous block: 1,012 warnings / 484 unused).
  No suppression or baseline is added; no lint finding points to the new fixture.
  Existing warnings remain follow-up work, not a safe deletion list.
- Both APKs pass native ELF/ZIP checks (24 libraries / four ABIs), release JNI
  seed checks and SDK 16 KB zipalign. Release retains package
  `com.emogoth.android.phone.mimi`, version **230 / 7.0.1**, minSdk **23**, target
  **36**. APK changes from **40,794,579 to 41,393,389 bytes**; AAB from
  **30,624,238 to 31,128,081 bytes**. Record this increase without claiming
  measured performance improvements or that every added library survives R8.
- Run 29 confirms the preceding tenth block; this new block still needs its own
  complete remote six-job run, including actual native 16 KB execution. The
  physical Android 10/32-bit and human-interaction acceptance gates remain.

## Twelfth cleanup: Glide image loading, fallback and caching

On 22 October 2026, update Glide, OkHttp integration and compiler together from
**4.11.0 to 5.0.7**, sharing one Gradle version. At review time Maven Central
publishes 5.0.9 as the newest stable version, but its AAR metadata requires
**minCompileSdk 37**. The published 5.0.7 AAR retains minSdk 14 and imposes no
higher compile-SDK baseline. Keep the application's **minSdk 23 / compile and
target 36** rather than bypassing AAR checks or widening this block into an SDK
migration. Published 5.0.8 and 5.0.6 artifacts are absent from Central; GitHub
release names alone are not evidence that a Maven coordinate is available.

Primary sources:

- [Glide generated-API migration guidance](https://bumptech.github.io/glide/doc/generatedapi.html)
  and [coordinated error requests](https://bumptech.github.io/glide/doc/options.html#starting-a-new-request-on-failure).
- [Glide 5.0.7 release](https://github.com/bumptech/glide/releases/tag/v5.0.7),
  [published POM](https://repo.maven.apache.org/maven2/com/github/bumptech/glide/glide/5.0.7/glide-5.0.7.pom)
  and [AAR](https://repo.maven.apache.org/maven2/com/github/bumptech/glide/glide/5.0.7/glide-5.0.7.aar).
- [Glide 5.0.9 AAR](https://repo.maven.apache.org/maven2/com/github/bumptech/glide/glide/5.0.9/glide-5.0.9.aar)
  for the explicit compile-SDK requirement; do not call 5.0.7 the latest upstream release.

Replace every first-party `GlideApp` / `GlideRequest` consumer with `Glide` /
`RequestBuilder`, including thumbnails, flags, history/drawer/reply recycling and
video previews. Keep `MimiAppGlideModule` and the compiler: module/integration
registration remains supported and necessary even though the generated convenience
API is deprecated. The processor still emits convenience classes and its internal
factory can use generated subclasses; first-party callers no longer name them.
This is not a claim that every generated class disappears from R8 output. Do not migrate
Room/KAPT or change unrelated libraries in this block.

The fallback helper now binds to its target view and no longer accepts a redundant
Context argument; both production callers are updated. Use Glide's `.error(...)`
coordinator instead of starting another `into()` inside `onLoadFailed`. Apply
nullable placeholders only when present. Deliver the listener on primary success
or the fallback's terminal result, suppressing the intermediate primary failure.
This fixes the lost primary-success listener when a fallback is configured.
Current production flag callers pass no fallback; the failing fallback branch is
a latent helper defect, not an established explanation for earlier user reports.

Four mandatory offline `GlideCompatibilityTest` cases cover:

- The actual application registry contains the OkHttp model loader. A separate
  OkHttp client with a synthetic interceptor exercises that integration's image
  decoding and HTTP 404 callback. No real request, server, custom CA, cleartext
  exemption or replacement of Glide's registry/singleton is used.
- Local red/blue thumbnail decoding and reused-view replacement, request clearing,
  plus SWF/missing-image placeholders. `clear()` may restore the original placeholder;
  assert that the old image disappears, not that the drawable must be null.
- Primary/fallback success and terminal failure, with/without fallback and a null
  placeholder, checking exactly one terminal callback and no intermediate failure.
- A decoded image reloaded cache-only from **RESOURCE_DISK_CACHE** after clearing
  memory, checking pixels rather than merely request completion. Glide notifies
  targets before deferred disk encoding completes; poll cache-only availability
  within the same total ten-second deadline, never fall back to a source reload.
  Fixtures live in
  a JUnit TemporaryFolder under filesDir, not the startup-cleaned app cache.

The pre-change baseline passed transport registration/fetching and disk-cache
tests. It exposed a real null-placeholder unboxing crash in the fallback helper;
the first thumbnail fixture also incorrectly expected `clear()` to return a null
drawable. Fix that fixture to check removal/replacement of the previous image.
Sample decoded pixels through a software copy so hardware bitmap decoding stays
enabled in production. Existing cache-cleaner and gallery playback/autoplay tests
remain mandatory. Full-media download concurrency stays **two**, and the complete
gallery queue still prioritizes nearest items; no ten-item limit or new parallel
prefetch is introduced.

Runtime SBOM remains **153 components / 76 direct declarations**. Exactly five
component records change from 4.11.0 to 5.0.7: Glide, okhttp3-integration,
annotations, gifdecoder and disklrucache (now packaged as an AAR rather than a JAR).
Glide gains an edge to the already-selected Tracing 2.0.0 platform; this does not
add or update a Tracing component. All other retained records, including
Ruffle's seven hashes, OkHttp and the PDF/native GIF player, are unchanged.
The separate build inventory also updates the compiler; its Guava/JavaPoet
dependencies remain build-time tools, not new shipped application libraries.
No global exclusions, new R8 keep/dontwarn rules or new test dependencies are added.

### Validation setup corrections

The first complete 65-case run exposed two setup issues. The unchanged settings
test tried a transaction after state was saved; the Android event log shows its
activity resumed and was immediately paused/stopped for `sleep` at 21:22:26.
The phone was unlocked when inspected, so the exact cause of that sleep event is
not established. Wake the device and repeat acceptance, without changing the
settings transaction to allow state loss or omitting the test.

The new disk-cache test also raced deferred encoding. In the versioned
[DecodeJob implementation](https://github.com/bumptech/glide/blob/v5.0.7/library/src/main/java/com/bumptech/glide/load/engine/DecodeJob.java),
`notifyComplete` precedes `deferredEncodeManager.encode`. Receiving pixels is not
proof that the encoded file is already readable. The corrected test waits for
cache-only availability within the original **total ten-second deadline**, still
requiring red pixels and RESOURCE_DISK_CACHE. Cache misses cannot trigger a
source/network load. All four Glide cases and all three existing UI compatibility
cases then passed together in a separate seven-case run, without skips.

### Twelfth-block validation (22 October 2026)

- Final complete Edge 20 / Android 12 run: **65 cases, 62 passed, three existing
  opt-in skips**, zero failures/errors. All four Glide cases, existing settings
  restoration and six native cases pass, including cache reuse after the earlier
  cache-cleaner case. No public posts are made. The original phone stay-awake
  setting is restored to **0**.
- **102 app + eight library unit tests**, **40 CI-helper tests**, actionlint,
  application-ID checks, debug/test APK builds, minified release APK/AAB and
  vital lint pass. SBOM freshness/six negative/hash/canonical checks pass with
  153 components and 76 declarations. No new R8 keep/dontwarn rules are added;
  the generated module's reflection name is retained in the release mapping.
- Lint has **zero errors, 1,016 warnings, one hint**, and 482 UnusedResources
  findings (previous block: 1,014 warnings / 482 unused), with no fixture findings
  and no new suppression/baseline. Those warnings remain separate follow-up work.
- Both APKs pass ELF/ZIP checks (24 native libraries, four ABIs); release JNI
  seeds and SDK 16 KB zipalign pass. Release keeps package
  `com.emogoth.android.phone.mimi`, version **230 / 7.0.1**, minSdk **23** and
  target **36**. APK changes from **41,393,389 to 41,413,250 bytes**; AAB from
  **31,128,081 to 31,154,281 bytes**. These are local artifact observations,
  not measured performance or memory improvements.
- Run 30 confirms the previous block. The new commit still requires its own
  complete remote six-job matrix, including actual 16 KB native execution;
  physical Android 10/32-bit and human-interaction release gates remain pending.

## Thirteenth cleanup: remove PercentLayout from filter rows

On 22 October 2026, replace the sole `androidx.percentlayout:percentlayout:1.0.0`
consumer, `filter_list_item.xml`, with a platform horizontal LinearLayout whose
two 0dp children have equal weights. The [Android reference](https://developer.android.com/reference/androidx/percentlayout/widget/PercentRelativeLayout)
marks PercentRelativeLayout deprecated; this simple two-column row does not need
another layout dependency. Keep minSdk 23 and compile/target 36.

Source/layout/manifest/R8/binding checks found no other PercentLayout consumers.
`FilterListAdapter` inflates the row by resource ID without casting its root;
`EditFiltersActivity` uses the existing holder and swipe-removal path. Preserve
the row's IDs, match-parent width, 48dp height, primary/secondary themed colours,
16sp bold name and 14sp expression. Use start/end padding and start gravity so
column order, padding and text alignment mirror in RTL rather than positioning
the expression absolutely to the name's right. Do not change wrapping/ellipsis,
stored filters, Room schemas, adapter mutations or dialog callbacks.

Three mandatory offline `FilterListLayoutTest` cases inflate and bind the real
adapter using in-memory models, without opening/saving dialogs or touching user
filters. They check LTR and RTL geometry in three themes, font scales 1.0/1.3,
320dp and 480dp-plus-one-pixel widths: equal columns within one pixel, exact
combined width, no child overflow, height/layout params, relative padding/start
alignment, colours, typography and full underlying long text. A third case checks
replacement/removal/rebinding, adapter notifications and attached click handlers.
It does not claim to exercise a real deletion gesture or a database-backed edit.

After correcting the fixture's missing RecyclerView LayoutManager, the old row
reproduced RTL child overflow; its in-memory adapter case passed. The initial
typography assertion also exposed expected XML dimension rounding (45.5 → 46px),
not an app font defect: allow nearest-pixel rounding without changing SP sizes.
All three corrected cases pass separately on the Edge 20 / Android 12 with the
weighted row. The full ordinary suite becomes **68 cases**, still with the same
three opt-in skips; the separate actual-16-KB job retains its six native cases.

SBOM/inventory change from **153/76 to 152 components / 75 declarations**.
Only PercentLayout and its node/root edge disappear; all retained component
records and graph edges outside that node remain identical, including Ruffle's
seven hashes and native media. No other dependency version, new dependency,
global exclusion or R8 keep/dontwarn rule is introduced. Run 32 for `2cfa199`
confirms all six jobs for the previous Glide cleanup; require a new complete
remote workflow for this block. Keep physical Android 10/32-bit and human
CAPTCHA/keyboard/browser acceptance pending, without starting CVE scanning,
the owner-gated 8.0 changelog or the final README rewrite.

### Thirteenth-block validation (22 October 2026)

- All three scoped filter-row cases pass, followed by the complete Edge 20 /
  Android 12 suite: **68 cases, 65 passed, three existing opt-in skips**, zero
  failures/errors. The six native cases and existing Glide cache/settings tests
  also pass. No real filters are edited or public posts made. The original
  phone stay-awake setting was restored to **0**, confirmed after validation.
- **102 app + eight chanlib unit tests**, **40 CI-helper tests**, actionlint,
  variant-ID checks, debug/test APK compilation, SBOM freshness/six negative/
  hash/canonical checks and minified release APK/AAB generation pass. The release
  mapping has no PercentLayout class records; no new R8 rule is needed.
- Lint passes with **zero errors, 1,013 warnings, one hint**, and 482
  UnusedResources findings (previous block: 1,016 warnings / 482 unused).
  No new fixture finding, suppression or baseline is introduced. Warnings remain
  separate follow-up work, not a list of safe deletions.
- Both APKs pass native ELF/ZIP checks (24 libraries, four ABIs); release JNI
  seeds and SDK 16 KB zipalign pass. Keep package
  `com.emogoth.android.phone.mimi`, version **230 / 7.0.1**, minSdk **23** and
  target **36**. APK changes from **41,413,250 to 41,412,351 bytes**; AAB from
  **31,154,281 to 31,150,815 bytes**. These small local size changes are not
  measured startup, memory or rendering performance improvements.
- Run 32 closes the preceding Glide block. This new cleanup still requires its
  own complete remote six-job matrix, including actual 16 KB native execution;
  the physical Android 10/32-bit and human-interaction gates remain pending.

## Fourteenth cleanup: More Info project identity and obsolete destinations

[Run 33 for `3dd4c9d`](https://github.com/jagumiel/mimi-reader/actions/runs/39852907707)
passes all six jobs, closing the thirteenth filter-row block, including actual
16 KB native runtime coverage. This new block still requires its own remote run.

The real More Info callers were checked before removal. Remove the rating row
and `MimiUtil.openMarketLink` together, including Amazon/market/Play fallbacks;
remove the feedback row and `sendLogcatMail` together, including the unused logcat
process/attachment path and legacy recipient/device-report construction. Their
two now-unused key resources are removed, but existing stored values are neither
read nor deleted. No app data or persisted setting is migrated in this block.

Reuse the existing `website_pref` key for the current project repository and
label it accordingly, not as the old website. Add the independent/free fork
status, explicit project Apache-2.0 link, and five credit/profile links using
`BrowserNavigator`'s plain ACTION_VIEW and missing/blocked-browser handling.
No browser is launched merely by opening settings. Profile links credit
Eli Connelly/@econnelly, the historical @MimiReader account, @Blatzar and
@jagumiel; @maestro12 is explicitly fork-network provenance, not original
authorship. See [identity, evidence and attribution limits](PROJECT.md).

The current repository has no Issues or Discussions enabled. Do not point
feedback/privacy at `/issues` or invent a new mailbox. Remove the old email
workflow and link to the repository without promising a support channel;
correct the bundled privacy policy accordingly. Navigation intents contain
only the requested URL, with no diagnostics, attachments or device metadata.
Preserve the original LICENSE and copyright, third-party notices and historical
changelog; do not claim all bundled software has the application's license.

Four mandatory `MoreInfoPrefsTest` cases exercise the actual screen's seven
link callbacks with intercepted outgoing intents, failure handling for absent
and blocked browsers, unchanged saved preferences, recreation/Back navigation,
and opening/restoring all three existing local document dialogs. They make no
real browser requests, send emails or publish messages. The first fixture build
needed explicit nullable-summary assertions. An initial locked/dozing phone run
could not transact fragments and also exposed a cleanup error for a monitor
that had not yet been registered; register it before entering the guarded test
body so cleanup cannot mask the original failure. No production lifecycle guard,
retry or skip is relaxed. After the user unlocked the phone, all four cases pass
separately on the Edge 20 / Android 12. The ordinary suite grows from 68 to **72
cases**, retaining the same three opt-in cases and separate six-case 16 KB job.

No Gradle dependency, component version, R8 rule, package/SDK/version setting,
SBOM snapshot or dependency inventory is changed. Keep 152 runtime components,
75 dependency declarations, Ruffle hashes and native artifacts. UI/layout
resources are changed deliberately, not solely because of their age. Legacy
`board_prefs.xml`/`posting_prefs.xml` still require a separate orphan audit before
deletion. The next bounded refactor is the duplicated local privacy/license/
changelog viewers: resource loading, view lifecycle, failures and link handling,
without writing an 8.0 changelog or changing legal notices.

The repeated whole-suite validation exposed an unrelated existing Glide fixture
race: `transformedImageCanBeReadFromDiskAfterMemoryIsCleared` received
`MEMORY_CACHE` instead of `RESOURCE_DISK_CACHE`. Preserve the failed report/log,
not just a successful rerun. Glide 5.0.7's
[`Engine`](https://github.com/bumptech/glide/blob/v5.0.7/library/src/main/java/com/bumptech/glide/load/engine/Engine.java#L279)
checks active/memory resources unless memory caching is bypassed, and a late
resource release can populate memory after the fixture clears it. Its
[`DecodeJob`](https://github.com/bumptech/glide/blob/v5.0.7/library/src/main/java/com/bumptech/glide/load/engine/DecodeJob.java#L488)
notifies before deferred encoding completes. Make the readback fixture bypass
memory with `skipMemoryCache(true)` and use `DiskCacheStrategy.RESOURCE`, retaining
`onlyRetrieveFromCache(true)`, the exact RESOURCE_DISK_CACHE assertion and the
original total ten-second write deadline. This also rules out a raw DATA-cache
decode. The warm-up and production caching stay unchanged: no accepting a memory
hit, source/network fallback, widened timeout, CI retry, or skipped test.

### Fourteenth-block validation (23 October 2026)

- All eight scoped Glide/More Info cases pass after the fixture correction.
  Final whole-suite acceptance on the Edge 20 / Android 12 passes: **72 cases,
  69 passed, three existing opt-in skips**, zero failures/errors. All four new
  screen cases and the corrected disk-cache assertion pass in that full run.
  No email, external profile browsing or public posting is performed. Restore
  and verify the phone's original stay-awake value **0** after the suite.
- **102 app + eight chanlib unit tests**, **40 CI-helper tests**, actionlint,
  application-variant checks, debug/test APKs, SBOM freshness/hash/canonical/six
  negative cases, minified release APK/AAB and vital lint pass. The final combined
  Gradle log is `build/more-info-verified.log`; the locked-phone and later cache
  fixture failures remain in their separate local logs, not accepted as passing.
- Lint has **zero errors, 1,012 warnings, one hint**, and **482 UnusedResources**
  findings. Remove the two newly orphaned rating/feedback labels as well as their
  preference keys; do not introduce a suppression, baseline or new fixture warning.
- Both APKs pass ELF/ZIP checks (24 libraries / four ABIs), release JNI seeds
  and SDK 16 KB zipalign. No market/email methods remain in release mapping.
  Package IDs, **230 / 7.0.1**, minSdk **23**, target **36**, 152 SBOM components,
  75 declarations, Ruffle, LICENSE and third-party/changelog documents are unchanged.
  Compared with the preceding local build, APK changes from **41,412,351 to
  41,425,051 bytes** and AAB from **31,150,815 to 31,143,978 bytes**. No startup,
  memory or rendering improvement is inferred from these sizes.
- Require this commit's own green six-job remote workflow, including actual
  16 KB execution, after pushing. Physical Android 10/32-bit and human CAPTCHA/
  keyboard/browser acceptance remain release gates. CVE scanning, owner-approved
  8.0 changelog preparation and the final README rewrite remain deferred.

## Fifteenth cleanup: shared offline documents and provider lifetime

Run 34 for `f5afe2f` passes five jobs; API 29 fails mandatory SWF playback after a
renderer exit. Review the complete artifact, not only the final Gradle error:
native fdsan/Binder descriptor failure precedes the SWF assertion, and the old
cache clear removes live `cache/WebView` infrastructure. The old HTML viewers
also keep WebViews alive beyond their views and duplicate resource-loading code.

Consolidate both packages' license/privacy classes in `LocalDocumentFragment`,
preserving original attribution, source factories/arguments and the live legacy
changelog entry point. Read UTF-8 raw documents with closed streams on existing
Rx I/O, dispose view-scoped work and stale generations, and detach/destroy the
WebView at view destruction. Intercept the prepared HTML at one local HTTPS URL
with CSP/no-store; deny every other resource request, scripting/storage/file/
content access, mixed content and automatic/subframe/unsafe-scheme navigation.
Only explicit HTTP(S) links go through existing browser error handling. Errors
offer Retry; renderer loss discards the affected instance and retry creates one.
Do not change any bundled legal/privacy/changelog document, LICENSE or README.

Protect `WebView` and suffix-specific directories alongside the existing live
HTTP cache, while preserving normal media/temp/Glide clearing. Extend its test
with owned markers, and add six real document lifecycle/rendering/navigation/
error/cache cases. Native text assertions found a genuine blank newer-provider
loadData implementation; replace it with the actual intercepted HTML response,
not a weaker assertion or enabled JavaScript. The original WebView also requires
a provider-neutral unchanged-URL assertion, not an assumed history URL.

The baseline with the exact pinned 128 provider reproduces native errors in
`libbinder_ndk` / `AParcel_writeParcelFileDescriptor`. Its test field trials
enable experimental MojoUseBinder, despite the declared disabled default.
For this CI-only API 29 provider, guard/verify a temporary command-line override
selecting the nonexperimental transport; confirm native startup output before
the full suite and restore the owned flag file even after failures. The pin/hash,
Ruffle, fdsan, sandbox, mandatory rendering assertions and newer-device providers
do not change. Never apply the override to the physical phone. Original-provider
capability and the new pinned-startup preflight remain distinct from full-suite
acceptance. The helper now uses the platform's correct Gradle launcher on Windows
or Linux; **54 CI-helper tests** cover safety, proof, failure propagation/cleanup.
Sources, the unsuitable/interrupted first local baseline and acceptance limits
are documented in [LOCAL_DOCUMENTS.md](LOCAL_DOCUMENTS.md).

No dependency or R8 rule changes are required: **152 components / 75 declarations**
and Ruffle/native records remain unchanged. The live legacy license class and
current preference viewers retain their names in release mapping; the otherwise
unused legacy privacy wrapper remains shrinkable, not a newly rooted release
class. Continue verified orphan-resource cleanup in a separate bounded block;
no files are deleted merely because they are old.

Final fifteenth-block acceptance on 23 October passes the actual CI helper end
to end on local API 29: original capability, pinned startup/native flags proof,
then **78 cases / 75 passed / three existing opt-in skips**, with flags removed.
The Edge 20 independently passes the same full suite. Also pass 110 app/library
unit tests, 54 CI-helper tests, lint without errors, SBOM checks, debug/test and
release builds, actionlint and static native/JNI/SDK alignment guards. Restore
phone stay-awake to 0 and owned emulator to 1, then shut down the emulator.
Detailed artifact sizes, evidence and acceptance limits are recorded in
[LOCAL_DOCUMENTS.md](LOCAL_DOCUMENTS.md#final-local-acceptance-23-october-2026).
Fresh remote six-job acceptance remains required.

## Sixteenth cleanup: confirmed orphan settings resources

[Run 35 for `f1b63df`](https://github.com/jagumiel/mimi-reader/actions/runs/39930834599)
passes all six jobs, accepting the fifteenth block's API 29 correction and local
viewers. Native startup flags proof, capability/preflight and the ordinary suite
pass; the dedicated native 16 KB job also succeeds. This does not accept later
commits automatically.

Audit both unused settings XML against source, live preference routing,
manifests, variants, R8 and dynamic lookups. `board_prefs.xml`/`posting_prefs.xml`
are identical abandoned More Info copies, not real board/posting functionality.
Remove them, the exclusive `about_pref`/`more_info_website` strings, the
definition-only `posting_pref`/`board_pref`/`more_info_version` strings, and
commented headers naming nonexistent PreferencesHC classes. Keep current shared
keys and all six live preference screens. No persisted values are removed.

Add actual six-category header click/recreation/Back coverage plus isolated
migration coverage preserving stored legacy/unknown values. The ordinary suite
becomes **80 cases**, retaining three opt-in skips and the separate six-case
native suite. Dependencies, 152 SBOM components / 75 declarations, R8, package
IDs, SDK/version settings, posting/CAPTCHA and legal documents are unchanged.
[Evidence, boundaries and next bounded review](SETTINGS_RESOURCE_AUDIT.md).

Final local acceptance passes **80 Edge 20 / Android 12 cases: 77 passed, three
existing opt-in skips, no failures/errors**, plus five scoped settings/migration
cases on API 29 without skips. Pass 110 app/library unit tests, 54 CI-helper
tests, SBOM checks, debug/test and minified release APK/AAB builds, actionlint
and static native/JNI/SDK guards. Lint has zero errors, 1,005 warnings and one
hint, with UnusedResources reduced from 482 to 475. Dump both actual APK resource
tables and check release mapping to confirm removed/retained resources and live
fragment names. Restore phone stay-awake to 0 and owned emulator to 1, then stop
the emulator. Detailed size comparisons and the initial Windows file-lock build
failure are retained in the audit; fresh six-job remote acceptance is pending.

## Seventeenth cleanup: unused settings shell and binding consumers

[Run 36 for `53379f4`](https://github.com/jagumiel/mimi-reader/actions/runs/39936759280)
passes all six jobs, including API 29 and native 16 KB, accepting the previous
resource cleanup. Audit the three settings-named layouts: keep the two used by
SettingsActivity/ThemeSettingsActivity, and remove only `mimi_settings_activity`
and its exclusive `settings_container` ID. Its automatically generated binding
has no consumers and is already removed by release R8; do not treat generated
output as an independent source caller or manually edit it.

Extend real settings navigation with binding/toolbar checks and add one case
covering detached binding inflation of both live layouts across three themes,
two widths and both layout directions, including color controls and unchanged
preferences. The ordinary suite becomes **81 cases**, retaining three opt-in
skips and six separate native cases. This does not execute the color editor's
interactive lifecycle or change production settings logic. Dependencies, SBOM,
R8, versions and legal content stay unchanged. See the
[settings layout audit](SETTINGS_RESOURCE_AUDIT.md#seventeenth-block-settings-layouts-and-generated-bindings).

Local seventeenth-block acceptance passes **81 Edge 20 / Android 12 cases:
78 passed, three existing opt-in skips, no failures/errors**, plus six scoped
API 29 cases without skips. Also pass 110 app/library unit tests, 54 CI-helper
tests, SBOM/variant checks, debug/test and minified release builds, actionlint
and static ELF/ZIP/JNI/SDK guards. Lint has zero errors, 1,004 warnings and one
hint, with 474 unused resources. APK resource tables and DEX confirm the old
layout/ID/binding are gone; retained resources and restored fragment names
remain. Restore phone stay-awake to 0; leave the emulator's observed boot value
3 unchanged and stop it. See the audit for sizes and acceptance limits. Fresh
remote six-job acceptance remains pending.

## Eighteenth cleanup: live color editor and swatches

Run 37 passes five jobs, but the native job stops at the adbd root handshake
before tests. Correct only bounded verified reconnection; retain actual page
size, root, compatibility and native-rendering assertions. CI-helper coverage
becomes 59 tests, without test retries or changed workflow gates.

Remove the color editor's redundant Rx scheduling/stop-time persistence, supply
valid mutable demo data, and retain slot/draft/target state using a DialogFragment
and results. Save only palette/OK edits. Disable navigation from fabricated
preview content and replace its dead sample URL. Fix ColorImageView's draw-time
drawable replacement loop and color-resource defaults. Keep the color picker,
layouts, stored keys, version settings, dependencies, SBOM and R8 rules unchanged.

Five mandatory cases bring ordinary coverage to **86 cases**, same three opt-in
skips and six separate native cases. Use a debug-only isolated-storage host for
editing and a separate real production read-only case; the fixture must not
ship in release. The first unsafe local fixture crashed before teardown; the
user explicitly authorized restoration of defaults, and all other preferences
were verified unchanged. Detailed failure/recovery evidence and limits are in
[COLOR_EDITOR.md](COLOR_EDITOR.md). Fresh six-job remote acceptance is required.

Final local acceptance on 24 October: **86 Edge 20 / Android 12 cases, 83 passed,
three existing opt-in skips, zero failures/errors**; five editor cases also pass
separately. The actual API 36 ps16k helper passes all six native cases with uid 0,
16384-byte pages and backcompat disabled. Pass 110 app/library unit tests,
59 CI-helper tests, SBOM/variant checks, actionlint, debug/test and minified
release builds and static ELF/ZIP/JNI/SDK guards. Lint has zero errors, 999
warnings and one hint; unused resources remain 474. Verify the fixture is
absent from release manifest/DEX and the restorable picker name is retained.
Default user colors remain restored; phone stay-awake returns to 0. Restore
emulator uid 2000, blank compatibility properties and stay-awake 1, then stop
it. Artifact sizes and failed local attempts remain documented in the review.

## Nineteenth cleanup: standalone chanlib dependency boundary

Implement inventory candidate D01 only. `chanlib` has no Core KTX or RxAndroid
consumer in production source, resources, manifest or generated outputs. Replace
those two exported declarations with explicit `api` dependencies on Annotation
**1.10.0** (`ArchiveConverter`'s public `@NonNull` contract) and OkHttp **4.12.0**
(`ChanConnector`, its builder and `ResponseAndPojo` expose HTTP types). These
versions are already selected by the application; the library no longer compiles
and tests against Retrofit's transitive OkHttp 3.14.9 minimum. Keep the app's
actual Core/Core KTX and RxAndroid dependencies, and do not change the remaining
Retrofit/Gson/RxJava/Jsoup/Kotlin exports as part of the separate D02 review.

The first compile against OkHttp 4.12.0 correctly fails: the old Kotlin
`MediaType.parse` entry point is error-deprecated, and mutable `cacheDir` cannot
be smart-cast for `Cache`. Replace only the constant MIME parsing with
`toMediaType()` and capture the directory in a local value before checking and
using it. Preserve method signatures, multipart keys/values/encoding, client
selection, cache size, endpoints, timeouts, redirects and retry policy. Do not
suppress the errors, rewrite posting, or raise either module's Android minimum:
`chanlib` stays 21 and the app 23, with compile/target 36.

`verifyDependencyBoundary`, required by the library's unit-test tasks and
`check`, verifies explicit API ownership and actual selections in debug/release
compile/runtime and debug unit runtime graphs. It requires the shared OkHttp
baseline and Annotation 1.10.0 and rejects Core/Core KTX or RxAndroid leaking
back into the standalone library. Existing CI already runs the library's unit
suite, so this gate needs no workflow change, force rule or global exclusion.

Add eleven offline unit cases: seven cover default/custom client identity,
timeouts, redirects/retries, read/post endpoints, cache directory/size, Gson/Rx
adapter availability, annotation-contract compilation and absence of app-only
framework adapters; four cover protected multipart conversion of string/int/long,
empty/Unicode text and unsupported/empty input. Lazy Rx service creation is never
subscribed; these tests do not send network requests or solve CAPTCHA.

Reviewed SBOM/inventory diff:

- **All 152 release component records are identical**, including versions,
  Package URLs, licenses, native AAR hashes and Ruffle's seven hashes. Only
  `chanlib`'s outgoing dependency set changes: Core KTX/RxAndroid are replaced by
  Annotation/OkHttp. Application root edges and all other component edges stay
  unchanged. Counts remain **152 components / 75 declarations**; lowering a
  count is not the goal of accurate dependency ownership.
- Only `chanlibUnitTestRuntimeClasspath` changes among the seven inventoried
  configurations: **23 to 20 entries**. Its old Core/arch/collection/lifecycle/
  versionedparcelable/RxAndroid paths disappear. Annotation becomes 1.10.0 with
  its JVM variant, OkHttp becomes 4.12.0 and Okio 3.6.0 with its JVM variant;
  Kotlin common/JDK forwarding artifacts follow that resolution. App runtime,
  app unit/instrumentation, desugaring, processor and build-tool graphs do not
  change. Do not claim that Core/RxAndroid are removed from the application.

Local validation: **121 unit tests** (102 app + 19 library, zero failures/errors),
59 CI-helper tests, debug/test APK and minified release APK/AAB builds pass.
App lint remains zero errors, 999 warnings and one hint, including 474 unused
resources; the standalone library lint report has no findings. Both APKs pass
static native checks (24 libraries/four ABIs); release also passes JNI seeds
and SDK 16 KB zipalign. Release APK is **41,423,143 bytes** (unchanged); AAB is
**31,141,845 bytes**, versus 31,141,796 at the baseline. Sizes are not performance
or security measurements.

Final `verifySbom testSbomValidation`, application-variant and standalone-boundary
checks pass. Three separate local init-script probes deliberately inject
RxAndroid, Core KTX and forced OkHttp 3.14.9; each is rejected by the expected
boundary assertion. These are local negative checks, not three new CI-helper
tests. The probe and logs remain under ignored `build/`, not production inputs;
the final normal invocation passes with no injected declarations or force rule.

The Edge 20 / Android 12 suite passes **86 distinct JUnit cases: 83 passed,
three existing opt-in skips, zero failures/errors** (1m41s). Gradle's console
prints an inflated 89 completion events; the XML reports 86 unique cases, not
89 tests. Restore and verify the original phone stay-awake value **0**. No public
message or live CAPTCHA test is performed. No fresh API 36 ps16k runtime run is
claimed for this non-native dependency-ownership change; unchanged native
artifact hashes and the static guards do not replace the next remote six-job
workflow. Physical Android 10/32-bit acceptance is still pending.

No UI/resource cleanup, native/WASM update, R8-rule, application identity,
version, legal, README or release-changelog change is included. Next review the
bounded R01 rating-resource closure; leave D02/D03 and the deferred CVE/GHSA
scanner separate. Physical Android 10/32-bit and human posting/browser checks
remain release gates; these local JVM/build checks do not replace them.

## Twentieth cleanup: remove the closed rating-resource subtree

Implement inventory candidate R01 only: remove three orphan rating-selector XML
files and `rating_button_selected`. All references to these four resources are
confined to that set; no live code, qualified resource, theme, manifest, binding or R8 consumer
is found. The obsolete rating preference/helper were removed together earlier.
Keep the unrelated rating colors, current More Info/credits/legal documents,
all three themes, preference keys, posting and CAPTCHA behavior. Deletions are
recoverable from Git; no user data is removed. See the
[bounded resource audit](SETTINGS_RESOURCE_AUDIT.md#twentieth-block-orphan-rating-selector-closure-r01)
for artifact checks, acceptance and limits.

No dependency, version, R8 or SBOM input changes: snapshots remain exactly
unchanged at **152 components / 75 declarations**. Reuse the existing settings/
More Info regression tests; do not add test-only lookups to production code or
claim new test cases. The next small candidate is R02's two reply-option arrays,
subject to its own consumer and posting/preference-preservation review.

Build/unit/SBOM/variant/boundary checks and 59 CI-helper tests pass. Lint has
zero errors, 995 warnings and one hint, with 470 unused resources (four fewer).
Actual debug/release resource tables omit the deleted names and retain live
settings/More Info/legal resources. The final unlocked Edge 20 / Android 12
suite passes 86 distinct cases: 83 passed, three existing opt-in skips, no
failures/errors. An earlier locked/sleeping attempt failed and is preserved,
not counted as passing. Both attempts restore phone stay-awake to 0. Details,
artifact sizes, static native checks and remote/physical acceptance limits are
in the resource audit; require the new commit's own six-job CI.

## Twenty-first cleanup: retire unused reply-option arrays

Run 40 for `62c24c3` passes all six jobs, accepting the preceding D01 library
boundary and R01 resource cleanup. Implement R02 only: remove `arrays.xml`, whose
two reply-option arrays have no production consumers. Live reply/quote callbacks,
posting fields, CAPTCHA handling, preference keys and migration schema do not
change. All other resource arrays, dependency declarations, SBOM snapshots,
versions, IDs, R8 rules and legal documents remain unchanged.

Extend the existing isolated legacy/unknown-preference preservation case with
synthetic keys matching the retired resource names and both former option
values; migration must preserve them and remain idempotent. This is not a claim
about historical key names, a migration of those keys or a new test case.
See the [R02 audit and validation](SETTINGS_RESOURCE_AUDIT.md#twenty-first-block-unused-reply-option-arrays-r02).
Next review D02's remaining declaration ownership/selection mismatches; retain
all direct and exported dependencies with real consumers. D03/R03 and CVE/GHSA
activation remain separately scoped, not included in this resource cleanup.

Local validation passes both build types/test APK, 121 app/library unit cases,
59 CI-helper cases, SBOM/variant/boundary guards and static native/JNI/ZIP checks.
Lint has zero errors, 993 warnings and one hint, with 468 unused resources (two
fewer). Four scoped device migration cases pass, including the expanded fixture.
The initial full UI preflight refuses to start behind keyguard and restores
stay-awake 0. After the owner unlocks the phone, a fresh full suite for `f741d0b`
passes in 1m31s: 86 distinct cases, 83 passed and three existing opt-in skips,
zero failures/errors; restore/verify stay-awake 0 again. This documentation-only
follow-up closes local UI acceptance, not the new commit's own six-job CI or
physical Android 10/32-bit gates. Do not reuse run 40 as remote validation.

## Twenty-second cleanup: explicit dependency ownership (D02)

Review actual main-source signatures, annotations, internal factory calls,
resources/manifests and R8 inputs instead of treating repeated declarations as
duplicate packaged copies. No production Java/Kotlin logic changes in this block.

| Declaration | Decision and consumer evidence |
| --- | --- |
| `:chanlib` -> Jsoup | Remove: no library source/signature/resource/reflection consumer. Keep the app's direct Jsoup declaration: `FourChanCommentParser` and `FourChanHtmlParser` parse actual board/post HTML. |
| Library Retrofit Gson converter / RxJava adapter | Move `api` to `implementation`: only constructed inside `ChanConnectorBuilder.initRetrofit`; no factory type appears in public/protected signatures. Both remain runtime dependencies. |
| Library Retrofit / OkHttp / RxJava | Keep `api`: connector methods, builder setters/return types and `ResponseAndPojo` expose their types. |
| Library Gson / Annotation / Kotlin stdlib | Keep `api`: model `@Expose`, public `@NonNull` contracts and Kotlin declarations remain visible. Do not strip metadata dependencies solely for lack of executable calls. |
| Application -> `:chanlib` | Use `implementation`: the application consumes the library but is not publishing a library API of its own. |
| Annotation version | Share `annotation_version = 1.10.0` between app, library and guard. The app previously requested 1.1.0 but already selected 1.10.0; no selected release upgrade is introduced. |
| Other direct app dependencies | Retain actual direct consumers of Retrofit, Gson, RxJava, Jsoup and Kotlin even where the library also uses them. Do not rely on accidental transitive exports. |

The standalone boundary task now checks the six reviewed public declarations
and presence of the two internal factory declarations. Its five real selected
graphs must also exclude Jsoup alongside the earlier app-only Core/RxAndroid
adapters. Extend the existing classpath-absence unit case to require that Jsoup
is not loadable in library tests. Existing transport tests still exercise Gson
conversion and lazy Rx adapter creation without network requests. Case counts
stay **121 app/library / 59 CI-helper / 86 ordinary / six native**.

Four local negative init-script probes reject exporting an internal converter,
hiding public Retrofit, dropping an internal Rx factory and reintroducing Jsoup.
These probes/logs live under ignored `build/`; they are not four new CI-helper
cases or production force/exclusion rules. The normal verification invocation
then passes without injected declarations.

Reviewed SBOM/inventory diff: **all 152 release component records, versions,
licenses, artifact hashes and Ruffle files remain identical**. Only the
library's outgoing Jsoup edge disappears; every other edge, including app root
edges, stays unchanged. The app still packages its actual Jsoup consumer. Direct
declarations become **74 instead of 75**, including scope/version corrections.
Only the inventoried standalone library unit graph changes, **20 -> 19**, by
removing Jsoup; all other selected application, test, desugaring, processor and
build-tool graphs remain identical. Annotation alignment and factory scope
changes do not disappear from the declarations inventory just because the
runtime component list stays the same.

Local acceptance on 24 October: **121 unit cases** (102 app + 19 library;
library cases run in the initial targeted invocation and are then up-to-date),
59 CI-helper cases, normal SBOM freshness/six malformed-input/hash tests,
variant and five-configuration boundary guards pass. Debug/test APKs, minified
release APK/AAB and both module lint checks pass; the combined build takes 38s.
App lint remains zero errors, 993 warnings and one hint, with 468 unused
resources; the library report has no findings. No suppressions or retry changes.

The full Edge 20 / Android 12 suite passes in **1m40s: 86 distinct XML/JUnit
cases, 83 passed, three existing opt-in skips, no failures/errors**. Do not count
89 console completion events as unique tests. Restore and verify original
stay-awake **0**; no public post or live CAPTCHA acceptance is performed.
Both APKs pass static ELF/ZIP checks (24 libraries/four ABIs); release also passes
JNI seed checks and SDK 16 KB zipalign. APK stays **41,422,903 bytes**; AAB is
**31,141,716 bytes**, versus 31,141,726. No performance gain or fresh native-16-KB
runtime result is inferred from unchanged native inputs or these sizes.

At this review, run 41 for the earlier `c75a558` is still in progress; run 40
accepts `62c24c3`, not D02. Record the new commit's own six-job remote acceptance
separately; local checks do not certify the emulator matrix or physical Android
10/32-bit release gates.

Next inspect D03's optional Room/Work/Preference KTX declarations against actual
resolved artifact contents and Kotlin extension ownership. Do not remove
runtime or Rx adapters, rewrite persistence/reactive APIs, or start the deferred
CVE/GHSA scan, 8.0 changelog or final README as part of D02. SDK minima, app IDs,
versions, R8 rules, native binaries, posting behavior and legal notices stay
unchanged; static ownership review is not an external published-library ABI test
or physical Android 10/32-bit acceptance. Require the new commit's own six-job CI.

## Remaining legacy-file review

The current follow-up is the [prioritized cleanup inventory](CLEANUP_INVENTORY.md),
reviewed after green six-job run 38 for `268fd5d`. It distinguishes the library's
standalone dependency graph from the application's selected versions, bounded
orphan resource closures and live legacy consumers. D01 (`chanlib` dependency
ownership/standalone network baseline) is implemented in the nineteenth block;
the twentieth removes the closed R01 resource set. Run 40 accepts both. The
twenty-first removes only R02's unused reply arrays. The twenty-second implements
D02 ownership and requests the already-selected Annotation baseline; next review
D03, not a bulk lint deletion or the deferred vulnerability scan.

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

The `openMarketLink` method was not an orphan when audited: the More Info rating
preference still called it. The fourteenth block removes both together after
reviewing the obsolete Amazon/Play destinations, not just the method in isolation.
The old email/logcat feedback path is also removed with its live caller.

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
4. Runs 28–30, 32 and 33 confirm all six jobs for the ninth through thirteenth
   browser, fast-scroll, Material, Glide and PercentLayout cleanups. The
   fourteenth updates More Info and removes obsolete market/email workflows.
   Run 34 passes five jobs but fails API 29; the fifteenth consolidates local
   viewers, protects live provider cache and corrects the pinned test runtime.
   Run 35 confirms all six jobs for the fifteenth block, including API 29 and
   native 16 KB. The sixteenth removes confirmed orphan settings XML/strings
   and tests all six live categories and legacy-value preservation; run 36
   confirms all six jobs. The seventeenth removes only the abandoned settings
   shell after reviewing generated bindings and protects both retained layouts
   with theme/geometry tests. The eighteenth modernizes the live color editor
   and verifies bounded adbd root reconnection. Run 38 for `268fd5d` confirms
   all six jobs for these changes. The remaining cleanup is now consolidated
   in [CLEANUP_INVENTORY.md](CLEANUP_INVENTORY.md). The nineteenth implements D01
   and the twentieth removes R01's four orphan resources. Run 40 for `62c24c3`
   confirms all six jobs for both. The twenty-first removes only R02's unused
   reply arrays and extends isolated value-preservation coverage; require its
   own workflow. The twenty-second implements D02's consumer-owned declarations
   without selected release-version changes; next review D03, without drafting
   the 8.0 changelog. Physical Android
   10/32-bit media and CAPTCHA acceptance remains
   required before release; wrapper versions are not native security provenance.
5. After posting recovery and the agreed cleanup/refactoring gates, add CVE/GHSA scanning of the
   cleaned SBOM to GitHub Actions, with scheduled runs, SARIF output and reviewed
   time-limited exceptions. Do not treat the scanner as the automatic next task
   while these cleanup blocks remain outstanding.

The 8.0 changelog still requires agreement with the project owner; the main
README remains the final documentation task.
