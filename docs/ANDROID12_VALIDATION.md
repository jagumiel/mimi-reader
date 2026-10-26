# Physical Android 12 regression after the CAPTCHA correction

Validated on **26 October 2026**, using source **9898983**, application version
**8.0 / 231**. This accepts an isolated **debug** regression run, not the public
signed/minified release or an in-place migration of the owner's applications.

## Device and isolated artifacts

- ADB identifies the phone as **Motorola Edge 20** (`berlin`), Android 12 /
  API 31, with `arm64-v8a`, `armeabi-v7a` and `armeabi` support.
- Actual runtime page size is **4096 bytes**. This is not actual-16-KB evidence.
- The active provider remains `com.google.android.webview` **155.0.8059.43**;
  no provider, keyboard, security configuration or animation scale is replaced.
- The existing beta and original-package internal release remain **7.0.1 / 230**.
  Neither is installed over, uninstalled, cleared or used as the test target.
- A local ignored init script/manifest creates
  `com.emogoth.android.phone.mimi.validation31`, label
  **Mimi 8.0 Test (Android 12)**, separate UID **10549**. Its runner is
  `.validation31.test/androidx.test.runner.AndroidJUnitRunner`, with that isolated
  target verified before installation. The script changes only debug identity,
  label/manifest overlay and the version-name suffix, not application/test logic.
- App APK SHA-256:
  `eca321bf499f519427b4d3910b1e1677505d7ce73c32f398dd6a586bc1289181`.
- Test APK SHA-256:
  `c6fbbaa5382979e6552bcca9b412ebce81e613ed28e1e48df1d82a92955fa173`.
- The app signature verifies with the existing public test certificate, SHA-256
  `e98eedf04e4bba13131ecdd591d8b9bf8906066b9dc942de09105504856a899f`.
  This is not a distribution-key decision or permission to publish with that key.

The ignored evidence directory is
`build/android12-validation/2026-10-26-9898983/`: isolated init/manifest, copied
APKs, `isolated-build.txt`, `full-suite.txt` and `standard-restoration.txt`.
Normal build outputs are restored without the init script; the standard variant
guard again verifies `.beta`/release identities. No personal databases, cookies,
media filenames or their individual hashes are copied into the repository.

## Execution and results

Direct `am instrument` targets only the isolated runner, explicitly enabling
`postingKeyboard=true` and `legacyFlashWebView=true`. No test class is excluded
and no existing assertion is relaxed to obtain a pass.

**93 methods: 91 passed, two opt-in assumption skips, zero failures/errors**,
completed in about **92 seconds** (`OK (93 tests)`). Counts already include the
six native cases; do not add them again.

- All **eight non-live posting cases** pass: bounded/manual answer parsing and
  expiry, explicit readiness/reload/cancel, one intercepted multipart submission,
  rejected-draft retention, attachment/recreation preservation, in-flight
  duplicate protection and exact-host cookie/header boundaries.
- The new compact-window regression passes: the challenge's existing 160 dp
  assertion remains, native controls are fully visible inside the screen-visible
  window, the 380 dp window actually completes layout before checking controls,
  browser fallback remains reachable, and recreation/cancellation retain the draft.
- The explicit keyboard case taps a **synthetic local input**, verifies that an
  actual IME occupies screen space, checks actions above that boundary, rotates
  the activity to landscape and checks browser reachability. It hides the keyboard
  and restores requested orientation afterward. This is not real CAPTCHA-provider
  acceptance and does not solve a site challenge or publish a message.
- **Bundled Ruffle playback passes**, as does the explicit original-provider
  compatibility case. Both use a minimal local SWF fixture, not an arbitrary
  `/f/` movie. This contrasts with the unchanged WebView 74 limitation on the
  [Android 10 phone](ANDROID10_VALIDATION.md); that earlier failure is not erased.
- All **six native PDF/GIF cases** pass on ARM64/4-KB pages. The remaining run
  covers startup, tab restoration/Back, themes/settings/color editor, local
  documents, migrations, cache/WorkManager fixtures, media/autoplay and fast scroll.
- The two unchanged skips are the human live-CAPTCHA preview and real-board
  attachment smoke check. Neither is silently counted as validated live behavior.

The isolated debug/test builds and standard variant/output restoration pass.
All **104 offline CI-helper tests** also pass. The 128 unit tests, lint, release
APK/AAB, SBOM and native static checks recorded with the preceding correction
remain historical host evidence; they are not misreported as rerun in this
device-only documentation block. No dependency, version or build-chain change
is made here.

## Data preservation and cleanup

The personal beta was running at the initial inventory. Its **12 persistent
database/preferences/files entries are byte-identical** before and after the
isolated suite, using SHA-256 comparisons without exporting their contents.
All **607 files in `/storage/emulated/0/Pictures/Mimi`** retain their paths and
SHA-256 values, with no additions/removals. No folder rename is necessary.

Both existing app versions and installation/update timestamps remain unchanged.
The phone has a secure lock screen; when it locks during preparation, the owner
unlocks it manually before tests start. No attempt is made to bypass that lock.
Temporary stay-awake **7** is scoped to the device run and restored to its
captured value **0** in `finally`. The test copy remains installed separately;
no public post, personal-data clear, WebView replacement or distribution occurs.

## Next acceptance step

Confirm the new source's remote CI independently, then agree the **public signing
identity and update/transition policy** before producing a distribution candidate.
Acceptance must use the exact signed minified **8.0** APK on both phones and the
[release checklist](RELEASE_ACCEPTANCE.md). Debug fixtures do not fill those
release cells. Real provider interaction/browser return, selected-folder SAF
checks, real pre-v22 database evidence and Android 10 Flash on a supported WebView
remain distinct limitations or owner decisions. Gradle stays deferred to **8.0.1**.
