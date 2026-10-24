# Android test matrix

Mimi Reader keeps `minSdk 23`, but the supported release baseline is exercised on four representative Android versions. GitHub Actions runs every instrumented test on each API level in parallel and preserves the HTML report, JUnit result, device information, and per-test logcat output for 14 days.

| Android | API | Purpose |
| --- | ---: | --- |
| 10 | 29 | Oldest device generation actively used by the project and legacy storage/lifecycle behaviour. |
| 12 | 31 | Regression target for startup, exported components, notifications, tabs, and the Motorola Edge 20. |
| 14 | 34 | Modern permissions, foreground work, system bars, and activity restoration. |
| 16 | 36 | Current compile/target SDK behaviour and forward compatibility. |
| 16, 16 KB | 36, `google_apis_ps16k` | Separate native PDF/GIF fixture job; actual page size asserted, backcompat disabled. |

The suite covers startup routing, tab and activity restoration, back navigation, Room and preference migrations, cache clearing, gallery playback and autoplay, MP4 decoding, and persistent WorkManager downloads.

On 20 October 2026, the cleanup adds a device check comparing `ChanUtil` colors
with the old Android color parser across 136 deterministic inputs. The Motorola
Edge 20 / Android 12 suite has 33 cases: 31 passed and two expected opt-in skips.
Eight real `chanlib` utility unit tests replace its old arithmetic example and
are now explicitly run in CI alongside the app's 80 unit tests.

The posting block adds 14 app protocol unit tests (94 app + 8 library) and five
mandatory offline instrumented checks: local-widget confirmation/reload/expiry,
visible dialog sizing, draft/attachment restoration and cancellation, rotation
of a single pending attempt, and posting cookie/header boundaries. The full
suite now has 39 cases including three opt-in skips. Public posting is never
part of CI; the extra live preview requires a human and has no posting API
attached. Fixture success does not establish real site acceptance. See
[posting contracts and acceptance limits](POSTING.md).

On 20 October the complete updated suite ran through ADB on the physical Motorola
Edge 20 / Android 12 / WebView 155: `OK (39 tests)`, 36 mandatory checks passed and
three opt-in checks skipped by their assumptions. The real human-only CAPTCHA
preview was recorded separately as `OK (1 test)` before the full suite. No
message was posted. The temporary stay-awake setting was restored to
its original value, 0. Remote Android 10/12/14/16 results for this posting commit
still require confirmation after pushing it.

Later on the same day, confirmation-UX coverage and an actual-composer-to-mocked-
HTTP test expanded the suite to 40 cases. After an interrupted attempt behind
the phone keyguard, the owner unlocked it and the complete Gradle device suite
passed: 37 passed, three expected opt-in skips, zero failures/errors. All six
offline posting-flow cases passed, alongside 94 app unit tests. The original
stay-awake setting (0) was restored. An earlier, separately authorized text reply
was confirmed publicly as post `110119159`; this regression run made no public
submissions. See [POSTING.md](POSTING.md) for the precise scope.

