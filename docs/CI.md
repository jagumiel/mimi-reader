# Android CI and Flash playback

The workflow builds debug/release artifacts, validates the release SBOM, and
runs instrumentation on Android 10, 12, 14 and 16 (API 29/31/34/36). A passing
build job alone is not a passing workflow: every instrumentation job must pass.

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
mandatory offline device tests in `PostingFlowTest`. They exercise a local
TCaptcha fixture through the production document/resource interceptor, visible
dialog size, explicit confirmation/reload, draft and attachment restoration,
in-flight rotation/duplicate protection, and host-scoped cookies/headers. No CI
test posts public messages or solves real CAPTCHA challenges. A third opt-in
test, `livePostingCaptcha=true`, is a human-only widget preview with no posting
API attached; it stays skipped in the normal matrix. See [POSTING.md](POSTING.md).

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
