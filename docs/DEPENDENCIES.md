# Dependency inventory and cleanup plan

Baseline: 18 October 2026, following the SWF playback correction. Refreshed on
20 October after the bounded cleanups and single-app consolidation. The fourth
block below aligns RxJava declarations with existing resolution and updates
only the library's test-only JUnit version; shipped library versions are unchanged.

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
if it recurs in CI. Run 15 was the initial remote baseline; the later
`eeb2e31` run confirmed by the user covers these changes cumulatively, as noted
under the third block. New cleanup changes still require their own matrix run.

## Findings from the resolved graph

| Area | Observed state | Follow-up and verification |
| --- | --- | --- |
| AppCompat | Declared 1.2.0, but Gradle selects **1.3.0-alpha02**, including appcompat-resources. | Trace the incoming dependency and align a deliberate supported version. Check themes, menus, dialogs, startup, system insets and R8. |
| RxJava | Both modules now declare the already-selected **2.2.21** through one shared version; RxAndroid 2.1.1 and RxKotlin 2.4.0 remain. | Declaration alignment is complete in the fourth block; broader reactive-code replacement remains a separate refactor. |
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
  measurements. The new block still needs its own remote matrix run.

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

1. Commit the validated fourth cleanup block, its dependency inventory and test
   coverage before starting another implementation block.
2. Restore user-solvable CAPTCHA and message posting as the next development
   task, ahead of the AndroidX group. Reading and participating are both core
   application functionality. Keep CAPTCHA resolution and submission under the
   user's control; use simulated posting in CI, and agree content/destination
   and obtain confirmation before a real public test post. See phase 5 of
   [the roadmap](../roadmap-2026.md) for scope and acceptance criteria.
3. Resume dependency modernization and responsibility-focused refactoring:
   trace and replace the unintended AppCompat/Fragment alpha selections as a
   bounded AndroidX UI group, with themes, dialogs, restoration/navigation,
   insets, R8 and the Android 10/12/14/16 matrix. Keep cleanup commits bounded,
   regenerate affected snapshots and run the relevant tests/lint/release builds.
4. Review PDF/native compatibility and remaining legacy adapters/resources;
   compare APK/AAB size and startup behavior with appropriate measurements.
5. After posting recovery and the agreed cleanup/refactoring gates, add CVE/GHSA scanning of the
   cleaned SBOM to GitHub Actions, with scheduled runs, SARIF output and reviewed
   time-limited exceptions. Do not treat the scanner as the automatic next task
   while these cleanup blocks remain outstanding.

The 8.0 changelog still requires agreement with the project owner; the main
README remains the final documentation task.
