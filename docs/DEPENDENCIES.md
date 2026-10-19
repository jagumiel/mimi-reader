# Dependency inventory and cleanup plan

Baseline: 18 October 2026, following the SWF playback correction. Refreshed on
19 October after the first bounded dependency cleanup; retained library versions
are unchanged.

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

## Legacy file review: pending a separate cleanup

A Git last-touch audit found 534 tracked files last changed in 2016 and 100 in
2019. Age alone is not a deletion criterion: `BoardConverter`, `PostConverter`,
`CodeSpan`, `DelegatingSocketFactory`, `BetterViewAnimator` and `RequestQueueUtil`
still have source or layout consumers, and license notices remain necessary.

Candidates with no references found in the source/resource search include
`AudioSettingsHost`, `MimiTab`, and the old `activity_video_ad`, `activity_youtube`
and `activity_gallery` layouts. The existing debug lint report contains 490
`UnusedResources` warnings. These findings require manual confirmation against
view binding, manifest entries, themes, variants and indirect resource usage;
they are not an automatic deletion list. Keep migration assets, font assets,
third-party licenses and CAPTCHA paths until their actual consumers and upgrade
requirements have been checked. The two old `chanlib` test templates also need
review; the arithmetic example is not meaningful regression coverage.

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