Remote [run 21 for `6541737`](https://github.com/jagumiel/mimi-reader/actions/runs/39409405146)
passed build/lint/unit/SBOM and the complete Android 10/12/14/16 matrix,
confirming the previous cleanup and posting regression coverage. Physical
Android 10 CAPTCHA/keyboard/browser acceptance remains pending; emulators do
not establish live provider acceptance.

The AndroidX UI migration adds two mandatory offline compatibility tests:
Material dialogs have view-tree lifecycle owners under light/dark/black themes,
and a nested settings screen with an open preference dialog survives activity
recreation, preserves its value when cancelled, and navigates Back correctly.
They do not modify the user's chosen theme/preference or post public messages.
That block has 42 cases, with three opt-in skips. Its remote matrix is now
confirmed by green [run 22 for `04b3a42`](https://github.com/jagumiel/mimi-reader/actions/runs/39469404001)
on Android 10/12/14/16, alongside build/lint/unit/SBOM.

On 20 October the complete 42-case suite passed on the physical Motorola Edge
20 / Android 12 / WebView 155 with the new AndroidX baseline: 39 passed, three
expected skips, zero failures/errors. The two new cases also passed separately
before the full run. No public posts were made and the original stay-awake
setting (0) was restored after testing.

The sixth cleanup adds a mandatory offline filter-editor case to
`AndroidXUiCompatibilityTest`. It checks five platform spacers and their heights,
ten grid columns, usable controls at 320/480dp, preserved fixture text and edit/
cancel callbacks in all three themes. The editor stays detached: no board fetch,
Save action, filter write or theme preference change. The suite now has 43 cases
with the same three opt-in skips; its remote result is recorded below.

On 20 October, after the owner unlocked the Edge 20 / Android 12, the complete
43-case suite passed: 40 passed, three expected opt-in skips and zero failures/
errors, including the new filter-editor case. No filter was saved and no public
message was posted. The original phone stay-awake setting (0) was restored.

[Run 23 for `e485a71`](https://github.com/jagumiel/mimi-reader/actions/runs/39473553462)
confirms that six-block baseline: build/lint/unit/SBOM and all four Android
instrumented jobs passed. The seventh cleanup strengthens the existing filter
case with control bounds, input/button non-overlap, vertical order and action-row
alignment at the same three themes/two widths. The stronger case passed against
GridLayout 1.0.0 before upgrading to 1.1.0; the suite still has 43 cases.

The complete suite with GridLayout 1.1.0 passed on the Edge 20 / Android 12 on
20 October: 40 passed, three expected skips and zero failures/errors. The
phone's original stay-awake setting (0) was restored. The filter fixture never
saves filters or changes theme preferences; no public messages were posted.
This library change still requires its own remote matrix after pushing the commit.

Native 16 KB acceptance is a separate requirement, now confirmed remotely in
run 27 for `fed11d2` (see below). The Edge 20
reports `PAGE_SIZE=4096`; neither its successful rendering tests nor an API 36
label alone verifies 16 KB behaviour. PDF/GIF binary audit findings and required
follow-up are in [DEPENDENCIES.md](DEPENDENCIES.md#native-audit-a-separate-pre-release-requirement).

On 21 October, the PDF/GIF native replacement added six mandatory local cases:
actual two-page colour rendering, scrolling/zoom, pause/detach/reselection release,
activity + ViewModel/Bundle restoration, malformed/password errors and retry,
stale-load cancellation, GIF loops/pause/resume/recycling, and bounded viewport
state. Five initial native cases passed separately; the final complete suite
then passed on the Edge 20 / Android 12: **49 cases, 46 passed, three expected
opt-in skips, zero failures/errors**. Stay-awake was restored to 0. No public
posts or user-data clearing were involved. The obsolete PDF layout/binding is no
longer used; the production gallery creates PDF pages directly.

The same six native cases run in the dedicated ps16k job with
`expectedPageSize=16384`, checked via `sysconf` before each case. Its launcher
also requires a matching API 36 emulator and disables/verifies both backcompat
properties. A 4 KB phone/emulator cannot substitute for that job. The local
Windows ps16k AVD did not reach ADB; run 27 instead supplies remote actual-16-KB
evidence after the fixture corrections below. Physical 32-bit Android 10 media/CAPTCHA
acceptance also remains pending; retaining packaged ARM/x86 ABI files is not a
claim that a physical 32-bit runtime has been tested in this block.

Run 26 reached all five emulator jobs after a successful build. Each regular
API failed only the blue-PDF viewport fixture; ps16k passed four of six cases,
with the same viewport failure and one fixture deleted by startup cache cleanup.
The fixtures now use per-test JUnit folders outside caches, apply zoom before
positioning, and assert three pixel colours plus page/zoom through restoration.
The six corrected native cases passed separately on the Edge 20 / Android 12.
See [run-26 diagnosis](CI.md#run-26-native-pdf-fixture-isolation-and-viewport-positioning);
the complete remote six-job result remains pending.

After the correction, the complete Edge 20 / Android 12 suite passed again:
49 cases, 46 passed, three existing opt-in skips and no failures/errors,
including all six stricter native cases and cache-cleaner preservation checks.
The temporary stay-awake change was restored to 0. No real posting was performed;
actual 16 KB and cold-install acceptance still require the remote rerun.

That rerun is now confirmed: [run 27](https://github.com/jagumiel/mimi-reader/actions/runs/39669781921)
passed all six jobs for `fed11d2`, including six actual-16-KB native cases without
backcompat and the full ordinary API matrix. Physical 32-bit Android 10 and live
CAPTCHA acceptance remain separate release checks.

The ninth cleanup adds five intercepted-intent browser cases for the Novoda
replacement: Custom Tab extras/toolbar colour/URL preservation, LinkSpan's real
preference dispatch and style, external/non-web handling, fallback/missing
handlers and Activity/wrapped-context task flags. They do not open real browsers,
fetch URLs or change preferences. The suite has 54 cases with three opt-in skips;
the six-case native job is unchanged. All five new cases passed separately on
the Edge 20 / Android 12; this cleanup requires its own remote six-job result.

On 22 October, the full ninth-block suite passed on that Edge 20: 54 cases,
51 passed, three existing opt-in skips and zero failures/errors. All five new
browser cases and all six native cases passed. The phone's stay-awake setting
was restored to 0; no real browser navigation or public posting was triggered
by the new fixtures. Remote Android 10/12/14/16 plus ps16k acceptance for the
new cleanup commit remains pending.

## Tenth cleanup: fast-scroll acceptance

Run 28 confirms the ninth cleanup: all six jobs passed for `dc17d6d`, including
the complete Android 10/12/14/16 matrix and actual 16 KB native tests.

The tenth cleanup adds four production-layout fast-scroll cases using synthetic
variable-height posts and MotionEvents, not a network thread: index-based
first/middle/last targets; popup bounds and drag gestures across three themes,
320dp LTR/480dp RTL layouts; pull-to-refresh after cancellation; and empty/short/
replaced adapters plus detach/reattach without duplicate controllers. A drag
cancelled by clearing data must also recover with a new adapter. All four cases
passed separately on the Edge 20 / Android 12. Six numerical unit tests cover
bounds/overflow. The suite now contains 58 instrumented cases, still with three
opt-in skips; the native 16 KB job remains six cases. A new remote run is required.

The final tenth-block suite passed on the Edge 20 / Android 12 on 22 October:
58 cases, 55 passed, three existing opt-in skips, no failures/errors. All four
new cases passed, including resumed dragging with replacement data; the earlier
scoped four-case run also passed before that extra recovery assertion was added.
The phone's temporary stay-awake setting was restored to 0. Full remote
Android 10/12/14/16 and actual-16-KB validation of this cleanup remains pending.

## Eleventh cleanup: Material and gallery-container acceptance

Run 29 (`db43b0b`) confirms the tenth cleanup, including Android 10/12/14/16
and actual 16 KB native execution. API 29's completed log confirms 58 cases
with three expected skips, no failures and uploaded reports even though its
job-detail response was stale at review time.

Three mandatory offline `MaterialComponentsCompatibilityTest` cases protect
the Material 1.14.0 / required ConstraintLayout 2.2.1 migration. The production
tabs layout with synthetic pages checks two-way mediator selection, chrome/FAB
bounds and clicks, collapse/expand movement and restoration in three themes at
320dp LTR / 480dp RTL. Dialog choices/confirmation and Snackbar action/dismissal
are checked in each theme. Both production gallery containers must fill their
measured bounds at those widths/directions/themes, releasing their media
fixtures. The three cases pass against the old versions before upgrading.
The suite becomes **61 cases**, still with three opt-in skips; existing insets,
recreation, drawer/back, settings and playback/autoplay checks remain mandatory.
This commit requires its own remote six-job acceptance; human interaction and
physical Android 10/32-bit gates remain pending.

Final eleventh-block validation on 22 October passed on the Edge 20 / Android 12:
**61 cases, 58 passed, three existing opt-in skips, zero failures/errors**,
including all three new cases and the six native cases. Unit coverage remains
102 app + eight library cases; 40 CI-helper tests also pass. Lint has zero errors,
release APK/AAB and static native guards pass, and phone stay-awake is restored
to 0. Remote acceptance for this new cleanup remains pending.

## Twelfth cleanup: Glide image/cache acceptance

Run 30 (`cca6fa6`) confirms the eleventh cleanup across all six CI jobs.
The Glide block adds four offline cases, growing the ordinary suite to **65
cases** with three existing opt-in skips. The dedicated ps16k suite stays at six.

`GlideCompatibilityTest` checks the actual registered OkHttp loader, then a
separate intercepted client for image decoding and HTTP errors; local thumbnail
pixels, recycled-view replacement and SWF/missing placeholders; five combinations
of primary/fallback success/failure with exactly one terminal callback; and
RESOURCE_DISK_CACHE pixel decoding after clearing memory. No registry/singleton
replacement, public request, custom CA, cleartext permission or persistent
preference change is needed. Each file fixture is isolated under filesDir.
All four cases pass separately on the Edge 20 / Android 12 with Glide 5.0.7.
Existing cache-cleaner, gallery playback/autoplay, lifecycle and native checks
remain mandatory. The full-media queue still has two workers and nearest-first
priority, with no ten-item limit.

The 4.11.0 baseline exposed a null-placeholder crash in the latent fallback
branch; transport and disk-cache cases passed. Correct the thumbnail fixture's
false assumption that clear() must produce null: it may restore a placeholder.
Retain checks that the old image is removed and the replacement pixels are blue.
The new commit still needs its own complete remote six-job run and does not
replace physical Android 10/32-bit or human-interaction acceptance.

The first full local run exposed a phone sleep transition in the unchanged
settings case and a disk-encoding race in the new fixture. Wake the phone and
wait for cache-only availability within the same total ten-second deadline;
Glide notifies targets before deferred encoding finishes. All seven scoped
Glide/UI cases then pass. The final full Edge 20 / Android 12 suite passes on
22 October: **65 cases, 62 passed, three expected skips, no failures/errors**,
including the six native cases and cache reuse after cache clearing. Unit/CI
helper tests, lint, SBOM, release APK/AAB and native guards pass; stay-awake is
restored to 0. No production settings behaviour or CI skip/retry policy is changed.

## Thirteenth cleanup: filter-row layout acceptance

Run 32 (`2cfa199`) confirms the preceding Glide block across all six jobs.
Three new mandatory `FilterListLayoutTest` cases bring the ordinary matrix to
**68 cases**, with the same three opt-in skips. The actual-16-KB job stays at six.

Use the real adapter and in-memory filters: LTR and RTL rows in light/dark/black
themes, font scales 1.0/1.3, 320dp / 480dp-plus-one-pixel widths, equal columns
without overflow, preserved height, padding/alignment, colours and SP typography,
and unchanged full underlying long text. Replacement/removal/rebinding must keep
adapter notifications and click handlers. Do not open dialogs, mutate real
filters or claim that an actual swipe-to-delete/database edit was exercised.

The old layout reproduced child overflow in RTL after fixing the fixture's
missing LayoutManager. Allow expected nearest-pixel XML text-size rounding,
not a font-size change. All three new cases pass separately on the Edge 20 /
Android 12 with the platform weighted row. New remote six-job acceptance and
physical/human release gates remain pending for the new commit.

Final thirteenth-block acceptance passes on the Edge 20 / Android 12 on
22 October: **68 cases, 65 passed, three existing opt-in skips**, zero failures/
errors, including all three new cases and the six native cases. Also pass
102 app + eight library unit tests, 40 CI-helper tests, lint without errors,
SBOM, minified release APK/AAB and static native checks. The phone stay-awake
setting is restored to 0. This does not pre-approve the new commit's remote
matrix or replace physical/human acceptance.

## Fourteenth cleanup: More Info links, credits and navigation

[Run 33 for `3dd4c9d`](https://github.com/jagumiel/mimi-reader/actions/runs/39852907707)
confirms all six jobs for the filter-row cleanup. Four new mandatory
`MoreInfoPrefsTest` cases increase the ordinary suite to **72 cases**; the three
opt-in skips and six dedicated 16 KB cases are unchanged.

- Open the real More Info entry from Settings, with no automatic browser launch.
  Check removal of rating/email rows, independent-fork status, Apache-2.0 label,
  credit roles and current version. Intercept each of the seven links and check
  exact HTTPS URLs, ACTION_VIEW, no extras/diagnostics and no new-task flag.
- Simulate both ActivityNotFoundException and SecurityException on every link:
  no crash, email/market fallback, additional launch or preference modification.
- Recreate More Info, repeat link callbacks, then use the Back dispatcher to
  return to the root settings screen with its original saved preferences.
- Open each bundled privacy/licenses/historical-changelog dialog, recreate the
  activity and dismiss it, retaining the underlying More Info back stack.

All external intents are intercepted; no real email, browsing or public posting
is performed. The first physical attempt was interrupted by phone lock/dozing,
not accepted as passing. Correct the fixture's monitor registration before
cleanup, rather than allowing cleanup to mask that failure. After the user
unlocked the phone, all four cases passed separately on the Edge 20 / Android 12.
This is not manual physical Android 10 acceptance or the new commit's remote
six-job acceptance. Notice contents/view lifecycle remain a separate refactor.

The repeat whole-suite run exposed an existing Glide fixture race: readback
returned MEMORY_CACHE despite the preceding clearMemory call. The test now
explicitly bypasses memory and reads only the RESOURCE disk cache, retaining
cache-only retrieval, exact disk-source/pixel assertions and the original total
ten-second encoding deadline. Production caching, CI retries/skips and timeouts
are unchanged. Keep the failed run as evidence, not a passing acceptance result.

Final acceptance on 23 October: all eight scoped Glide/More Info cases pass,
then the full Edge 20 / Android 12 suite passes **72 cases: 69 passed, three
existing opt-in skips, zero failures/errors**. Pass 102 app + eight library unit
tests, 40 CI-helper tests, lint without errors, SBOM checks, release APK/AAB and
static ELF/ZIP/JNI/SDK 16 KB zipalign guards. Restore and verify stay-awake **0**.
The local pass does not replace this commit's remote matrix or physical/human
release acceptance. See [detailed evidence and limits](DEPENDENCIES.md#fourteenth-block-validation-23-october-2026).

## Fifteenth cleanup: real document rendering and provider-safe cache clearing

The ordinary suite is **78 cases**, still with three opt-in skips; the dedicated
actual-16-KB suite remains six. Six mandatory `LocalDocumentTest` cases cover:

- Actual rendered text for licenses, privacy and historical changelog, both
  source packages, recreation, custom arguments and preserved dialog back stack.
- Disabled script/storage/file/content access and blocked networking, the exact
  local 200/CSP response and denied external resources; user browser links,
  fragments, blocked schemes/subframes/automatic navigation, absent/blocked apps.
- Bad-resource error/Retry, simulated renderer-exit disposal and a fresh retry
  WebView, pending-read cancellation and ignored callbacks from a destroyed view.
- Media-cache clearing after a real WebView has loaded, followed by recreation
  and an actual text assertion. The existing cache test separately preserves
  owned markers inside `WebView` and `WebView_*`, along with persistent data.

Find text natively with JavaScript still disabled. Size/visibility/load-finished
signals alone missed a blank document in the first Edge 20 implementation; a
diagnostic screenshot confirmed it. Serve the HTML as an actual intercepted
response, rather than weakening the content assertion or enabling scripting.
An initial old-provider test also assumed getUrl equals the supplied base URL;
the correct invariant is that external link handling leaves the local page URL
and content unchanged. Diagnostic JavaScript/screenshot code is not shipped.

The Edge 20 final suite passes **78 cases: 75 passed, three existing opt-in skips,
zero failures/errors**, with the original stay-awake setting restored to 0.
Local API 29 / Google APIs with the exact pinned WebView 128 also passes all 78
by direct instrumentation. On the original WebView 74, eleven scoped document/
cache cases and the separate original-provider capability case pass. Neither
an interrupted baseline run nor that scoped provider check is a full ordinary
suite pass. The physical Android 10/32-bit and human-interaction gates remain.

CI run 34 passes five jobs but fails the old API 29 renderer. The helper now
selects the nonexperimental Binder transport only for the verified disposable
API 29/pinned provider, proves its native flags with one existing startup case
before the full suite, retains that preflight's results, and removes its owned
flags file in finally. No phone flags, provider hash, playback assertion, retry
or skip policy changes. See [sources, evidence and limits](LOCAL_DOCUMENTS.md).
The new commit still needs all six remote jobs.

The final end-to-end invocation of the actual CI helper also passes locally on
API 29: original capability (one case), pinned startup/native proof (one case),
then all 78 ordinary cases (75 passed, three expected skips). The owned flags
file is verified absent afterward. See [final local acceptance and artifact
limits](LOCAL_DOCUMENTS.md#final-local-acceptance-23-october-2026); this does not
claim the next remote run has passed.

## Sixteenth cleanup: live settings navigation and legacy values

[Run 35](https://github.com/jagumiel/mimi-reader/actions/runs/39930834599) accepts
the previous block in all six jobs, including API 29 and native 16 KB. This new
block raises ordinary instrumentation to **80 cases**, keeping the three opt-in
skips and six native 16 KB cases unchanged.

`SettingsNavigationTest` clicks all six actual headers, checks live controls and
restores each category after activity recreation, then returns with Back to the
same root. Existing saved values must stay equal; a fresh installation may
initialize missing XML defaults. No destructive/authentication/external control
is clicked. `PreferenceMigrationTest` separately preserves stored legacy key
values and unknown data in isolated preferences, including repeated migration.
Existing posting and document tests are not replaced. See [the orphan-resource
audit](SETTINGS_RESOURCE_AUDIT.md) for deletion evidence and acceptance limits.

Local acceptance on 23 October passes the full Edge 20 / Android 12 suite:
**80 distinct JUnit cases, 77 passed, three existing opt-in skips, zero
failures/errors**. The API 29 emulator separately passes all five scoped
settings/migration cases without skips. Restore stay-awake to 0 on the phone
and 1 on the owned emulator, then shut down the emulator. This is not a new
physical Android 10, cold-install, minified-release UI or remote-matrix pass.

## Seventeenth cleanup: settings layout and generated bindings

[Run 36](https://github.com/jagumiel/mimi-reader/actions/runs/39936759280) passes
all six jobs for the previous block. Add one mandatory detached-layout case:
bind both live settings layouts in light/dark/black, at 320/480 dp, LTR/RTL;
check toolbar/container geometry, swatches/spinner/preview and unchanged saved
preferences. Extend real six-category navigation with actual binding/toolbar
checks around recreation. No real color editor or destructive control is
invoked; its interactive lifecycle is not accepted by detached inflation.

The ordinary suite becomes **81 cases**, with the same three opt-in skips;
the dedicated native 16 KB job remains six. The deleted shell and its generated
binding have no consumers; both live layouts remain. See the
[audit](SETTINGS_RESOURCE_AUDIT.md#seventeenth-block-settings-layouts-and-generated-bindings)
for evidence and acceptance boundaries. Require a new six-job remote run.

Final local acceptance on 23 October passes all **81 distinct Edge 20 / Android
12 JUnit cases: 78 passed, three existing opt-in skips, no failures/errors**.
The two settings cases also pass separately there, and all six scoped settings/
migration cases pass on the API 29 emulator without skips. Restore phone
stay-awake to 0, preserve the emulator's observed value 3 and stop the emulator.
Do not count these as physical Android 10, cold-install or remote acceptance.

## Eighteenth cleanup: actual color editor lifecycle

Five new mandatory `ThemeSettingsActivityTest` cases cover real preview text/span
colors, stable swatch drawables/pixels, selection/restoration/read-only lifecycle,
palette slot isolation, picker draft/target restoration and explicit OK, cancel/
rapid duplicates/invalid results, and real production-activity read-only values.
Editing uses a debug-only non-exported host with its own preferences, not a
snapshot/teardown scheme that fails if the process crashes. Verify the fixture
is absent from release. Ordinary instrumentation becomes **86 cases**, with
three existing opt-in skips; the native 16 KB suite remains six.

Run 37 supplies five green jobs, but **no native test result**: adbd closes at
the root request. Five helper cases test bounded reconnection, actual root/boot,
denial, deadline and post-restart identity/page size (59 CI-helper tests total).
See [the diagnosis and isolated-test recovery](COLOR_EDITOR.md); local results
do not replace the new commit's six remote jobs or physical release gates.

Final local acceptance on 24 October passes **86 distinct Edge 20 / Android 12
JUnit cases: 83 passed, three existing opt-in skips, zero failures/errors**.
The five editor cases also pass separately. The actual updated native helper
passes all six cases on API 36 ps16k with page size 16384, verified uid 0 and
backcompat disabled. The personal phone is not rooted; defaults and original
stay-awake 0 are verified after the suite. Restore the owned emulator's original
uid/properties/stay-awake and stop it. No physical Android 10/32-bit or new human
CAPTCHA/browser acceptance is inferred, and a new green remote run is required.

## Nineteenth cleanup: standalone library dependencies

Eleven new offline JVM cases bring `chanlib` to **19 tests** and app/library
coverage to **121** (102 + 19). They protect connector client/default/custom
configuration, read/post endpoints, cache directory/size, converter/Rx adapter
availability, annotation-contract compilation, absent app-only adapters and
multipart string/int/long/Unicode/unsupported-input behavior. The shared-client
fixture creates lazy Rx calls but never subscribes or sends a request.

The library unit task also requires its independent graph/ownership guard;
local negative probes verify rejection of injected Core KTX/RxAndroid and old
OkHttp. Keep these separate from the unchanged 59 CI-helper cases.

On 24 October the Edge 20 / Android 12 passes **86 distinct XML/JUnit cases:
83 passed, three existing opt-in skips, no failures/errors** in 1m41s. Ignore
the console's inflated 89 completion events. The suite adds no public posting,
live CAPTCHA or native-16-KB claim; restore/verify phone stay-awake **0**.
Both JVM suites, both module lint checks, debug/test and minified release
builds, SBOM/variant/boundary guards and static native/JNI/ZIP checks pass.
Require fresh six-job CI and retain physical Android 10/32-bit release gates.

## Twenty-third cleanup: optional KTX API ownership

Two mandatory `OptionalKtxCompatibilityTest` cases bring ordinary coverage to
**88**, retaining three existing opt-in skips and six separate native cases.
They build Work data/a typed `RefreshWorker` request without enqueueing, preserve
Rx worker inheritance/Room runtime extension availability, reject the unused
Preference KTX facade and exercise core preference operations in three themes
with a detached write-rejecting data store. No activity, user preferences, remote
request or worker execution is needed for the new cases. Existing migration,
settings, media and download coverage remains enabled. Unit/helper counts stay
121 / 59. See the [artifact audit and validation limits](DEPENDENCIES.md#twenty-third-cleanup-optional-ktx-artifacts-and-actual-api-owners-d03).

Local Edge 20 / Android 12 acceptance passes **88 unique JUnit cases: 85 passed,
three existing opt-in skips, zero failures/errors** in 1m46s; both new cases
pass. Use XML counts, not 91 console events. Restore/verify stay-awake 0.
Previous remote CI is deliberately not inspected; new remote matrix and physical
Android 10/32-bit release acceptance remain separate requirements.

## Twenty-fourth cleanup: retained theme-resource compatibility (R03)

`ThemeResourceCompatibilityTest.retainedCardStylesAndSelectorsRenderAcrossThemesAndRecreation`
is mandatory in the ordinary API matrix. Before and after host recreation, it
uses detached real catalog/list/grid and first/reply-post layouts in light,
dark and black at 320/480 dp in LTR/RTL. Check original background resource IDs,
normal/pressed/focused drawable rendering and live catalog CardView color states,
click/focus/foreground behavior, nonzero geometry and direction inheritance.
No user preference writes, adapters or remote media are involved. Existing
settings/navigation and posting cases remain enabled. Ordinary coverage becomes
**89** distinct cases (three existing opt-in skips), with six separate native
cases; unit/helper counts stay 121 / 59. See the [R03 evidence and local results](SETTINGS_RESOURCE_AUDIT.md#twenty-fourth-block-closed-legacy-theme-resource-subtrees-r03).

Fresh Edge 20 / Android 12 acceptance: **89 unique XML/JUnit cases, 86 passed,
three existing opt-in skips, zero failures/errors**, in 1m45s; the new mandatory
case passes. Preserve XML rather than using 92 console completion events as the
case count. Original stay-awake 0 is restored/verified. No fresh remote matrix,
physical Android 10/32-bit or actual-16-KB runtime result is claimed.

## SBOM security policy tests

The security integration adds **37 standard-library Python cases**, bringing
CI-helper coverage to **96**. At that point app/library unit and ordinary/native
coverage stayed 121 / 89 / six. Both the existing Android build job and the
independent SBOM-security scanner run the helpers. They exercise blocking,
report visibility, exact/expiring exceptions, coverage, scanner failures,
database freshness, tampering and SARIF source attribution. Real local Trivy
CLI acceptance and subsequent remote/SARIF confirmation are recorded in
[VULNERABILITY_SCANNING.md](VULNERABILITY_SCANNING.md). No new device run was
claimed for that tooling-only change.

## Commons Lang security compatibility

The Commons Lang security update adds **seven app unit cases** (six original
behavior contracts plus one patched-lookup case): **128 app/library cases**
(109 + 19). Two mandatory `CommonsLangCompatibilityTest` cases exercise the real
option filter and comment parser using only synthetic data. Ordinary coverage
becomes **91**; CI-helper/native counts stay 96 / six. Selected 3.20.0 passes the
unchanged contracts that reject 3.21.0's supplementary-digit classification.
The final full Edge 20 suite passes **91 unique XML cases: 88 passed, three
existing opt-in skips, no failures/errors**, in 1m47s; both new cases pass,
including the supplementary-digit reference. Original stay-awake 0 is restored.
See [baseline failures, fixes and remaining remote/physical gates](COMMONS_LANG.md).

## Local device validation

Connect exactly one authorized device or emulator and run:

```powershell
adb devices -l
.\gradlew.bat :mimi-app:connectedDebugAndroidTest
```

When more than one device is connected, set `ANDROID_SERIAL` to avoid accidentally running the suite on every device:

```powershell
$env:ANDROID_SERIAL = "emulator-5554"
.\gradlew.bat :mimi-app:connectedDebugAndroidTest
Remove-Item Env:\ANDROID_SERIAL
```

On October 17, 2026, all 25 instrumented tests passed on a physical Motorola Edge 20 running Android 12/API 31.

## Release acceptance

Before publishing a release:

- Require a green instrumented-test job for API 29, 31, 34, and 36 from the release commit.
- Repeat startup, tab restoration, gallery MP4/WebM playback, autoplay, cache clearing, and a SAF download on the available physical Android 10 and Android 12 devices.
- Preserve failed CI artifacts until the cause has been understood; do not rerun solely to hide an intermittent failure.
