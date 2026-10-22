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
