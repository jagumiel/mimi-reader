# Physical Android 10 / ARMv7 validation

Validation resumed on **25 October 2026**, using source commit **9a81f51**,
version **8.0 / 231**. A follow-up on **26 October** corrects the CAPTCHA issues
found in that baseline. These are isolated-debug results, **not minified-release
acceptance**. No publication or new signing identity is made in either block.

## Device and preservation

- Motorola Moto G 2014 (`titan_umtsds`), custom Android 10 / API 29, ARMv7 only,
  approximately 906 MB RAM, 720 x 1280 display, density 320, font scale 1.0.
- Runtime pages are **4096 bytes**. This validates 32-bit native execution,
  not the separate API 36 actual-16-KB requirement.
- Active provider remains `com.android.webview` **74.0.3729.186**. No WebView
  package/provider, ROM, security setting or animation scale is replaced.
- Original release `com.emogoth.android.phone.mimi` remains **6.1.2 / 226**,
  with its installation/update timestamps unchanged. Its installed certificate
  identifies Eli Connelly and has SHA-256
  `ea7dcff8b4a9f71b8991f636336b542c66a03b3ce85eb28f0588b77b1cf9e3ef`.
- The owner explicitly authorizes updating only `.beta` from 7.0.1 / 230 to
  **8.0 (DEBUG) / 231**, preserving persistent data but allowing cache clearing.
  Its certificate matches the existing public test key, SHA-256
  `e98eedf04e4bba13131ecdd591d8b9bf8906066b9dc942de09105504856a899f`.
  In-place `adb install -r` succeeds; the original first-install timestamp is
  retained. Before opening it, all **six persistent database/preferences files
  are byte-identical**, including database sidecars. They remain identical after
  the isolated suite and cold-start check. No database or cookie data is copied
  into another app or committed; only hashes are compared.
- The protected public folder found here is `/storage/emulated/0/Mimidown`.
  It has zero files before and after testing and remains present afterward with
  a directory timestamp from May 2025.
  No `Mimi`, `Pictures/Mimi` or `mimi_old` folder is found in the inspected
  locations; none is created, removed or renamed.

## Why tests use another package

The user-data audit finds real side effects in instrumentation startup and some
test bodies. Normal application startup applies preference migrations and the
configured history-retention policy, prunes old hidden-thread/user-post records
and cleans cached posts. The history value zero is the existing **App Start**
option, not an invented "never prune" setting. Some navigation/posting/work tests
also write real stores. Therefore the updated personal beta is not opened or
used as the instrumentation target.

The owner separately authorizes an isolated copy. A local ignored Gradle init
script changes only the debug package suffix and launcher label/manifest overlay:

- App: `com.emogoth.android.phone.mimi.validation29`, launcher label
  **Mimi 8.0 Test (Android 10)**, a separate UID (`10154`).
- Runner: `com.emogoth.android.phone.mimi.validation29.test/androidx.test.runner.AndroidJUnitRunner`;
  its declared target is verified before execution.
- Same application/test source, SDK baseline and bundled libraries; debug code,
  not R8-minified production code. No existing preferences/database are imported.
- App APK SHA-256:
  `b9eaeb1099adafb0878120082067225090d70710f33800ac9a0d20128a0b756e`.
- Test APK SHA-256:
  `7fd42763923f8cd45aea62812e1d8d7c8e55c088f29abc5d4db4b0427baa83d0`.

The ignored evidence folder is
`build/android10-validation/2026-10-25-9a81f51/`. It contains the init script,
overlay, copied APKs, runner reports and screenshots of synthetic test UI. Only
installed compiled APKs were pulled for certificate inspection, not private
databases. Normal build outputs are restored afterward without the init script;
`verifyApplicationVariants` again confirms the original `.beta`/release IDs.
The normal debug artifacts are not left disguised as the isolated copy.

## Executions and actual results

Reports are separate; a filtered run does not erase the interrupted full run.
Counts include opt-in assumption skips. The 91-method suite includes the six
native cases; do not add those six again when reporting distinct suite coverage.

| Execution | Result | Evidence |
| --- | --- | --- |
| Unmodified full suite (91 planned) | 12 started: 10 passed, one failed, one hung; remaining cases not reached | `full-suite.txt` |
| All classes except `PostingFlowTest` (84 methods) | **81 passed, one failed, two opt-in skips**, completed in about 169 seconds | `non-posting-suite.txt` |
| Original-provider Flash requirement check, explicitly enabled | **1 passed**: unsupported provider is explained correctly | `legacy-flash-fallback.txt` |
| Two detached posting-protocol cases plus the unenabled live-CAPTCHA case | **2 passed, one opt-in skip** | `posting-protocol-only.txt` |

### What passes on physical ARMv7

