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
