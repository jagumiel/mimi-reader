# Bundled document viewers

## Scope

Privacy, third-party licenses and the historical changelog share
`prefs.LocalDocumentFragment`. The two `LicensesFragment` and two
`PrivacyPolicyFragment` source entry points delegate to it, retaining existing
factory methods, default constructors and the `raw_res_extra` / `title_extra`
argument keys. The board's automatic changelog and More Info use the same reader.
The live legacy license class remains in the release mapping. The unused legacy
privacy wrapper is still shrinkable; its debug restoration test is not evidence
that an otherwise unreferenced class is retained in a minified release.

This changes rendering and resource lifetime, not document contents, licensing,
saved user preferences, databases or the application version. Original Eli
Connelly and Adam Speakman attribution is preserved. No 8.0 changelog is written.

## Loading, rendering and cleanup

- Read bundled raw resources on the existing Rx I/O scheduler, using UTF-8 and
  try-with-resources. Capture Resources before asynchronous work, not an Activity.
- Dispose the load at `onDestroyView`, reject stale generations, detach and
  destroy the WebView, then release view references. There are no global WebView
  timer, cookie, storage or cache resets. Follow Android's
  [WebView destruction contract](https://developer.android.com/reference/android/webkit/WebView#destroy()).
- Serve the prepared HTML at one exact appassets HTTPS URL with an intercepted
  200 response, UTF-8, no-store and a restrictive CSP. Block all other resource
  requests. JavaScript, DOM storage, file/content access, mixed content and
  automatic extra windows remain disabled; network loading is also blocked.
- A first `loadDataWithBaseURL` implementation rendered a blank page on the
  Edge 20's newer provider despite view size and completion signals. A diagnostic
  screenshot confirmed the blank page. Use the actual intercepted HTML response;
  tests require visible geometry and native text search in the rendered document,
  not merely a shown dialog or finished load. Do not enable JavaScript to make a
  text assertion pass. The diagnostic screenshot/evaluation code is not shipped.
- Only user-initiated main-frame HTTP(S) links with a host and no user-info may
  open the external browser. Keep fragment links local and block automatic,
  subframe, file/content/data/javascript/intent/mailto navigation. Reuse browser
  error handling without loading external pages in the document WebView.
  [Android URI-loading guidance](https://developer.android.com/privacy-and-security/risks/unsafe-uri-loading)
  explains why scheme and host need separate checks.
- Resource errors show a Retry action. A renderer exit discards the affected
  WebView and shows the same error state; explicit Retry creates another instance.
  Do not reuse a dead renderer. See
  [Android's renderer-exit contract](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail)).

## Cache cleanup

`AppCacheCleaner` still clears media, Glide disk data and temporary files, and
evicts HTTP responses through the existing client. Preserve the live HTTP-cache
directory and internal `WebView` / `WebView_*` infrastructure; never recursively
delete a provider's active files behind its back. This does not clear cookies,
CAPTCHA state or a browser profile as a side effect of clearing media cache.

The existing cache test now checks both provider directory forms with owned
markers, without deleting the real profile in cleanup. Another test opens a
real document, clears media cache off the UI thread, recreates the activity and
requires the document's actual text to render again.

## CI run 34 and the pinned Android 10 provider

[Run 34 for `f5afe2f`](https://github.com/jagumiel/mimi-reader/actions/runs/39922242383)
passed five jobs; only Android 10 failed. `bundledRuffleLoadsAFlashMovieInAndroidWebView`
reported a stopped renderer. The artifact shows a preceding native `fdsan`
descriptor-ownership error during document recreation and a later missing
`cache/WebView/Crashpad` directory after the old cache cleaner ran. Do not classify
this as an MP4 failure, a unit-test failure or a reason to skip SWF coverage.

An unmodified baseline on a local Google APIs API 29 emulator with the identical
hash-verified WebView 128 reproduced native descriptor failures; its tombstone
reaches `libbinder_ndk.so` / `AParcel_writeParcelFileDescriptor` from Chromium.
An early local full-baseline attempt also had an unsuitable overridden display
configuration and was interrupted; it is not a valid passing baseline. Correct
the emulator's native geometry to 1080x1920 / 420dpi rather than claiming that
its window/surface errors are application regressions.

The pinned provider's
[128 feature declaration](https://github.com/chromium/chromium/blob/128.0.6613.88/mojo/core/embedder/features.cc)
defaults `MojoUseBinder` off, while its
[test variations configuration](https://github.com/chromium/chromium/blob/128.0.6613.88/testing/variations/fieldtrial_testing_config.json)
enables that experiment for WebView. The upstream project subsequently
[disabled the transport at compile time](https://chromium.googlesource.com/chromium/src/+/ac474eb6b13d96c8304abbc10c760110ca698ff3).
For this historical CI-only binary, select the nonexperimental transport with
`--disable-features=MojoUseBinder`; do not disable fdsan, sandboxing, rendering,
assertions or mandatory playback. APK version/hash, Ruffle and shipped app
configuration remain unchanged. This workaround does not certify the old test
provider's security or all possible platform failures.

The helper checks emulator serial, qemu, API 29, debuggability and exact active
provider before writing flags. It refuses pre-existing developer flags, verifies
the owned file, restarts only the test app and removes the file in `finally`,
including after failures. A short existing startup-error test runs first; its
native `WebViewCommandLine 'disable-features'` output must contain the exact
feature token before running the full suite. Capture that proof before a long
suite or per-test log collection can rotate startup logs. Preserve its JUnit/
logcat under `build/reports/legacy-webview/pinned-runtime`, already uploaded by CI.
The original-provider capability test still runs before installation. Android
12/14/16 and the physical phone do not receive these flags or a provider change.
See the pinned version's
[command-line documentation](https://github.com/chromium/chromium/blob/128.0.6613.88/android_webview/docs/commandline-flags.md).

## Tests and acceptance boundaries

Six new mandatory `LocalDocumentTest` cases cover actual HTML text and recreation
for all four source entry points and the custom changelog title, offline settings
and response CSP, allowed/blocked navigation and browser failures, bad-resource
Retry, simulated renderer-exit replacement, cancelled I/O / stale callbacks, and
cache clearing after a live WebView. Renderer recovery is simulated rather than
deliberately crashing the shared provider. Existing More Info tests remain.

The ordinary suite has **78 cases**, with the same three opt-in skips. The
dedicated real-16-KB job still has six native cases. CI-helper coverage is **54
tests**, including identity/provider guards, refused existing flags, exact native
proof, startup failure propagation, finally cleanup and Windows/Linux launchers.
The helper accepts no test retry as a substitute for a passing assertion.

### Final local acceptance, 23 October 2026

The complete real CI helper also succeeds end to end on the local Google APIs
API 29 emulator: original-provider capability (one case), pinned-provider startup
and native flags proof (one case), then the ordinary suite (**78 cases: 75 passed,
three existing opt-in skips, zero failures/errors**). The preflight report is
preserved and the owned flags file is verified absent afterward. This is not a
mocked orchestration result. The Edge 20 / Android 12 independently passes the
same full 78-case suite. Original WebView 74 also passes eleven scoped document/
cache cases; that scoped result is not full-suite acceptance.

Also pass: **110 app/library unit tests**, **54 CI-helper tests**, variant IDs,
SBOM freshness and six negative validation cases, debug lint (zero errors,
1,012 warnings and one hint), debug/test APKs, minified release APK/AAB,
actionlint, static ELF/ZIP alignment (24 native libraries / four ABIs), release
JNI seeds and SDK `zipalign -c -P 16 4`. Static guards do not prove a new actual
16 KB runtime pass. SBOM remains 152 components / 75 declarations, with no
dependency, R8-rule, Ruffle or bundled-document-content changes.

Compared with `f5afe2f`, the release APK grows from 41,425,051 to 41,426,571 bytes
and the AAB from 31,143,978 to 31,144,361 bytes. These sizes are not startup or
memory benchmarks. Restore and verify the phone's stay-awake setting to **0**;
restore the owned API 29 emulator's setting to **1** and shut down the emulator.
The SDK image and new reusable test AVD are retained; no personal-device WebView
provider or flags are changed.

Local Android 10 / Android 12 results are not the new commit's remote six-job
acceptance. Physical Android 10/32-bit media and human CAPTCHA/keyboard/browser
checks remain release gates; the CVE scan, owner-approved 8.0 changelog and final
README rewrite remain deferred. Continue the next bounded cleanup with verified
orphan resources/screens, never deleting files solely because they are old.