The completed 84-case block covers startup routing, legacy/main tabs and
restoration, Back, system-bar geometry, themes/dialogs, filters, settings/color
editor, local documents (including current 8.0 and retained historical notes),
Room/preference migration fixtures, cache preservation boundaries, WorkManager
fixtures, Commons Lang consumers, image loading, media/autoplay and thread
fast-scrolling. **All six native PDF/GIF cases pass**, including PDF render/zoom/
recreation/error/retry and GIF decoding/loop/pause/release. The migration fixtures
are not an in-place migration of the user's original 6.1.2 database.

In the interrupted run, posting in-flight rotation/duplicate protection and
draft/attachment restoration pass. Separate protocol checks validate manual
answer/expiry parsing and exact-host cookie/header boundaries. The live CAPTCHA
case remains disabled: no challenge is solved automatically and no public post
is sent.

### Findings from 25 October, before the CAPTCHA correction

1. **CAPTCHA layout on the smaller screen.**
   `PostingFlowTest#captchaConfirmationRequiresACompletedAnswerAndCanReloadOrCancel`
   fails the existing 160 dp visible-WebView minimum. A later screenshot of the
   nested posting/CAPTCHA fixture also shows clipped native dialog buttons and
   too little room for the challenge. Default font scale rules out a user font
   enlargement as the explanation. No assertion is relaxed or removed.
2. **Nested posting test fails to reach UI idle.**
   `PostingFlowTest#confirmingCaptchaSubmitsTheActualDraftOnceAndKeepsARejectedDraft`
   remains stuck in fixture readiness. Scoped JDWP stacks show the test thread
   waiting in `ActivityScenario.onActivity` / `Instrumentation.waitForIdleSync`,
   while the main thread is dispatching window insets/layout. This is diagnostic
   evidence, not proof of the final root cause or a claim that the server rejected
   a real post. After preserving stacks/screenshot, only the isolated process is
   force-stopped. The resulting runner `Process crashed` is an **intentional
   diagnostic interruption**, not a spontaneous startup crash. The next run
   explicitly excludes the posting class to test the remaining features; it is
   not presented as a successful full-suite rerun.
3. **Ruffle cannot execute with WebView 74.**
   `GalleryPlaybackTest#bundledRuffleLoadsAFlashMovieInAndroidWebView` reports
   `UNSUPPORTED_WEBVIEW`, with the instruction to update Android System WebView.
   The separate `originalWebViewHandlesFlashOrExplainsItsRequirement` case passes
   on the unchanged provider. This proves fallback/error reporting, **not SWF
   playback**. No pinned x86 emulator provider is installed on this ARM phone.

## CAPTCHA correction on 26 October

The preceding failures are retained as baseline evidence. The corrective change
keeps the existing 160 dp assertion and nested posting pipeline, rather than
replacing `ActivityScenario.onActivity` with a shortcut around UI-idle checks.

- The posting form used a `wrap_content` **window**, overriding Material's
  full-size bottom-sheet host. Restoring a full-size host lets the sheet itself
  determine the visible form height; the previously hanging nested pipeline
  completes, sends one intercepted local request and retains the rejected draft.
- The custom CAPTCHA body no longer imposes a display-percentage minimum or a
  zero-height weighted WebView. A finite 240 dp WebView sits inside a scrollable
  body; its native actions remain outside that body.
- The Material action panel can reserve only a partial stacked button bar.
  Reserve the entire measured bar and request its second measurement outside the
  current layout pass, **only when that minimum changes**. Remove the listener
  when stopped. Reduced vertical background insets also avoid carrying the
  tall-screen 80 dp top/bottom margins into an IME-resized window.
- The composer drops stale keyboard requests while the CAPTCHA owns focus. The
  CAPTCHA permits text entry and IME resizing without opening a keyboard itself.
  A keyboard-only guard was tested first and did **not** cure the original hang;
  it is not reported as the complete fix.

