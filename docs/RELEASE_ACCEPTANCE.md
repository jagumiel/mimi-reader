# Physical release acceptance for the planned 8.0

Prepared on **25 October 2026**, using application source commit
`c3824a78f1aa712058c90f5b4d9b8754b2036aa5`. This is an internal validation package,
not the public 8.0 release. That archived build's identity is **7.0.1 /
versionCode 230**, `minSdk 23`, target/compile 36. The owner subsequently approved
English release notes and version **8.0 / 231**, now prepared separately in source
and marked **Unreleased**. None of this archived artifact's hashes or physical
results transfer to the new build. Public signing/publication remain separate
gates. Gradle remains deferred to application version 8.0.1.

## Automated baseline accepted for this source

- [Android CI 46](https://github.com/jagumiel/mimi-reader/actions/runs/40137617724):
  all six jobs pass, including API 29/31/34/36 and six native cases on the actual
  16 KB job. This accepts the Commons Lang update, not just the preceding CI.
- [SBOM security 2](https://github.com/jagumiel/mimi-reader/actions/runs/40137617737):
  both jobs pass; the log recognizes 148/148 Maven packages, reports zero known
  findings and completes SARIF processing. Native/WASM/generic/build-tool coverage
  limitations remain in [VULNERABILITY_SCANNING.md](VULNERABILITY_SCANNING.md).
- The local acceptance-preparation build passes release APK/AAB generation,
  SBOM freshness/schema/negative checks and variant guards. Static ELF/ZIP/JNI
  and SDK 16 KB zipalign pass (24 native libraries / four ABIs). A fresh scan of
  the packaged input SBOM again recognizes 148/148 Maven packages with no findings
  and no exceptions. No new application code or dependency changes are required.

## Artifact hand-off and signing gate

The ignored local folder
`build/release-validation/2026-10-25-c3824a7-c267a929/` holds copies of the
minified/resource-shrunk unsigned APK/AAB, exact R8 `mapping.txt`, release SBOM,
dependency inventory, manual component manifest, exception registry and scan/
native evidence. Keep this set together; do not mix a mapping or SBOM from a
later build. Other machines must prepare their own equivalent folder.

| File | SHA-256 before signing |
| --- | --- |
| `mimi-reader-c3824a7-validation-unsigned.apk` | `15d5102d19ba3d03e3a6eb66b2e3278bb0e2105112287d4dedec766809693c87` |
| `mimi-reader-c3824a7-validation-unsigned.aab` | `722d8371d10a6e495f8d2dc3db008e1e0ca16bd7d1af0f95cb750c672cab12d1` |
| `mapping.txt` | `1a9c52b440f9729d461bbd69cfa3d41e8c50e49913af7b3f289c220e28646896` |
| `mimi-reader-release.cdx.json` | `79dd5e37bb8661955018aa74f95a1823156734a04de3f2db23680aa708dbc316` |

Unsigned APKs cannot be installed. There is currently **no configured release
signing key**. The repository's tracked debug keystore is not a production
secret or a suitable public-release key. Ask the owner to choose either an
internal test-signed copy or a locally configured publication key before signing.
Do not print passwords, commit key material, generate an unapproved publication
identity, or silently sign with the debug key for distribution.

If an internal test-signed copy is authorized, preserve the unsigned original,
verify the resulting signature, certificate fingerprint, version/SDK/package
metadata and native alignment, and record its **new** checksum. Keep the release
package ID `com.emogoth.android.phone.mimi` and `debuggable=false`; this is still
the minified release code, not the normal unminified `.beta` build. The different
signing key means it cannot later receive an ordinary update signed with an
unrelated publication certificate. Explain this before installation.

For every target device, first inspect its serial/API/ABI and installed package/
certificate. Never uninstall or clear an existing app to resolve a signature
conflict. Use an in-place update only when its certificate/version are compatible;
a fresh test install does **not** validate migration from an existing release.
Do not replace `com.emogoth.android.phone.mimi.beta`, copy its private databases,
or assume that its preferences transfer to the release package. Preserve the
original stay-awake setting and restore it after any automated device work.

## Human acceptance checklist

Mark a case passed only after testing the **exact signed minified APK** and
recording the device/API/ABI, APK hash, certificate and observed result. Existing
debug instrumentation or emulator CI does not fill the physical-device cells.

| Case | Android 10 physical / 32-bit | Android 12 physical |
| --- | --- | --- |
| Safe install/update, cold start, main menu and system-bar insets | Pending | Partial: fresh install and cold start pass; update and visual acceptance pending |
| Open several threads, switch/close tabs, rotate, stop/reopen and verify restoration | Pending | Pending |
| Image zoom, GIF, MP4 and WebM playback, pause/resume and audio preference | Pending | Pending |
| Autoplay timing/end-of-media and stopping it; navigate next/previous | Pending | Pending |
| `/f/` SWF thumbnails and at least one accessible, supported SWF; distinguish HTTP 404 from renderer limitations | Pending | Pending |
| Clear media cache from the menu without losing preferences/bookmarks or corrupting active work | Pending | Pending |
| Download to an explicitly selected test folder through SAF; verify files and cancellation | Pending | Pending |
| Posting form, keyboard/insets, rotation/draft, CAPTCHA readability/reload/cancellation and Back | Pending | Pending |
| Browser hand-off and return without losing app/draft state | Pending | Pending |

Use an owner-selected test folder and harmless synthetic attachment for storage/
attachment checks. Do not erase existing downloads, overwrite another folder or
select/upload private files. Record unsupported SWFs separately from crashes;
arbitrary SWF compatibility and missing/purged server files are not guaranteed.

CAPTCHA solving is human work. **Do not press “Confirm and send” in the real
release form as a non-posting test**: that action can publish the message.
Use the documented non-posting debug preview only when explicitly exercising
that preview, and do not count it as end-to-end release submission. Any new
public post needs fresh authorization for its thread and exact message. The
earlier authorized Android 12 text reply is historical evidence, not permission
to repeat it. Attachment and new-thread publication were not accepted by that
earlier text-only test. See [POSTING.md](POSTING.md).

## Current device evidence and next decision

Initial inventory finds only **Motorola Edge 20 / Android 12 / API 31**, with
arm64-v8a/armeabi-v7a support. It has the beta package installed and no release
package; inventory alone does not validate release UI or installation. No Android
10 device is connected. The owner confirms that only Android 12 is currently
available; physical Android 10/32-bit testing remains pending.

The owner explicitly authorizes an **internal copy signed with the existing
test key**, acknowledging that a differently signed future public APK is not
an ordinary update to this copy. The signed artifact is:

- `mimi-reader-c3824a7-internal-release.apk`: **41,528,196 bytes**.
- SHA-256: `78f075d723e87d2b7867a0a22607a07d5fc16424711a702a1b48b5a8c190b888`.
- Certificate SHA-256:
  `e98eedf04e4bba13131ecdd591d8b9bf8906066b9dc942de09105504856a899f`,
  matching the tracked debug keystore (public legacy RSA-1024 test key, **not
  a publication identity**).
- Full-range signature verification passes v1/v2/v3; explicit API 29 and API 31
  verification passes using v3. Full-range verification emits META-INF/v1
  metadata warnings; retain them rather than removing metadata/licenses to hide
  warnings. This is not production signing approval.
- Post-signing native ELF/ZIP/JNI and SDK 16 KB alignment checks pass. Relative
  APK paths are used for native `aapt`: the Windows tool rejects the accented
  absolute workspace path; this is a host path issue, not an invalid APK.
- Package/version/SDK/ABI metadata remains unchanged. The unsigned original,
  AAB, mapping and matching SBOM are preserved. No release-key properties, version
  bump, application code or tracked key is modified by signing this copy.

The initial installation preflight stopped while the phone was asleep/locked.
After the owner unlocked it, fresh installation of the exact signed APK succeeded
without `-r`, uninstalling, clearing data or replacing the beta. Android reports
a successful cold start into `TabsActivity` (834 ms), a running process and a
resumed release activity. The beta's version and installation/update timestamps
are unchanged. The temporary stay-awake setting was restored to its captured
value, `0`.

The owner additionally requires preservation of the original app's `Mimi`
folder, allowing a rename to `mimi_old` only if needed for protection. The located
folder is `/storage/emulated/0/Pictures/Mimi`, containing **607 files**. Before
installation, inspection confirms that startup cleanup uses the app's cache
directories, not this public picture folder; the APK requests no broad storage
permission and this fresh installation has no owner-granted SAF access to it.
No rename is needed or performed. Comparing every file's path and SHA-256 before
installation and after startup confirms **607/607 unchanged**, with no files
added or removed. Private filenames and their individual hashes are not committed.

The phone locks again before the guarded screenshot attempt, so no screenshot
of an unrelated screen is captured. This is a successful install/cold-start
smoke check, **not** visual main-menu/inset acceptance, update/migration validation
or completion of the other human checklist rows. Android 10 remains unavailable.

The owner defers physical Android 10 checks and authorizes the 8.0 versioning/
English changelog block on 25 October 2026; those checks remain pending, not
passed. [CHANGELOG.md](../CHANGELOG.md) and the in-app notes explicitly retain
release limitations. New 8.0 artifacts need their own acceptance, and further
fixes must be reflected in those notes. The full README rewrite remains last.
Do not publish an APK/AAB, create a release/tag, change production signing or
claim all release gates are met merely because automated CI is green.
