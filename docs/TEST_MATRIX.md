# Android test matrix

Mimi Reader keeps `minSdk 23`, but the supported release baseline is exercised on four representative Android versions. GitHub Actions runs every instrumented test on each API level in parallel and preserves the HTML report, JUnit result, device information, and per-test logcat output for 14 days.

| Android | API | Purpose |
| --- | ---: | --- |
| 10 | 29 | Oldest device generation actively used by the project and legacy storage/lifecycle behaviour. |
| 12 | 31 | Regression target for startup, exported components, notifications, tabs, and the Motorola Edge 20. |
| 14 | 34 | Modern permissions, foreground work, system bars, and activity restoration. |
| 16 | 36 | Current compile/target SDK behaviour and forward compatibility. |

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