The layout investigation uses the actual cached Material 1.14.0/AppCompat 1.8.0
resources and checks the [bottom-sheet window contract](https://github.com/material-components/material-components-android/blob/1.14.0/lib/java/com/google/android/material/bottomsheet/BottomSheetDialog.java)
and [dialog panel measurement](https://github.com/androidx/androidx/blob/androidx-main/appcompat/appcompat/src/main/java/androidx/appcompat/widget/AlertDialogLayout.java).

Regression assertions check full button rectangles, clipping and screen-visible
window bounds, not just `isShown` or programmatic clickability. They also cover
the browser fallback's reachability in a 380 dp window, recreation, preserved
draft/cancellation and the absence of automatic posting.

The additional `postingKeyboard=true` instrumentation option explicitly enables
a real-IME/landscape case. It taps a **synthetic local input**, verifies that an
IME actually occupies screen space and that all actions remain visible, rotates
the activity to landscape, checks actions and browser reachability, and restores
the original requested orientation/hides the keyboard. It never solves a site
challenge or sends a public post. An emulator's configured hardware keyboard can
intentionally suppress its software IME, so this interaction is opt-in; the
deterministic short-window/recreation regression always runs.

Evidence is kept separately under
`build/android10-validation/2026-10-26-captcha-fix/`. Earlier failed experiments
are not overwritten. Early runs with the screen off/locked hit saved-state errors;
those environmental failures are not hidden by allowing fragment state loss. The
owner later authorizes dismissing this phone's **unsecured** lock screen. No PIN,
password, security setting or WebView provider is changed.

The completed follow-up full run, `final-full-suite.txt`, executes **93 methods**
without excluding a class: **90 pass, one fails, two opt-in skips**, in about
190 seconds. Both `postingKeyboard=true` and `legacyFlashWebView=true` are
explicitly enabled. All eight offline/keyboard posting cases pass, including
the formerly hanging pipeline and both new regressions. All six native cases
pass again. The remaining failure is **only** actual bundled Ruffle playback on
the unchanged WebView 74 (`UNSUPPORTED_WEBVIEW`); the original-provider
explanation case passes. Live CAPTCHA solving and real-board attachment smoke
tests remain unenabled. This is **not a green full suite**, and the playback
assertion is not removed, bypassed or changed to accept an error as playback.

After adding an explicit wait for the short window's actual layout (rather than
depending on emulator/device speed), `confirmation-full-suite.txt` repeats all
**93 methods** against the final code and bundled notes: **90 pass, the same one
unsupported-WebView playback failure, two skips**, in about **182 seconds**.
All eight non-live posting cases and all six native cases pass again. The copied
isolated app/test APK SHA-256 values are respectively:

- `94e263703ec78ef4596d8cd294cd1931ac2f1e0df5d28985893d088540856d48`
- `ba8d0f14830785876b7c83f2bbe222a10f220357ab9f205bf693b007967cbf98`

Their package/runner identities remain `.validation29`/`.validation29.test`.
The app verifies with v1/v2 signatures and the same public test certificate;
this is not approval for public distribution with that key.

Local host checks pass **128 unit tests** (109 app + 19 library), all **104**
offline CI-helper tests, debug lint (zero errors, 977 warnings, one hint), SBOM
freshness/negative validation, standard variant guards, debug/test APKs and
minified release APK/AAB generation. Static native ELF/ZIP/JNI and SDK 16 KB
zipalign checks pass for 24 libraries/four ABIs; these are not a physical 16 KB
runtime result or minified-device acceptance. Ordinary `.beta` build outputs
are restored without the local isolation init script afterward. The dependency
inventory, version, minimum SDK and production signing are unchanged.

Preservation evidence for **26 October** is distinct from the prior beta update:
the first `run-as` preflight already reports the personal `.beta` package absent,
and the final package inventory confirms its absence. Consequently **no new beta
database-hash comparison is claimed** for this session; the six-file comparison
above belongs to 25 October. This session updates only `.validation29` and its
test APK, never installs/uninstalls the personal beta or original release. The
original 6.1.2 version/install timestamps and empty `Mimidown` folder remain as
recorded; no user media is removed, no WebView is replaced, and stay-awake returns
to **0**. No public message is posted.

## Cold start and cleanup

The isolated copy cold-starts into `TabsActivity` successfully (about 8.3 seconds).
The first screenshot shows the loading screen with toolbar below the status
bar, not overlapping it. Scoped logs subsequently confirm 77 boards and a
successful boards fetch; this is not comprehensive manual browsing acceptance.

Each automated block captures and restores the original stay-awake value **0**.
The debugger resumes all threads and its local forwarding port is removed.
Device screenshot temporaries owned by this task are removed after pulling;
no user's media is deleted. No root access is acquired. A root-only native stack
command is denied, so diagnostics use the debuggable isolated copy's JDWP instead.
The original Windows `jdb -attach` transport fails; explicit socket transport
works. Reports from PowerShell are UTF-16; that host encoding is not an app error.

## Next acceptance step

The corrected regressions subsequently pass on the Edge 20 / Android 12:
[93 methods, 91 passes and two opt-in skips](ANDROID12_VALIDATION.md), including
actual fixture SWF playback on its unchanged modern WebView. This does not turn
the Android 10 WebView-74 playback failure into a pass. Next confirm remote CI,
agree public signing/update policy and continue acceptance of the exact signed
minified 8.0 APK. The 25 October block was diagnostic-only; the 26 October
follow-up includes production fixes.
WebView updating requires a separate owner decision through a supported channel;
actual SWF rendering still needs that acceptance. Human keyboard/browser-return,
real CAPTCHA interaction, SAF destination choice and certificate-compatible
minified release/update checks remain pending. Do not infer them from debug
fixtures, the earlier Android 12 reply or these partial results.
