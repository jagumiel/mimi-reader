# Dependency inventory and cleanup plan

Baseline: 18 October 2026, following the SWF playback correction. Refreshed on
19 October after the bounded dependency and orphan-file cleanup blocks;
retained library versions are unchanged.

The canonical release SBOM has **154 top-level components**: 152 Maven components,
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
if it recurs in CI. The previous remote baseline is green in run 15; this
dependency-cleanup commit still needs its own Android 10/12/14/16 CI run.

## Findings from the resolved graph

| Area | Observed state | Follow-up and verification |
| --- | --- | --- |
| AppCompat | Declared 1.2.0, but Gradle selects **1.3.0-alpha02**, including appcompat-resources. | Trace the incoming dependency and align a deliberate supported version. Check themes, menus, dialogs, startup, system insets and R8. |
| RxJava | Declared 2.2.19, selected **2.2.21**; RxAndroid 2.1.1 and RxKotlin 2.4.0 remain. | Align declarations with resolution before considering broader replacement. Check cancellation, network errors and background work. |
| PDF | MimiReader AndroidPdfViewer 3.2.0-beta.2 pulls pdfium-android 1.9.0 and native binaries. | Review maintenance and native compatibility. Exercise opening, scrolling, rotation and release builds on all supported Android test targets. |
| UI libraries | Material 1.2.1, Glide 4.11.0, Preference 1.1.1, ConstraintLayout 2.0.1 and Browser 1.2.0 remain declared. | Modernize in small groups with UI, image/cache and navigation regression checks. Confirm Android 10/12 compatibility for each proposed replacement. |
| Kotlin | stdlib 2.3.21 coexists with jdk7/jdk8 compatibility artifacts 1.9.10 and kotlin-bom 1.8.0. | Inspect dependency paths and distinguish compatibility/constraint artifacts from duplicate executable code before removing anything. |
| Ruffle | Vendored 0.7.1 JS/WASM, MIT OR Apache-2.0. | Update its manifest, provenance and hashes together. Retain isolation, animation/pause tests and opt-in live SWF checks. |

These are inventory findings, not conclusions that a package has a known CVE.
No vulnerability database was queried in this phase.

## Candidates for incremental removal or consolidation

- The first three dependency candidates are resolved above. Continue reviewing
  indirect/generated usage rather than equating absent imports with safe removal.
- Legacy AndroidX umbrella dependencies: check which APIs and resources still
  require them after the ViewPager and background-work migrations.
- JUnit 4.12 in `chanlib` versus 4.13.2 in the app: align the library test setup.
- Empty `chanlib/libs` file-tree and unused version variables: remove obsolete
  build declarations after checking that no local binary workflow depends on them.

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
code. Both `free` and `paid` flavors use these shared main sources.

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

The Android 10/12/14/16 remote matrix still needs to run on this cleanup commit;
the earlier green baseline is not validation of these deletions.

### Remaining legacy-file review

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
have been checked. The two old `chanlib` test templates also need review; the
arithmetic example is not meaningful regression coverage.

## Sequence for subsequent iterations

1. Complete bounded removal of confirmed unused dependencies and orphaned code/
   resources in separate commits. Regenerate snapshots and run relevant tests,
   lint and a minified release build after each dependency group.
2. Add CVE/GHSA scanning of the cleaned SBOM to GitHub Actions, including
   scheduled execution, SARIF output and reviewed time-limited exceptions.
3. Align selected/declarative versions, then modernize UI/native dependencies
   with the Android 10/12/14/16 matrix and targeted device checks.
4. Compare APK/AAB size and startup behavior before and after cleanup.

The 8.0 changelog still requires agreement with the project owner; the main
README remains the final documentation task.
