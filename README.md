<img src="mimi-app/src/main/ic_launcher-web.png" alt="Mimi Reader clover icon" width="96" />

# Mimi Reader

A free, open-source Android app for browsing 4chan: find a board, open threads in tabs, swipe through images and videos, save media, and write replies with a manually solved CAPTCHA.

[![Android CI](https://github.com/jagumiel/mimi-reader/actions/workflows/android-ci.yml/badge.svg)](https://github.com/jagumiel/mimi-reader/actions/workflows/android-ci.yml)
[![SBOM security](https://github.com/jagumiel/mimi-reader/actions/workflows/sbom-security.yml/badge.svg)](https://github.com/jagumiel/mimi-reader/actions/workflows/sbom-security.yml)

[Download / Releases](https://github.com/jagumiel/mimi-reader/releases) · [Installation guide](#download-and-install) · [What's new in 8.0](CHANGELOG.md) · [Build from source](#build-from-source)

**8.0 is being prepared and is not published yet.** Once released, its signed APK will be available under **Assets** on the Releases page above. If there is no installable APK there yet, please wait for the release; a source ZIP or an unsigned CI artifact is not a substitute.

This is an independent continuation of Mimi Reader, with one free edition and no interleaved gallery ads. Mimi is **no longer available on Google Play**, and the original project website is no longer operational. Use this repository for this fork's downloads and documentation; it is not an official 4chan app or an endorsed upstream release.

## What can I do with Mimi?

- **Find boards:** browse by topic, sort the catalog, and filter boards by All, SFW or NSFW. The bundled catalog includes the 77 boards synchronized in October 2026, including `/r/` and `/trash/`. These labels classify boards, not every post within them.
- **Follow the conversation:** open multiple threads in tabs, bookmark them, revisit history, and refresh a tab after a loading error. Sessions are limited to 12 tabs to bound memory use.
- **Browse media:** swipe through images, animated GIFs, MP4 and WebM videos, and supported PDFs. Video mute/unmute carries over to the next videos in the current gallery.
- **Use autoplay:** tap the gallery's play/pause action to advance images after three seconds, videos when they finish, and GIFs after one loop. Autoplay is optional and stops at the last item.
- **Save what you choose:** pick a folder with Android's file picker and download individual files or batches, with progress, cancellation and recovery. Clear disposable app cache without deleting your saved downloads.
- **Participate:** compose posts and replies and solve the site's CAPTCHA yourself. Verification, posting limits and board rules are controlled by the site.
- **Try Flash content:** open supported SWF movies using the bundled Ruffle player. SWF cards identify the format; they are not movie previews, and some files are unavailable or unsupported.

4chan includes adult and other potentially sensitive user-generated content. A board's SFW label is not a guarantee about everything it contains.

## Download and install

You only need your Android phone and the release APK. Normal APK installation does not require a computer, root access or USB debugging.

### Requirements

- **Android 6.0 or later** (API 23 minimum). Android 10 and 12 remain within the intended compatibility range. Physical debug checks cover the corrected CAPTCHA layout, keyboard and rotation on both versions; final 8.0 minified-release acceptance is not complete. See the [Android 10](docs/ANDROID10_VALIDATION.md) and [Android 12](docs/ANDROID12_VALIDATION.md) reports.
- An internet connection to load boards, media and CAPTCHA challenges. Keep Android System WebView or your device's active WebView provider updated through its supported update channel.

### Install the release APK

1. On your phone, open [this repository's Releases page](https://github.com/jagumiel/mimi-reader/releases).
2. Choose the release you want, read its notes, expand **Assets**, and download its installable **`.apk`** file. Do not choose **Source code**, an **`.aab`**, or a file marked **unsigned**. Use the release's checksum if provided to verify the download.
3. Open the downloaded APK from your browser's downloads or your **Files / Downloads** app.
4. If Android blocks installation from that source, tap **Settings** in the prompt and enable **Allow from this source** for the app opening the APK, such as your browser or Files app. Return to the installer, tap **Install**, then **Open**.
5. After installing, you can turn that source's permission off again under **Settings → Apps → Special app access → Install unknown apps**. You may need to enable it again when installing a later update.

These are the usual steps on **Android 8 and later, including Android 10 and 12**; menu names vary by manufacturer. On Android 6–7, the equivalent is generally **Settings → Security → Unknown sources**; allow a single installation if offered and turn the setting off afterward. See [Android's installation guidance](https://developer.android.com/distribute/marketing-tools/alternative-distribution#unknown-sources).

“Unknown source” means the APK comes from outside your usual app store. It does **not** mean the APK can be unsigned: [Android requires a digital signature](https://developer.android.com/studio/publish/app-signing) to install or update an APK. A project-signed APK does not need Google Play distribution, but a signature is not a guarantee that an app is safe. Our unsigned build/CI outputs are for verification and later signing, **not installation**.

If Android or Play Protect blocks the file, read the warning and verify the download's origin; do not disable device-wide protections just to force installation. Managed devices, manufacturer policies and [Android developer-verification requirements](https://developer.android.com/developer-verification) may impose additional restrictions. Developer verification is separate from the APK's cryptographic signature.

### Already have Mimi installed?

Download the new release APK and open it as above. Android can update an existing app only with a compatible package identity and signing certificate, and an acceptable version code.

**Do not uninstall the old app, clear its storage, or delete your `Mimi` / `Pictures/Mimi` media folder to fix an installation error.** Back up important data and saved media first. An original upstream installation or an internal test build may use a different certificate from this fork's eventual public release; a direct update is not guaranteed. Keep the old installation and consult the release notes rather than forcing replacement. There is no automatic cross-signature data migration promised here.

The developer build uses a separate `.beta` package and does not automatically share the release app's bookmarks or preferences. Version upgrades may also clear cached posts/media as maintenance; do not rely on the cache as a permanent offline archive.

### First steps

1. Open the board list, choose a topic or safety filter, then tap a board and a thread.
2. Tap an attachment to open the gallery. Swipe between items; use the toolbar for autoplay, saving or sharing. Unmute a video when you want sound.
3. Choose your media destination in the gallery settings when saving files. Android's folder picker grants access to the folder you select, not your entire storage.
4. To reply, write your message, complete the CAPTCHA, then use the native **Send message** button. **Done** inside the challenge completes verification; **Get Captcha** requests a new challenge. Sending is an explicit action that can publish your post.

## If something does not work

- **“App not installed” or an update conflict:** check that you selected the installable release APK, not an unsigned artifact or source archive. A different signing certificate or an older version code can prevent updating. Keep your existing installation and data.
- **An SWF is listed but will not open:** its CDN file may return HTTP 404, or Ruffle may not support it. A retry cannot restore a file removed from the server. Encrypted PDFs also have no gallery password-entry flow.
- **CAPTCHA is unavailable or rejected:** update the active WebView provider, check connectivity, allow any site-imposed wait, then request a fresh challenge. External browser sessions do not automatically transfer to the native form. Some device/network/provider combinations remain unvalidated.
- **A tab cannot refresh:** use its retry/refresh action; already loaded content may remain visible while the network is unavailable.

See the [changelog's compatibility notes](CHANGELOG.md#compatibility-and-release-status) and [release acceptance checklist](docs/RELEASE_ACCEPTANCE.md) for known limits. Historical Android 12 tests are not a claim that every 8.0 release interaction or device has passed. Additional watched-thread/quote notifications are planned after 8.0, not newly delivered features of this release.

## Build from source

This section is for developers. **You do not need to build the app to install a published release APK.**

Use **JDK 17**, the **Android SDK platform 36** and **Build Tools 36.0.0**. The repository includes its Gradle wrapper; no separate Gradle installation is needed. Let Android Studio configure your SDK location, or set `sdk.dir` in your own ignored `local.properties`. Do not copy another developer's machine-specific paths.

```sh
git clone https://github.com/jagumiel/mimi-reader.git
cd mimi-reader
```

On Windows (PowerShell):

```powershell
.\gradlew.bat :mimi-app:assembleDebug
```

On Linux/macOS:

```sh
./gradlew :mimi-app:assembleDebug
```

The debug APK is written to `mimi-app/build/outputs/apk/debug/mimi-app-debug.apk`. It is automatically test-signed and uses `com.emogoth.android.phone.mimi.beta`; it is not a public release. Release uses `com.emogoth.android.phone.mimi`. The tracked legacy test key is public and must not become the distribution identity.

For local verification, run the following with `./gradlew` (or `.\gradlew.bat` on Windows):

```sh
./gradlew verifySbom testSbomValidation :mimi-app:verifyApplicationVariants :mimi-app:testDebugUnitTest :chanlib:testDebugUnitTest :mimi-app:lintDebug
python3 -m unittest discover -s scripts/ci -p 'test_*.py'
```

The helper tests need Python 3; on Windows its command may be `python`. Device/emulator tests and native 16 KB requirements are documented in the [test matrix](docs/TEST_MATRIX.md). See the [release guide](docs/RELEASING.md) before building/signing distributable artifacts; without private signing configuration, release outputs are unsigned.

## Project documentation

- [Changelog](CHANGELOG.md): modernization since the inherited 7.0.1 baseline.
- [Roadmap](roadmap-2026.md): current status and future work (maintained in Spanish).
- [Release guide](docs/RELEASING.md) and [physical acceptance](docs/RELEASE_ACCEPTANCE.md).
- [SBOM](docs/SBOM.md), [dependency audit](docs/DEPENDENCIES.md) and [vulnerability-scanning policy](docs/VULNERABILITY_SCANNING.md). A scan without findings is not a security certification.
- [Posting/CAPTCHA boundaries](docs/POSTING.md) and [bundled privacy policy](mimi-app/src/main/res/raw/privacypolicy.html): external site/provider connections still apply.
- [Project history and credits](docs/PROJECT.md): authorship, fork lineage and independent-project status. Use the repository for project information; do not assume an old website, email address or unavailable issue tracker is a current support channel.

## Credits and license

Original application by [Eli Connelly (@econnelly)](https://github.com/econnelly), with historical commits under [@MimiReader](https://github.com/MimiReader). This continuation builds on [@Blatzar's fork](https://github.com/Blatzar/mimi-reader) and is maintained by [@jagumiel](https://github.com/jagumiel).

Mimi Reader is licensed under [Apache License 2.0](LICENSE). Original copyright notices are preserved. Third-party libraries and bundled components, including Ruffle, retain their own licenses; see the [bundled notices](mimi-app/src/main/res/raw/licenses.html) and [SBOM](docs/SBOM.md).
