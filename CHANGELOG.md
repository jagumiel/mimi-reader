# Changelog

This independent continuation starts from the inherited 7.0.1 code in Blatzar's fork (commit `fc1e4cd`). The 8.0 entry summarizes the modernization since that baseline, not the earlier application's entire history. Historical 7.0.1 and 7.0.0 notes remain available inside the app.

## [8.0] - Unreleased

Version code: `231`. Release preparation only: no public release date, tag, distribution signing key or publication is implied.

### Added

- MP4 playback through AndroidX Media3 alongside WebM, with lifecycle-aware playback, saving and sharing.
- Optional gallery autoplay with a visible play/pause toolbar action: images advance after three seconds, videos at the end, and animated GIFs after one cycle. Autoplay stops at the last item.
- Bundled Ruffle-based SWF playback with local format cards for Flash-board entries that have no server thumbnail. Cards identify SWF files; they are not rendered movie previews.
- Persistent All, SFW and NSFW board filters, theme-based sorting and safety labels from the site's metadata. The bundled catalog includes all 77 boards present in the October 2026 synchronization, including /r/ and /trash/. These classify boards, not individual posts.
- Recoverable loading states, refresh actions and direct retry controls for failed tabs, while keeping already loaded content visible.
- Confirmed, background cache clearing with reclaimed-space reporting, without deleting saved downloads, history, bookmarks, filters or preferences.
- A manually solvable CAPTCHA dialog with explicit completion and sending, challenge renewal, cancellation and a browser fallback.

### Changed

- Main tabs and the thread pager use ViewPager2 with stable identities and state restoration. Sessions are limited to 12 tabs, closing the oldest inactive thread with a notice instead of exhausting memory.
- Gallery preloading keeps the whole media list queued, limits concurrent downloads to two and prioritizes nearby items; the next item wins distance ties.
- Manually muting or unmuting a video carries that choice to subsequent videos in the current gallery and survives gallery recreation. Videos start muted by default.
- Batch downloads run through persistent foreground WorkManager jobs with progress, cancellation, network constraints, exponential retries and recovery after process death.
- Saving and folder selection use Android's Storage Access Framework and content URIs instead of broad storage permissions. Sharing uses package-specific FileProvider authorities.
- Back navigation uses lifecycle-aware AndroidX callbacks, with predictive-back integration where supported and preserved drawer, tab, reply, gallery-grid and split-pane behavior.
- System-bar insets, light/dark contrast, rotation and state restoration are handled across tabbed and legacy layouts, settings, filters and the color editor.
- There is one free application: paid/Amazon variants, residual interleaved-gallery ad insertion, donation entries and obsolete store-rating actions have been removed. This does not attribute the earlier removal of advertising services to this fork.
- More Info now links to this independent repository and verified credits. The original website is no longer operational and Mimi is no longer available on Google Play.

### Fixed

- The startup crash on Android 12 caused by legacy background-work initialization.
- Blank or unstable screens after opening many threads, duplicate fragments after recreation and damaged or oversized saved tab sessions.
- Download integrity: partial files are finalized only after size checks, truncated files are replaced during recovery, and failed-download cleanup completes before terminal notifications.
- Cross-board catalog cache collisions and refresh failures that previously discarded usable board, catalog or thread content.
- Malformed or partial API/media responses, missing optional fields and inconsistent error handling for HTTP failures, timeouts, connectivity and TLS errors. Read-only API requests are paced separately from media transfers.
- Flash-board URL construction: SWF files use their encoded original filename, not the numeric media timestamp. HTTP errors and invalid SWF signatures no longer reach the player as fake movies; missing files show an explicit error and retry action.
- SWF control layout, load/error reporting and pause/resume behavior across page changes and activity backgrounding.
- Clarified CAPTCHA completion versus explicit sending: Done completes verification, Get Captcha requests another challenge, and the native Send message action uses the completed verification. Only a confirmed server receipt counts as success.
- Posting failures and CAPTCHA expiry preserve the draft and attachment; rotation retains one in-flight attempt, and uncertain delivery after process death does not trigger an automatic duplicate post.
- PDF viewport positioning, media cleanup races, variable-height thread fast-scrolling and image/cache lifecycle regressions found during modernization.

### Security

