# Dependency inventory and cleanup plan

Baseline: 18 October 2026, following the SWF playback correction. This iteration
adds visibility and verification; application library versions are unchanged.

The canonical release SBOM has **157 top-level components**: 155 Maven components,
the local `chanlib` module and Ruffle. Ruffle contains seven individually hashed
files. The inventory records **77 direct declarations** across the two modules;
repeated declarations and dependency constraints are not 77 distinct libraries.
See [SBOM generation and scope](SBOM.md) for commands and limitations.

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

- `androidx.room:room-guava`: no direct `ListenableFuture`/Guava Optional usage
  was found in application source. Confirm generated/indirect usage before removal.
- `commons-codec:commons-codec`: no direct imports were found in either module.
  Contrast its dependency paths and compile/test results before removing it.
- `kapt androidx.annotation:annotation`: inspect whether this annotation artifact
  provides any processor; preserve the normal annotation dependency as needed.
- Legacy AndroidX umbrella dependencies: check which APIs and resources still
  require them after the ViewPager and background-work migrations.
- JUnit 4.12 in `chanlib` versus 4.13.2 in the app: align the library test setup.
- Empty `chanlib/libs` file-tree and unused version variables: remove obsolete
  build declarations after checking that no local binary workflow depends on them.

Do not infer that a layout library is unused merely from Java/Kotlin imports:
`PercentRelativeLayout` remains in `filter_list_item.xml`, and AndroidX GridLayout
remains in `post_filter_view.xml`. Commons Lang `StringUtils` is also used in the
comment parser and post-option adapter.

## Sequence for subsequent iterations

1. Add CVE/GHSA scanning of the versioned SBOM to GitHub Actions, including
   scheduled execution, SARIF output and reviewed time-limited exceptions.
2. Remove confirmed unused dependencies in small commits; regenerate snapshots
   and run relevant tests, lint and a minified release build after each group.
3. Align selected/declarative versions, then modernize UI/native dependencies
   with the Android 10/12/14/16 matrix and targeted device checks.
4. Compare APK/AAB size and startup behavior before and after cleanup.

The 8.0 changelog still requires agreement with the project owner; the main
README remains the final documentation task.
