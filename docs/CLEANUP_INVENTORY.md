# Remaining cleanup inventory

Reviewed on 24 October 2026 against `268fd5d`. This is a prioritized review of
the remaining cleanup, not a removal list, a version-upgrade catalogue or a
vulnerability scan. The initial inventory was documentation-only; the subsequent
D01–D03, R01 and R02 implementations are recorded below and in the cleanup history.

## Accepted baseline and scope

[CI run 38](https://github.com/jagumiel/mimi-reader/actions/runs/40032230880)
accepts `268fd5dd1809504f3cdfd348f617087215a6f835`: all six jobs pass, including
ordinary instrumentation on API 29/31/34/36 and the separate actual-16-KB native
job. Its native log starts six tests and finishes successfully. This confirms
the seventeenth/eighteenth settings/color-editor changes and bounded adbd
reconnection; it is not acceptance of future commits.

The checked-in [dependency inventory](../sbom/dependency-inventory.json) records
75 direct declarations, including repeated module declarations, platforms,
AGP-provided view binding, tests, processors and desugaring. The
[release SBOM](../sbom/mimi-reader-release.cdx.json) has 152 components:
150 Maven components, local `chanlib` and Ruffle, whose seven upstream files
are individually hashed. These are selected inputs before R8, not 152
independent application features or proof that every class ships.

The release runtime configuration has 149 entries (148 Maven and `chanlib`);
desugaring adds two Maven entries and Ruffle is manually inventoried. Keep the separate configurations:
`chanlibUnitTestRuntimeClasspath` (23), `debugUnitTestRuntimeClasspath` (158),
instrumentation additions (45), `kaptRelease` (37) and root buildscript (190).
Counts from different configurations must not be added as unique libraries.

The existing local debug lint report for this baseline contains zero errors,
999 warnings and one hint, including 474 `UnusedResources` findings. These are
not 474 safely deletable files. The historical last-touch counts in
[DEPENDENCIES.md](DEPENDENCIES.md#remaining-legacy-file-review) likewise measure
age, not reachability. This review does not rerun or redefine that age audit.

## Priority 1: independently review the library dependency boundary

**D01 accepted by all six jobs in run 40 for `62c24c3`.**
The library now owns OkHttp 4.12.0 and Annotation 1.10.0 explicitly, without Core
KTX or RxAndroid exports. The app retains its live Core/RxAndroid declarations.
The standalone unit graph becomes 20 entries instead of the baseline 23; release
components and declaration totals remain 152 / 75. See the
[nineteenth cleanup](DEPENDENCIES.md#nineteenth-cleanup-standalone-chanlib-dependency-boundary)
for code adjustments, tests, exact graph scope and limits. R01 is implemented
below; D03's subsequent artifact review is recorded below. The table retains the original
evidence so the implementation can be compared against its accepted baseline.

**D02 implemented locally in the twenty-second block; remote acceptance pending.**
Remove the library's unused Jsoup declaration, keep its actual app consumer, make library factories
internal implementation details and retain real public API dependencies. Share
Annotation's already-selected 1.10.0 baseline and make the app's local-module
dependency implementation-scoped. Release components stay 152; declarations
become 74 and the standalone library unit graph 19. See the
[ownership audit](DEPENDENCIES.md#twenty-second-cleanup-explicit-dependency-ownership-d02).
The subsequent D03 review inspects actual artifact/extension consumers, not a
whole-Rx migration or automatic scanner activation.

**D03 implemented locally in the twenty-third block; remote acceptance pending.**
Remove direct Room/Work/Preference KTX declarations after inspecting resolved
AARs and actual extension owners. Work/Room bridges have no runtime classes;
Preference extensions are unused. WorkManager still requires empty Room KTX
2.8.5 transitively, so keep that node. Current totals are **150 components / 71
declarations**, with identical retained versions/hashes. Two mandatory offline
device cases raise ordinary coverage to 88. See the
[artifact audit](DEPENDENCIES.md#twenty-third-cleanup-optional-ktx-artifacts-and-actual-api-owners-d03).
R03's subsequent closed-resource review is recorded below; live themes remain.

| ID | Evidence at the accepted baseline | Bounded follow-up and acceptance |
| --- | --- | --- |
| D01 | `chanlib/build.gradle` exports Core KTX 1.3.0 and RxAndroid 2.1.1. The only `androidx` import found in its production source is `@NonNull` in `ArchiveConverter`; no production RxAndroid import was found. Its standalone unit graph selects Core/Core KTX 1.3.0, Annotation 1.1.0 and OkHttp 3.14.9, while the app selects Core/Core KTX 1.18.0, Annotation 1.10.0 and OkHttp 4.12.0. | Review all public signatures, generated code and app consumers before replacing unnecessary exports with actual annotation/network dependencies. Make the intended standalone OkHttp baseline explicit. Compare app **and library** graphs; run both unit suites, lint, debug/test and minified release builds, SBOM/variant guards and ordinary instrumentation. Do not align Core blindly: `chanlib` currently declares minSdk 21 while the app declares 23. |
| D02 | The app requests Annotation 1.1.0 but resolves 1.10.0. Retrofit/Gson/RxJava/Jsoup/Kotlin appear in both module declarations; this does not mean two versions are packaged. `ChanConnector` exposes `Single`, Retrofit `Response` and OkHttp response types. | Review ownership and `api` versus `implementation` per public type. Retain explicit direct app dependencies for actual app consumers. Do not remove exports or direct declarations just to lower the count; report graph edges and selected versions separately. |
| D03 | App `room-ktx`, `work-runtime-ktx` and `preference-ktx` are declared. `RefreshScheduler2` uses `workDataOf` / `OneTimeWorkRequestBuilder`, `RefreshWorker` extends `RxWorker`, and DAOs return Rx types. No production `withTransaction` or `suspend fun` match was found. | Inspect the resolved AAR/JAR contents and Kotlin extension ownership before proposing removal of optional/forwarding declarations. Absence of an import is insufficient, especially with wildcard imports. Preserve `room-rxjava2`, `work-rxjava2`, runtime libraries and persisted worker names. Test migrations, scheduled refresh and resumable downloads if the graph changes. |

Keep D02/D03 separate, not permission for a simultaneous network/persistence
rewrite. In particular, moving the application wholesale
from RxJava to coroutines is not a prerequisite for the 8.0 release.

## Priority 2: remove only bounded orphan resource closures

**R01 accepted by [run 40](https://github.com/jagumiel/mimi-reader/actions/runs/40038285565), all six jobs for `62c24c3`.**
Remove only the three rating-selector XML files and `rating_button_selected`
after confirming their closed internal edges and no outside consumer. Keep
other rating colors and all live themes/More Info behavior. See the
[resource audit](SETTINGS_RESOURCE_AUDIT.md#twentieth-block-orphan-rating-selector-closure-r01)
for searches, compiled-artifact checks and validation.

**R02 implemented locally in the twenty-first block; remote acceptance pending.**
Remove only the unreferenced `reply_entries` / `reply_values` definitions and
their otherwise-empty XML file. Extend the isolated migration-preservation
fixture without changing production posting, stored values or migration schema.
See the [reply-array audit](SETTINGS_RESOURCE_AUDIT.md#twenty-first-block-unused-reply-option-arrays-r02).
The D02/D03 reviews are recorded above. R03 is implemented in the twenty-fourth
block below; this is not approval for bulk removals or a requirement to rewrite
every live library.

**R03 implemented locally; this commit's remote acceptance remains required.**
Remove only three unused old card selectors, their six focused/pressed XMLs,
three textured action-bar wrappers and eight qualified tile PNGs: 20 files / 14
resource names. Preserve the three shared default card backgrounds, live catalog
color selectors, grid selectors, transparent/solid action-bar styles, all colors
and notices. One mandatory three-theme/width/direction/recreation case brings
ordinary instrumentation to 89 cases. See the [R03 reachability and validation](SETTINGS_RESOURCE_AUDIT.md#twenty-fourth-block-closed-legacy-theme-resource-subtrees-r03).
SBOM inputs remain 150 components / 71 declarations, without version/hash drift.
The live-library rows below are retained/deferred with actual consumers; they
are not unused dependencies awaiting blanket removal. This closes the agreed
D01–D03 / R01–R03 candidate set, not every lint warning or every legacy update.

| ID | Evidence and caution | Required proof before deletion |
| --- | --- | --- |
| R01 | Lint flags `app_rating_selector`, its normal/pressed selectors and `rating_button_selected`. Source search finds only the selector's internal edges and the color definition. The live market/rating preference was already removed in the fourteenth block. | First small resource candidate: inspect every source set, XML/style/manifest edge, binding and R8 input. If the four-node closure is unreferenced, remove it together, not just its root. Verify resource linking, three themes, More Info and release resource tables; retain legal notices. |
| R02 | `reply_entries` and `reply_values` appear only as definitions in `res/values/arrays.xml`; lint flags both. | Check posting/settings/preferences and any name-based loading. Removing display arrays must not delete legacy stored preference keys or alter CAPTCHA/submission behavior. Run settings and posting contract tests without sending public messages. |
| R03 | Lint flags textured action-bar assets and old card selectors. Textured XML references tile images internally. `styles_thememimilight.xml` still references `ab_transparent_thememimilight`; all three theme files reference their `basic_card_background_*`. | Audit complete style inheritance and qualified resources first. Do not bulk-delete the `ab_*` or `basic_card_*` families. Retain live card backgrounds; test light/dark/black, widths, RTL and recreation before removing an isolated obsolete subtree. |

There is no production `getIdentifier` / `Class.forName` match in the two main
Java/Kotlin source trees inspected here. That negative search is useful but
does not cover framework-generated bindings, manifest-instantiated components,
R8 restoration rules or third-party internals; inspect those independently.

## Priority 3: live legacy libraries and repository policy

These are retained consumers, not orphan declarations:

| Family | Concrete consumers | Decision / later review |
| --- | --- | --- |
| Commons Lang 3.11 | `PostOptionAdapter` uses `StringUtils.containsIgnoreCase`; `FourChanCommentParser` uses `isNumericSpace`. | Review separately with null, Unicode digit/space, case and quote-parsing contracts before an update or equivalent replacement. Do not assume standard Java helpers preserve these semantics. |
| RxJava / RxAndroid / RxKotlin | `ChanConnector` API, `ChanDataSource` (`combineLatest`, `zipWith`), UI schedulers, DAOs and `RefreshWorker`. | Keep. Any later lifecycle refactor must retain cancellation, error delivery, scheduling and Room/Work integration. The obsolete color-editor subscription was removed, not the application's Rx consumers. |
| PersistentCookieJar 1.0.1 | `HttpClientFactory` constructs the jar with a persistent cookie store. | Keep posting/login persistence. Review upstream maintenance/provenance separately; automated checks must not publish posts or solve CAPTCHA. |
| HSV color picker 3.0.1 | `ThemeColorPickerDialog` creates `ColorPickerView`. | Keep the just-tested live editor; test editing only in isolated fixture storage. |
| Subsampling image view 3.10.0 | `FitWidthSubsamplingScaleImageView` and gallery image pages. | Keep zoom/large-image support. Any replacement needs large-image, lifecycle and low-memory coverage. |
| Browser and AndroidX layout families | `BrowserNavigator`, settings/fragments and XML: CoordinatorLayout, DrawerLayout, SlidingPaneLayout, SwipeRefreshLayout, CardView and GridLayout. | Keep direct consumer declarations. Update a family only with a compatibility/consumer review; a small version number is not evidence of obsolescence. |
| Lifecycle / Room / Work / Kotlin serialization | `MimiApplication` uses `ProcessLifecycleOwner`; ViewModels/DAOs/workers and saved-state transitive dependencies remain live. | Keep public/generated dependencies and the serialization BOM. Do not remove constraints simply because application code has no direct serialization imports. |
| Media3 / Glide / PDF / native GIF | Gallery players, `MimiAppGlideModule`, `PdfPage` and GIF pages. | Keep the accepted SDK/minSdk and native compatibility baselines. Native alignment is not CVE clearance or embedded PDFium/GIFLib source provenance. |

Root repositories still include Google, Maven Central, JitPack and Sonatype
snapshots. No selected runtime coordinate in the snapshot uses a `-SNAPSHOT`
version, but that does not prove repository origin. Audit actual artifact and
metadata resolution before restricting/removing repositories; a warm Gradle
cache cannot prove a clean build succeeds without them. Retain JitPack until
the GitHub-coordinate dependencies' resolution has been established. Never
replace missing artifacts with unreviewed binaries or add global exclusions.

The Retrofit upgrade comment predates Kotlin 2.3.21. Review that rationale and
current upstream requirements in a future bounded network update; this
inventory neither selects a replacement version nor claims all versions are
current or free of vulnerabilities. Build-tool deprecations also need a
separate warning-origin audit before any Gradle-major migration.

## Protected inputs and release gates

- Keep the live old `BoardConverter`, `PostConverter`, `CodeSpan`,
  `DelegatingSocketFactory`, `BetterViewAnimator` and `RequestQueueUtil`:
  converters/models, parser, HTTP factory and board-list layout/source still
  reference them.
- Keep the icon font: `IconTextView` reads `R.string.font_path` and `Typefaces`
  loads that asset. Keep migration SQL/assets pending provenance review, Room
  schemas and upgrade fixtures; their age is not permission to remove them.
- Preserve Apache 2.0 attribution, bundled third-party licenses, project credits,
  local documents and CAPTCHA/Ruffle assets. Bundled JavaScript/WASM must remain
  accounted for by the manual component manifest and hashes.
- Keep Room/Glide annotation processors and desugaring. App source imports alone
  do not describe generated code or old-Android runtime requirements. Keep
  test/build dependencies separate from the shipped runtime analysis.
- Preserve application IDs, minSdk 23, target/compile 36, persisted values,
  fragment/worker restoration and release R8/JNI rules. A changed native input
  requires static ELF/ZIP checks and the actual-16-KB six-case runtime gate.
- Physical Android 10/32-bit media and human CAPTCHA/keyboard/browser acceptance
  remain pending. Green emulators do not replace them. No further public posts
  are authorized by this inventory.

## Validation and completion criteria

The initial documentation-only block ran `verifySbom testSbomValidation` and
`:mimi-app:verifyApplicationVariants`: all passed; six malformed SBOMs were
rejected, the asset hash negative check passed and canonical output was stable.
Generated reports equalled the tracked snapshots. Source/resource searches
substantiate the specific candidates above, not a blanket dead-code
certification. Markdown links and `git diff --check` were checked before commit.
That initial block changed no APK and claimed no new device/lint suite. The
subsequent D01 implementation and its new build/device validation are recorded
separately in the nineteenth cleanup linked above.

For each later implementation, record the exact candidate, consumers, changes,
graph/resource diff, tests and remaining limits in the cleanup history. Require
that commit's own complete six-job CI. D01–D03/R01–R03 are now implemented with
the bounded evidence linked above. Keep the live-library rows and repository
origin audit as separately scoped reviews, not an unlimited requirement to
rewrite every component before security work. No clean-cache repository-origin
proof, physical-device release acceptance or complete vulnerability clearance
is inferred from this inventory.

After the agreed bounded cleanup and explicit disposition of remaining
candidates, [CVE/GHSA scanning](VULNERABILITY_SCANNING.md) is now implemented over
the existing SBOM with scheduled runs, reviewed expiring exceptions and SARIF.
Its first real scan reports a MEDIUM Commons Lang CVE without waiving it; next
review that live consumer/update with null/Unicode/case/parser contracts, not
another mass dependency rewrite. Native/WASM provenance
gaps must remain visible even if a scanner reports no findings. Consult the
owner before defining the 8.0 changelog from fork baseline 7.0.1; leave the full
README rewrite until the end.