- SWF execution is isolated in a WebView using a private HTTPS origin, bundled runtime assets, blocked external requests/navigation and disabled file access. Arbitrary SWF compatibility is not guaranteed.
- CAPTCHA handling restricts navigation/resources, has no native JavaScript bridge, and avoids logging answers, tokens, session cookies or credentials. External challenge providers still make their own network connections.
- Removed the Stetho debugging integration and HTTP-header logging, tightened release R8 rules and updated the bundled privacy information to match actual storage and connections.
- Upgraded Commons Lang from 3.11 to 3.20.0 to address CVE-2025-48924 while preserving quote-parsing behavior. The incompatible supplementary-digit behavior of 3.21.0 is not adopted.
- Added a versioned CycloneDX release SBOM, dependency inventory and bundled-component hashes. GitHub Actions scans for CVE/GHSA findings, blocks unexcepted HIGH/CRITICAL findings, publishes SARIF and performs scheduled rescans with expiring, scoped exceptions.

### Internal modernization and verification

- Modernized the build to JDK 17, Android SDK/target 36, Android Gradle Plugin 8.13.2 and Kotlin 2.3.21. Gradle remains 8.13; its next update is deferred to 8.0.1.
- Updated Room, Lifecycle, AndroidX UI, Material Components, Glide, Gson, Retrofit, OkHttp and Jsoup; replaced Novoda Custom Tabs, PercentLayout, legacy fast-scrolling and obsolete PDF/GIF native integrations. Removed verified unused dependencies, processors, interfaces, layouts, settings arrays and theme resources without removing active preference keys.
- Versioned preferences and moved database writes off the main thread. Room migration tests cover 22 to 23 and a reconstructed 21 to 22 to 23 path, preserving persistent user data.
- Added API, HTTP, HTML, media, posting, migration, navigation, settings, cache and lifecycle regression tests. GitHub Actions verifies build/lint/unit tests and instrumented Android 10, 12, 14 and 16 jobs, plus a dedicated native-media job with real 16 KB pages.
- Release builds support local signing configuration or unsigned minified APK/AAB generation, configurable versions, checksums, archived R8 mappings and ELF/APK/JNI alignment checks. SBOM metadata uses the same version properties as the app.

### Compatibility and release status

- Minimum Android remains 6.0 (API 23); target/compile SDK is 36. ARMv7, ARM64, x86 and x86_64 are packaged; obsolete MIPS/MIPS64 native binaries are no longer included. The emulator matrix and static checks do not replace physical-device acceptance.
- Android 12 has physical regression and historical authorized text-reply evidence. The internal 7.0.1/230 minified test APK also passed fresh installation and cold start, preserving all 607 files in the owner's existing Pictures/Mimi folder. This is not acceptance of the new 8.0/231 artifacts or an in-place update.
- Final physical Android 10/32-bit validation is deferred by the owner, not marked passed. Visual/manual release checks and certificate-compatible update/migration acceptance remain pending; a real pre-v22 database backup is still unavailable.
- SWF support is partial: missing/purged CDN files can return HTTP 404 even while listed, and Ruffle may not support every movie, ActionScript feature or interaction. Live Android 10 and broader ActionScript 3/touch coverage remain pending.
- The PDF viewer has no gallery password-entry flow for encrypted documents; do not assume every PDF is supported.
- CAPTCHA acceptance depends on the site's providers, WebView, device and network. A real text reply was verified on Android 12; real attachment/new-thread publication and the remaining manual keyboard/browser-return checks are not claimed as validated. An external browser does not automatically transfer its session into the native form.
- Vulnerability scans report known findings at scan time, not proof of safety. Native/WASM provenance, generic/GitHub components and build tooling have explicit coverage limits.
- Preserve existing app data and saved media. Updates require a compatible signing certificate; the internal public test key is not a distribution identity. No uninstall/data-clear workaround is part of this release preparation.
- Version upgrades retain inherited maintenance that clears media cache and normal/archived cached post tables. This is different from manual cache clearing: do not expect every offline post to survive an update. The user's saved Pictures/Mimi media folder is not a cache-cleaning target.
- Expanded watched-thread and quote notifications are post-8.0 proposals, not new features delivered by this release. The README explains release APK installation, safe updates and building from source.

### Credits and license

- Original application: [Eli Connelly (@econnelly)](https://github.com/econnelly), with historical commits under [@MimiReader](https://github.com/MimiReader).
- Immediate upstream fork: [@Blatzar](https://github.com/Blatzar). Current independent maintenance and modernization: [@jagumiel](https://github.com/jagumiel).
- The GitHub fork-network source [@maestro12](https://github.com/maestro12) is not evidence of original application authorship. This continuation is not an endorsed upstream release.
- The application remains Apache License 2.0. Original copyright notices and third-party licenses are preserved; bundled components retain their own licenses.
