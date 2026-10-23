# Android CI and Flash playback

The workflow builds debug/release artifacts, validates the release SBOM, and
runs instrumentation on Android 10, 12, 14 and 16 (API 29/31/34/36). A passing
build job alone is not a passing workflow: every instrumentation job must pass.

## Run 25: download cleanup ordering (21 October 2026)

[Run 25](https://github.com/jagumiel/mimi-reader/actions/runs/39573428637)
failed `Build and verify debug`: 93 of 94 app unit tests passed, but
`DownloadToFileTest.rejectsSuccessfulResponsesWhoseSizeDoesNotMatchTheCatalog`
failed at line 90 while asserting that `truncated.swf.part` had been removed.
The downloaded HTML report confirms this was the file-cleanup assertion, not
the expected size-mismatch error. The preceding runs 22, 23 and 24 passed.
The release/native alignment checks and all emulator jobs were skipped; this
run supplies **no** 16 KB runtime result.

`downloadToFile` notified `onError` before its `finally` deleted the partial.
The subscriber (or an immediate retry) could therefore run before cleanup.
Read failures also bypassed the manual sink close. Responses and file sinks
now use scoped closing, incomplete partials are removed, and only then is a
terminal event delivered. HTTP/size validation and final rename remain
mandatory; no test retries or suppressions have been added to CI.

The regression assertions capture partial-file state **inside** error
notification, not after a scheduling-dependent wait. The catalog mismatch,
mid-body disconnection and immediate subscriber-retry cases all failed locally
against the old implementation before the correction. The two added cases
bring the app unit suite to 96 (104 with chanlib); download tests have bounded
10-second waits. CI report artifacts now retain JUnit XML for both modules as
well as HTML, even on failure. A fresh remote six-job run is still required,
including the actual 16 KB emulator; local builds do not replace it.

Local post-fix verification passed: 96 app and 8 chanlib unit tests, 40 CI helper
tests, application-ID verification, debug lint (zero errors, 1,012 existing
warnings and one hint), debug/app-test APK compilation, SBOM freshness and six
negative validation cases, and minified release APK/AAB builds. Both APKs
passed native alignment checks (24 libraries, four ABIs); release also passed
the JNI seed contract and SDK `zipalign -c -P 16 4`. Actionlint accepted the
workflow. No device was connected in `adb devices`, so no new physical-device
or emulator instrumentation pass is claimed for this fix.

## Run 26: native PDF fixture isolation and viewport positioning

[Run 26](https://github.com/jagumiel/mimi-reader/actions/runs/39627059043)
confirms the run-25 correction: build/unit/lint/SBOM, minified release, native
alignment and JNI checks passed. The four regular emulator jobs each failed
only `pdfRendersPagesZoomsAndReleasesOnPauseAndDetach` at line 83, waiting for
the blue second page (49 cases: 45 passed, three expected skips, one failure).
API 29's separate original-provider capability check also passed.

The dedicated ps16k job reached `PAGE_SIZE=16384` with backcompat disabled;
its `device.txt` and per-case page-size assertions confirm a real 16 KB run.
Four of six native cases passed. Besides the same blue-page failure, its first
case, `pdfViewportSurvivesActivityAndModelRecreation`, failed before initial
render: logcat shows the startup cache cleanup followed by a missing fixture
and `FileNotFoundException`, not a linker error.

Both failures were in the test setup:

- The fixture called `jumpTo(1)` **before** `zoomTo(1.5f)`. In the
  [viewer implementation](https://github.com/oothp/AndroidPdfViewer/blob/master/android-pdf-viewer/src/main/java/com/github/barteksc/pdfviewer/PDFView.java),
  `zoomTo` only changes a field; it does not recalculate offsets. The viewport
  then straddled both pages, and its centre landed on their shared boundary.
  Apply zoom first, then `jumpTo` to reposition and request tiles at that scale.
- Files were created in `cacheDir` before launching `TabsActivity`, whose
  first-install/upgrade path clears that cache. Every native fixture now lives
  in its own JUnit `TemporaryFolder` beneath `filesDir`, checked before each
  case and cleaned by the rule even after failure. No user files or app cache
  policy are changed to accommodate the tests.

Pixel checks now sample three interior positions, together with the expected
page and zoom, both before and after pause/resume, detach/reselection and
activity/model recreation. This is stricter than the old single centre pixel
or readiness-only checks. Timeout diagnostics include the stage, file existence,
attachment, readiness, recycled state, page/zoom/offsets, size, sample colours
and visible error state. The deadline remains 15 seconds; no retry, skip or
16 KB compatibility bypass was added. Only tests and documentation change.

The three-position assertion reproduced the old positioning failure on the
Edge 20 / Android 12 before fixing the call order. All six corrected native
cases then passed separately on that phone. Full remote six-job acceptance,
including all six cases on the actual ps16k image, remains required.

Post-fix local validation also passed the complete 49-case Edge 20 suite:
46 passed, three existing opt-in skips, zero failures/errors. This includes
the unchanged cache-cleaner test preserving `filesDir` data and all six native
cases with the stronger pixel checks. Also passed: 96 app + 8 library unit
tests, 40 CI helper tests, debug lint (zero errors), variant IDs, SBOM freshness
and six negative cases, debug/release APK and release AAB builds, ELF/ZIP checks,
release JNI seeds, SDK zipalign and actionlint. The phone's original stay-awake
setting (0) was restored. This already-installed Android 12 phone is not a local
cold-install or 16 KB emulator validation; those remote gates remain pending.

## Run 27 confirmed; ninth cleanup browser navigation

[Run 27 for `fed11d2`](https://github.com/jagumiel/mimi-reader/actions/runs/39669781921)
passed **all six jobs**. Besides build/unit/lint/SBOM/release and the ordinary
Android 10/12/14/16 matrix, the native artifact contains `PAGE_SIZE=16384`,
`backcompat=false` and JUnit results for six tests with zero failures/errors/skips.
This closes the fixture corrections and the eighth block's actual 16 KB runtime
gate. It does not validate a physical 32-bit Android 10 phone or future commits.

The ninth cleanup removes Novoda's wrapper and uses AndroidX Browser directly.
Five new mandatory offline `BrowserNavigatorTest` cases cover intent extras,
toolbar colour, LinkSpan preference dispatch/styling, non-web/external modes,
missing/blocked handlers and Activity/context-wrapper flags. All outgoing intents
are captured: no browser UI or public URL is opened, and no stored preference is
changed. The suite becomes **54 cases**, with the same three opt-in skips; the
separate actual-16-KB job retains its six native tests. This new commit still
requires its own complete remote workflow.

Local ninth-block acceptance passed on the Edge 20 / Android 12: five isolated
navigation cases and the complete 54-case suite (51 passed, three existing
opt-in skips, no failures/errors). Also passed 104 app/library unit tests,
40 CI-helper tests, lint without errors, variant IDs, SBOM freshness/negative
checks, debug/test APK and minified release APK/AAB builds, native ELF/ZIP/JNI
guards, SDK zipalign and actionlint. The original phone stay-awake setting (0)
was restored. These results do not pre-approve this new commit's remote matrix.

## Run 28 confirmed; tenth cleanup thread fast-scrolling

[Run 28 for `dc17d6d`](https://github.com/jagumiel/mimi-reader/actions/runs/39823710636)
passed all six jobs, confirming the ninth-block browser replacement on the
ordinary Android 10/12/14/16 matrix plus actual native 16 KB execution.

The tenth cleanup swaps the archived Quiph wrapper for AndroidFastScroll 1.3.0
with an index-aware helper for variable-height posts. Four mandatory offline
`ThreadFastScrollTest` cases cover production-layout MotionEvent dispatch,
first/middle/last posts, counter bounds, themes/widths/LTR-RTL, scrollbar/refresh
gesture separation, active-drag cancellation when data empties, short/replaced
adapters and idempotent reattachment. Six additional pure unit tests cover
numeric boundaries/overflow. The instrumented suite becomes **58 cases**, with
the same three opt-in skips; app/library unit coverage becomes **102 + 8**.
CI-helper coverage remains 40 cases and the ps16k job still runs six native
cases. No skip, retry, timeout increase or relaxed check is introduced.

Initial local runs exposed an emptied-list drag that was not cancelled, then
an attach/observe timing issue that cancelled a fresh gesture. The integration
now observes before interaction and cancels mutations before the library's
disabled-scrollbar path. All four new instrumented cases passed separately on
the Edge 20 / Android 12. The full remote six-job workflow for this new cleanup
remains pending; run 28 does not pre-approve its successor.

Final local tenth-block checks passed: 102 app + eight library unit tests,
40 CI-helper tests, four isolated fast-scroll device cases and the full
58-case Edge 20 / Android 12 suite (55 passed, three existing opt-in skips,
no failures/errors). Debug/test and minified release APK/AAB builds, lint
without errors, variant IDs, SBOM freshness/negative tests, native ELF/ZIP/JNI
checks, SDK 16 KB zipalign and actionlint passed. Restored phone stay-awake to 0.
No remote-success claim is made for the new commit until its six jobs pass.

## Run 29 confirmed; eleventh cleanup Material and ConstraintLayout

[Run 29 for `db43b0b`](https://github.com/jagumiel/mimi-reader/actions/runs/39830199566)
completed successfully, confirming the tenth cleanup's six-job workflow.
Five job-detail records report success directly. API 29's detail endpoint still
returned `in_progress` at review time, but its completed downloadable log confirms
58 cases, three expected skips, zero failures, `BUILD SUCCESSFUL` and successful
report upload. This is not a failed or still-running test hidden by a skip.

The eleventh cleanup updates Material Components 1.2.1 → 1.14.0 and its required
ConstraintLayout 2.0.1 → 2.2.1 baseline, keeping minSdk 23 and the existing themes.
Three mandatory `MaterialComponentsCompatibilityTest` cases exercise production
tabs/chrome/FAB, dialog and Snackbar callbacks, and gallery-container bounds.
They use synthetic content, not public posting or persisted preference changes.
All three pass with the old versions before migrating. The ordinary matrix
grows to **61 cases**, with the same three existing opt-in skips; the ps16k job
retains its six native cases. No workflow bypass, retry, timeout increase or
weakened assertion is added. The new commit needs its own complete remote run.

Local eleventh-block validation passed: 102 app + eight library unit tests,
40 CI-helper tests, lint with zero errors, SBOM/negative checks, variant IDs,
debug/test APK and minified release APK/AAB builds. The full Edge 20 / Android 12
suite has **61 cases: 58 passed, three expected skips, zero failures/errors**.
Native ELF/ZIP/JNI checks, SDK 16 KB zipalign and actionlint passed. The original
phone stay-awake setting (0) was restored. These results do not pre-approve the
new commit's remote API matrix or actual 16 KB runtime.

## Run 30 confirmed; twelfth cleanup Glide and image/cache contracts

[Run 30 for `cca6fa6`](https://github.com/jagumiel/mimi-reader/actions/runs/39835334241)
passed all six jobs: build/unit/lint/SBOM/release, Android 10/12/14/16 and native
media on actual 16 KB pages. This confirms the eleventh-block Material migration,
not the new Glide changes.

The twelfth block updates Glide/integration/compiler to 5.0.7. The published
5.0.9 AAR requires compile SDK 37; keep the tested SDK 36 baseline rather than
bypassing its metadata check. Four mandatory offline `GlideCompatibilityTest`
cases cover registered OkHttp integration and intercepted image/404 responses,
local thumbnail recycling and placeholders, coordinated fallback callbacks, and
cache-only disk decoding after clearing memory. They do not replace the global
registry, alter production TLS/cleartext policy or access a live CDN. They pass
separately on the Edge 20 / Android 12 after correcting the fallback helper and
the fixture's assumption that clearing a request always sets a null drawable.

The ordinary matrix grows to **65 cases**, with the same three opt-in skips.
The ps16k job retains six native cases. No workflow retry, skip/timeout relaxation or new
test dependency is introduced; require this commit's own complete six-job run.

The initial local full run had a phone sleep/state-saved failure in the unchanged
settings case and a premature assertion in the new disk-cache fixture. Android's
event log confirms the sleep transition; Glide's DecodeJob delivers the resource
before deferred encoding finishes. Wake the phone and poll cache-only readiness
within the same total ten seconds, still requiring RESOURCE_DISK_CACHE and the
correct pixels. The corrected four Glide cases plus three existing UI cases pass
together (seven cases, no skips); no settings production logic is weakened.

Final local twelfth-block acceptance passes: **65 Edge 20 / Android 12 cases,
62 passed, three expected opt-in skips, zero failures/errors**, including the
corrected cache case and unchanged settings restoration. Also pass 102 app +
eight library unit tests, 40 CI-helper tests, lint with zero errors, SBOM freshness/
negative checks, variant IDs, debug/test and minified release APK/AAB builds,
ELF/ZIP/JNI guards, SDK 16 KB zipalign and actionlint. The original phone
stay-awake setting (0) is restored. Remote acceptance for this new commit remains
pending; the green run 30 does not pre-approve its successor.

## Run 32 confirmed; thirteenth cleanup filter-row PercentLayout removal

[Run 32 for `2cfa199`](https://github.com/jagumiel/mimi-reader/actions/runs/39841661911)
passed all six jobs, confirming the Glide cleanup's build/unit/lint/SBOM/release,
ordinary Android 10/12/14/16 matrix and actual native 16 KB execution.

The thirteenth block removes PercentLayout after replacing its only row layout
with platform equal weights. Three mandatory offline `FilterListLayoutTest`
cases cover LTR/RTL bounds, relative padding/alignment, three themes, two widths
(including an odd physical pixel), two font scales, colours/typography/long text
and in-memory adapter replacement/removal/rebinding. No user filter is changed,
dialog opened, or production adapter callback replaced by a test stub.
All three cases pass separately on the Edge 20 / Android 12.

The ordinary matrix becomes **68 cases**, with three unchanged opt-in skips;
ps16k remains six native cases. Snapshots contain 152 components / 75 declarations,
with only PercentLayout removed. No skip/retry/timeout relaxation is added; this
commit still needs its own complete six-job remote acceptance.

Final local thirteenth-block acceptance passes: **68 Edge 20 / Android 12 cases,
65 passed, three expected opt-in skips, zero failures/errors**. Also pass 102 app
and eight library unit tests, 40 CI-helper tests, lint with zero errors, variant
IDs, SBOM freshness/negative checks, debug/test and minified release APK/AAB
builds, native ELF/ZIP/JNI guards, SDK 16 KB zipalign and actionlint. The release
mapping contains no PercentLayout classes. The original phone stay-awake setting
(0) is restored. The new commit's complete remote workflow remains pending.

## Run 33 confirmed; fourteenth cleanup More Info link review

[Run 33 for `3dd4c9d`](https://github.com/jagumiel/mimi-reader/actions/runs/39852907707)
passes all six jobs, closing the filter-row PercentLayout cleanup. More Info's
new four-case offline instrumentation is discovered by the existing ordinary
matrix: **72 cases**, three unchanged opt-in skips; the dedicated actual-16-KB
job remains six cases. No workflow skips, retries or timeouts are changed.

`MoreInfoPrefsTest` intercepts every outgoing project/license/profile intent;
the matrix does not email a maintainer, contact a real support channel or open
a real browser for these cases. It verifies destinations/identity/no diagnostic
payload, absent/blocked handlers, saved preferences, recreation/Back and the
existing three local notice dialogs. All four cases passed separately on the
unlocked Edge 20 after correcting the fixture's monitor cleanup; the initial
locked/dozing attempt is not accepted. The current repository has no Issues or
Discussions enabled; repository navigation does not promise a support service.

Dependencies and SBOM snapshots remain unchanged at 152 components and 75
declarations. Require all six jobs again for this commit, including native 16 KB;
local Android 12 does not replace the remote matrix or pending physical/human
release checks. CVE scanning and the owner-gated changelog remain deferred.

A repeated full physical run exposed the existing Glide disk-cache fixture
receiving MEMORY_CACHE after clearMemory. Its readback now explicitly bypasses
memory and restricts disk retrieval to RESOURCE, retaining cache-only loading,
the exact resource-disk-source assertion and ten-second total deadline. This is
a fixture correction, not a production cache change, relaxed assertion, retry
or new skip. Retain the failed local validation log for diagnosis.

Final local acceptance passes on 23 October: eight scoped Glide/More Info cases,
followed by **72 full Edge 20 / Android 12 cases: 69 passed, three existing skips,
zero failures/errors**. Also pass 102 app + eight library unit tests, 40 CI-helper
tests, actionlint, lint without errors, variant IDs, SBOM checks, minified release
APK/AAB and ELF/ZIP/JNI/SDK zipalign guards. Stay-awake is restored and verified
as 0. The new commit's six-job remote acceptance remains pending until push.

## Run 37: adbd root transport closes before native tests

[Run 37](https://github.com/jagumiel/mimi-reader/actions/runs/40026744999) passes
five jobs; the 16 KB job fails at `adb root` with `unable to connect for root:
closed`, before Gradle or any native case. Preserve that distinction: no PDF/GIF
assertion failed and the previous layout cleanup is not fully accepted yet.

Use bounded reconnection/uid-0/boot verification after the known closed/offline
root-request ambiguity, recheck identity/API/page size, then retain strict
compatibility-property and native assertions. Root denial, nonroot timeout and
changed pages fail; no test retry, host ADB restart or physical-device write.
Evidence includes `adbd_uid=0`; five new helper regressions bring coverage to
59 tests. Workflow YAML and its six-job gates stay unchanged.

The same block refactors the live color editor and swatch drawing, adding five
mandatory cases (ordinary **86**, same three opt-in skips; native six). See
[diagnosis, recovery, isolation and acceptance limits](COLOR_EDITOR.md).
Require a fresh full remote workflow after push.

Local verification on 24 October passes the actual updated helper on API 36
ps16k: six native cases, no skips/failures/errors, with page size 16384, uid 0
and compatibility disabled. This is not only a simulated transport regression.
The Edge 20 full suite passes 86 JUnit cases (83 passed, three existing skips).
Also pass 110 app/library unit tests, 59 CI-helper tests, lint without errors,
SBOM/variant checks, debug/test and minified release builds, actionlint and
static native/JNI/SDK guards. The debug-only fixture is non-exported and absent
from release manifest/DEX. Restore phone settings, restore emulator uid/properties
and stop it. The new remote six-job run remains required.

## Run 36 confirmed; seventeenth cleanup of an unused settings layout

[Run 36 for `53379f4`](https://github.com/jagumiel/mimi-reader/actions/runs/39936759280)
passes all six jobs, including API 29 and the dedicated native 16 KB job. The
next bounded cleanup removes one unused settings shell, not either of the two
live settings layouts. Confirm generated binding consumers before removal;
release already shrinks that unused binding.

Extend actual settings navigation with binding/toolbar assertions and add one
mandatory detached-binding case across themes, widths and layout directions.
The ordinary suite becomes **81 cases**, still with three opt-in skips and
six separate native cases. No workflow/dependency/R8/production activity logic
changes. See [the audit](SETTINGS_RESOURCE_AUDIT.md#seventeenth-block-settings-layouts-and-generated-bindings).
This block still requires its own complete remote workflow after push.

Local acceptance passes 110 app/library unit tests, 54 CI-helper tests, lint
without errors (one fewer unused-resource finding), SBOM/variant checks,
debug/test and minified release builds, actionlint and static native guards.
The Edge 20 full suite has 81 distinct JUnit cases (78 passed, three existing
skips); API 29 passes six scoped settings/migration cases without skips. APK
resource/DEX checks confirm removal, and device settings are left restored.
Details and limits are recorded in the audit; this is not remote acceptance.

## Run 35 confirmed; sixteenth cleanup of orphan settings resources

[Run 35 for `f1b63df`](https://github.com/jagumiel/mimi-reader/actions/runs/39930834599)
passes **all six jobs**, accepting the preceding document/cache/runtime cleanup.
API 29's native startup log confirms `MojoUseBinder` is disabled before its
78-case suite; original-provider capability and pinned preflight also pass.
The dedicated native 16 KB job succeeds, not just the static alignment checks.

The next bounded change removes two verified orphan settings XML copies, five
exclusive/definition-only strings and commented nonexistent header examples.
Two mandatory cases cover real navigation/recreation/Back for all six live
categories and isolated migration of legacy/unknown stored values. The ordinary
suite becomes **80 cases**, with the same three opt-in skips; the dedicated
native suite remains six. No CI gates, dependencies or production settings
logic change. See [audit evidence and boundaries](SETTINGS_RESOURCE_AUDIT.md).
This cleanup still needs its own green six-job run after pushing.

Local sixteenth-block checks pass: 110 app/library unit tests, 54 CI-helper
tests, lint with zero errors (seven fewer unused-resource warnings), SBOM,
debug/test and minified release builds, actionlint and static native guards.
The Edge 20 full suite passes 80 JUnit cases (77 passed, three existing skips),
and API 29 passes five scoped settings/migration cases without skips. Restore
device settings after testing. Detailed evidence, APK resource-table/R8 checks
and the initial Windows file-lock build failure are in the audit above.

## Run 34: API 29 native Binder failure and local document cleanup

[Run 34 for `f5afe2f`](https://github.com/jagumiel/mimi-reader/actions/runs/39922242383)
passes build/lint/unit/SBOM/release, API 31/34/36 and actual 16 KB native tests.
Only API 29 fails: the mandatory bundled SWF test receives a renderer exit. Its
artifact includes native descriptor-ownership (`fdsan`) failure during document
recreation, then missing `cache/WebView` paths after the cache test. This is not
a reason to mark all six jobs green, retry the failing assertion or skip SWF.

The fifteenth block fixes live WebView-profile deletion and consolidates the
four local document source entry points with view-scoped I/O, safe rendering,
error/retry and WebView teardown. Six new mandatory tests bring the ordinary
suite to **78 cases**, with the same three opt-in skips. Native ps16k remains six.
Documents, Ruffle, provider version/hash and SBOM components are unchanged.

For the historical API 29 test provider only, explicitly disable its experimental
`MojoUseBinder` transport, which its test-variations configuration enables despite
the feature's normal default. A local unmodified baseline reproduced the native
Binder descriptor failure; the override is checked against native startup logs,
not only the presence of a command-line file. Keep fdsan, sandboxing, rendering
and all assertions enabled. No app/phone runtime flags are changed.

The helper refuses physical serials, non-debuggable/non-29 images, wrong active
providers and pre-existing developer flags. It verifies its owned file, restarts
only the test app, and attempts cleanup even when app stopping fails. The original
provider's capability case still runs before pinning; then one existing
startup-error case proves native flags before the complete ordinary suite.
Preserve that extra preflight's JUnit/logcat in
`build/reports/legacy-webview/pinned-runtime`, covered by the existing artifact
upload. No failing test retry or relaxed timeout is introduced. **54 CI-helper
tests** cover the new environment/proof/cleanup guards and Windows/Linux wrapper
selection. Newer API jobs keep their image provider and never enter this runtime.

Local acceptance on 23 October: **78 cases / 75 passed / three existing skips**
on the Edge 20 / Android 12 and by direct instrumentation on the Google APIs
API 29 emulator with the pinned provider. The original WebView 74 passes eleven
scoped document/cache cases on the local AOSP image and one capability check on
Google APIs. Unlike the earlier unavailable Windows AVD attempts, the SDK's
ASCII path and AEHD now produce working local emulators; Google APIs uses native
1080x1920 / 420dpi geometry, two cores, 2 GB and SwiftShader. An early baseline
attempt with unsuitable overridden geometry was interrupted, not accepted.

Require the new commit's own six-job remote acceptance, including actual 16 KB.
The final real helper invocation also passes end to end locally: original
capability, pinned startup/native flags proof and all 78 ordinary cases
(75 passed, three expected skips), with its owned flags file removed afterward.
See [final local acceptance](LOCAL_DOCUMENTS.md#final-local-acceptance-23-october-2026)
for the complete build/unit/lint/native evidence and limits.
Physical Android 10/32-bit media, human CAPTCHA/keyboard/browser, CVE scanning,
owner-approved changelog and final README gates remain. Detailed sources and
boundaries are in [the local-document review](LOCAL_DOCUMENTS.md).

## Failures investigated on 19 October 2026

Runs [11](https://github.com/jagumiel/mimi-reader/actions/runs/39071074251),
[12](https://github.com/jagumiel/mimi-reader/actions/runs/39158910576) and
[13](https://github.com/jagumiel/mimi-reader/actions/runs/39160285233) all passed
build, lint and unit tests; run 13 also passed SBOM verification. All three
failed the mandatory bundled-Ruffle playback test. Downloaded run-13 logcat
reports identified these distinct causes:

- API 29's original AOSP WebView could not parse Ruffle's optional chaining:
  `Uncaught SyntaxError: Unexpected token .`.
- API 31 and 36 initialized WASM but panicked in wgpu shader compilation:
  `Vertex shader active uniforms exceed GL_MAX_VERTEX_UNIFORM_VECTORS (256)`.
- API 34 failed before tests: SDK Manager could not prepare its system-image
  ZIP (`Error on ZipFile unknown archive`).

The test previously waited 20 seconds even after a player error. That generic
assertion obscured the actual JavaScript/graphics failure.

### Run 14: Android 10 selection-response regression

[Run 14](https://github.com/jagumiel/mimi-reader/actions/runs/39314808778)
passed build/SBOM checks and the Android 12, 14 and 16 suites. On Android 10,
the original-provider test passed and APK installation succeeded, but the helper
rejected the provider-selection command's plain `Success` response before
querying its active state. The upgraded-provider playback suite never started.
This was a CI-helper error, not a reported application-test failure.

Android 10's [shell implementation](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-10.0.0_r1/services/core/java/com/android/server/webkit/WebViewUpdateServiceShellCommand.java)
returns this short success message. The helper now preserves nonzero-exit
failures and checks the exact **current-provider** package/version line in
`dumpsys webviewupdate`, independently of success-message wording. An eligible
provider elsewhere in the dump is not sufficient. The 21 helper/contract tests
include both response formats and reject wrong/missing active providers or
versions. [Run 15](https://github.com/jagumiel/mimi-reader/actions/runs/39318200289)
then passed build/SBOM checks and all four instrumentation jobs, including the
full API 29 suite after provider installation. This closes the original Flash/CI
regression; subsequent dependency changes still require their own green run.

## Application changes

The trusted Flash shell prefers Ruffle's legacy `webgl` renderer, avoiding the
wgpu shader path that panics on the emulators' SwiftShader limits. Ruffle can
still fall back to Canvas. The upstream runtime and its inventoried hashes have
not been modified. The legacy renderer has fewer advanced rendering features
than wgpu; complex bitmap effects/blend modes and ActionScript compatibility
still need real-content testing. See the
[versioned renderer configuration](https://github.com/ruffle-rs/ruffle/blob/v0.7.1/web/packages/core/src/public/config/load-options.ts).

The shell checks JavaScript syntax and WebAssembly support, catches startup
errors/rejected promises and publishes a bounded diagnostic. A deficient
WebView produces an explicit update message, rather than an indefinitely blank
gallery. This does not increase `minSdk` or require upgrading Android 10/12.
Users should update their normal Android System WebView through their device's
trusted distribution channel, not install the historical CI provider below.

Instrumentation fails immediately when the player reports an error, including
the state/message/user agent in the assertion. The outer 60-second deadline
allows the player's 45-second startup deadline to report first; it is not a
retry and does not mask graphics failures. Synthetic startup-error tests verify
that errors are delivered within five seconds.

## Emulator preparation

All matrix entries use rootable/debuggable `google_apis` x86_64 images. SDK image
installation has three bounded attempts; cleanup is restricted to the exact
matrix image package. Test failures are **not** retried or ignored. The emulator
action reuses the prepared image. Its supported options are documented
[upstream](https://github.com/ReactiveCircus/android-emulator-runner/tree/v2.38.0).

For API 29 only, `scripts/ci/android_webview.py`:

1. Downloads and verifies the pinned AOSP APK before running installation commands.
2. Checks an emulator serial, `ro.kernel.qemu=1`, API 29, a debuggable image and
   absence of a conflicting `com.android.webview` installation. It refuses a
   physical device and never removes a system app or modifies a system image.
3. Tests the original provider: successful playback if its capability probe
   succeeds, otherwise the explicit unsupported-WebView result. This report is
   preserved separately from the subsequent suite.
4. Installs/selects the test provider and verifies its exact active version.
5. Runs the complete suite with SWF playback **mandatory**, without skip flags.

The extra original-provider test is opt-in; the separate live `/f/` smoke test
also remains opt-in to avoid depending on CDN availability. Neither replaces
the mandatory offline SWF playback fixture on any matrix entry.

### CI-only provider provenance

- Source: [AOSP chromium-webview](https://android.googlesource.com/platform/external/chromium-webview/+/27ac2bdb9a1a4e55dc65bb6cdc752dbfdea3b9f4/128.0.6613.88/x86_64/webview.apk).
- Revision: `27ac2bdb9a1a4e55dc65bb6cdc752dbfdea3b9f4`.
- Version: `128.0.6613.88`; package: `com.android.webview`; minimum API: 26.
- APK SHA-256: `deb1987501d40c4c326697be8fdd7733abdf2e0ab7fb6e028205b0ff786876e5`.
- Scope: historical compatibility-test baseline inside a disposable emulator,
  **not** a current secure browser recommendation or an application dependency.
  It is never bundled in the APK, committed as a binary, or installed on the phone.

The helper retries interrupted downloads up to three times, but hash mismatches
are hard failures. Gradle is scoped to the verified emulator via `ANDROID_SERIAL`.
The emulator action receives one complete Python command: its script parser runs
each input line in a separate shell, so multiline shell conditionals are not used.

Google APIs userdebug images allow this additional provider; see Chromium's
[provider requirements](https://chromium.googlesource.com/chromium/src/+/HEAD/android_webview/docs/webview-providers.md).
Changing the pinned APK requires deliberate provenance/hash review and a full
playback run. Newer matrix entries keep their image's own WebView.

## Local checks and remaining verification

Since 20 October 2026 the app and library have no product flavors. CI uses only
`debug`/`release`, checks the actual application variant IDs with
`verifyApplicationVariants`, and uploads artifacts from `apk/debug`,
`apk/release`, `bundle/release` and `mapping/release`. The WebView wrapper runs
`connectedDebugAndroidTest`; its provider-safety checks and emulator matrix are
unchanged. CI helper coverage is now 22 tests, including these task/output paths.
The next cleanup also makes `:chanlib:testDebugUnitTest` mandatory in the build
job: its eight actual utility tests replace the old arithmetic example. A device
color-compatibility test compares the refactored utility with Android's legacy
parser across 136 deterministic names on every instrumented matrix target.

The posting block adds 14 protocol unit tests (94 app tests in total) and five
mandatory offline device tests in `PostingFlowTest`. A subsequent sixth test drives
the actual composer through CAPTCHA confirmation into a mocked HTTP submission
and verifies rejection recovery; the matrix now has 40 cases with three opt-in
skips. It also checks that readiness enables an explicit Send message action,
without posting or requesting another challenge automatically. They exercise a local
TCaptcha fixture through the production document/resource interceptor, visible
dialog size, explicit confirmation/reload, draft and attachment restoration,
in-flight rotation/duplicate protection, and host-scoped cookies/headers. No CI
test posts public messages or solves real CAPTCHA challenges. A third opt-in
test, `livePostingCaptcha=true`, is a human-only widget preview with no posting
API attached; it stays skipped in the normal matrix. See [POSTING.md](POSTING.md).

[Run 21 for `6541737`](https://github.com/jagumiel/mimi-reader/actions/runs/39409405146)
passed all five jobs: build/lint/unit/SBOM plus the Android 10, 12, 14 and 16
instrumentation matrix. This confirms the fourth cleanup and posting blocks.
It does not replace physical-device CAPTCHA acceptance on Android 10.

The following AndroidX UI block adds two mandatory offline cases in
`AndroidXUiCompatibilityTest`: lifecycle-owned Material dialogs under the light,
dark and black themes, and settings nested-screen/dialog restoration followed
by Back navigation without changing the selected preference. The suite now has
42 cases, with the same three opt-in skips. [Run 22 for `04b3a42`](https://github.com/jagumiel/mimi-reader/actions/runs/39469404001)
now confirms this AndroidX baseline: all five jobs passed, including the four
instrumented API targets.

The sixth cleanup adds a third mandatory `AndroidXUiCompatibilityTest` case for
the filter editor's platform spacers, controls, fixture text and edit/cancel
callbacks under three themes and two widths. It never saves filters or changes
theme preferences. The suite now has 43 cases with three opt-in skips. SBOM
negative coverage expands from four to six malformed documents, checking exact
application root edges as well as component coverage/schema/hashes. The raw
generator refreshes even if only direct/transitive relationships change. This
cleanup is now confirmed by [run 23 for `e485a71`](https://github.com/jagumiel/mimi-reader/actions/runs/39473553462):
all five jobs passed, including Android 10/12/14/16.

The seventh cleanup upgrades only GridLayout to 1.1.0 and strengthens the same
filter-editor case with bounds, overlap and action-row assertions (still 43 cases,
three opt-in skips). Its own remote matrix remains pending. Neither a green
API 36 job nor the existing 4 KB physical phone establishes native 16 KB page-size
compatibility: PDF/GIF ELF findings and the separate runtime acceptance gate are
recorded in [the dependency audit](DEPENDENCIES.md#native-audit-a-separate-pre-release-requirement).

[Run 24 for `3c5357f`](https://github.com/jagumiel/mimi-reader/actions/runs/39480315229)
passed all five preceding jobs. The native-media cleanup now has **six** jobs:
the existing build and four-API full suite remain, plus a dedicated API 36
`google_apis_ps16k` run of six native fixtures. The full suite expands to 49
cases (46 mandatory, three opt-in skips). The physical Edge 20 / Android 12
passed all 46 mandatory cases; that device has 4 KB pages, not 16 KB.

The build also runs `scripts/ci/android_native.py` on debug/release APKs and SDK
`zipalign` on release. The 24 packaged libraries retain arm64/x86_64 and ARM/x86
32-bit coverage; ELF64 PT_LOAD and uncompressed ZIP entries must be 16 KB aligned.
Reports are uploaded for 14 days. The verifier does not certify all ELF semantics
or replace runtime tests. CI-helper coverage is now 40 tests, including synthetic
old/malformed ELF/ZIP rejection, ABI preservation, emulator guards, SDK targets
and workflow job boundaries.
Release verification also checks `seeds.txt` for the two Pdfium JNI-resolved
types/constructors, preventing R8 from silently stripping a password exception
or native size constructor while keeping the ELF files correctly aligned.

The native runtime launcher refuses physical devices and ordinary 4 KB emulators
before any setting change. It verifies API 36 and actual `PAGE_SIZE=16384`, roots
only that disposable emulator, disables/verifies both native backcompat switches,
then scopes Gradle to its serial. The instrumentation also checks the expected
page size in every case. No failing test is retried. Preparation retries apply
only to SDK downloads. Its first remote execution in run 26 reached actual
16 KB pages but failed the fixture cases described above; run 27 subsequently
passed all six native cases. The local Windows ps16k emulator did not reach ADB and is not
counted as passing.
See [native acceptance limits](DEPENDENCIES.md#eighth-cleanup-pdfgif-native-replacement-and-resource-lifecycle).

```sh
python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v
bash -n scripts/ci/install_emulator_sdk.sh
./gradlew verifySbom testSbomValidation \
  :mimi-app:verifyApplicationVariants \
  :mimi-app:testDebugUnitTest :mimi-app:lintDebug \
  :chanlib:testDebugUnitTest \
  :mimi-app:assembleDebug :mimi-app:assembleRelease :mimi-app:bundleRelease
./gradlew :mimi-app:connectedDebugAndroidTest
```

The helper tests cover bounded SDK retries, final failure propagation, invalid
API arguments, APK hashes/base64/cache handling, device safety and provider
selection before the full suite. They do not substitute for a real emulator run.
After pushing this change, check **all four** matrix jobs and their artifacts;
do not mark the remote CI fixed merely because local checks pass.

The Motorola Edge 20 (Android 12, WebView 155) passed the gallery playback suite
with the WebGL renderer during this investigation. A later full-suite attempt
was interrupted after the phone locked: ActivityScenario could not reach
`RESUMED` behind its keyguard. That interrupted run is **not** recorded as passing.
After the user unlocked the phone on 19 October 2026, the complete suite passed:
32 cases, 30 passed, 2 expected opt-in skips, no failures or errors. The
original-provider capability/playback check was then explicitly enabled and also
passed separately (1 test). This validates its supported-provider path on
WebView 155; run 15 also confirms the API 29 original-provider check and full
suite with the pinned provider.
Local Windows API 29/31 emulators did not reach ADB availability, so no local
emulator-matrix success is claimed. The remote baseline is green in run 15.

Final local checks passed: 80 app unit tests, 21 CI-helper/contract tests, debug
lint, SBOM freshness/negative tests, minified release APK/AAB generation, and the
isolated instrumented startup-error test (both error states, without needing an
unlocked display). The temporary phone stay-awake setting was restored.
