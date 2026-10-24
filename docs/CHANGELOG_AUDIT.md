# 8.0 release-note scope and history audit

The owner approved this block on **25 October 2026**: English root Markdown and
in-app HTML, grouped by features, changes, fixes, security, internals and limits;
version **8.0 / code 231**; no publication, tag or production signing change.
Physical Android 10 is explicitly deferred, not accepted. Gradle stays 8.13
until the separately scoped application 8.0.1 work. README rewriting remains last.

## Baseline and evidence

`fc1e4cd8d4f73360723b910be31907b970a5e5d1` is the inherited Blatzar tip and
merge-base at this audit; it already declares 7.0.1 / 230 and includes centered
replies. The earlier 7.0.1 import `a624cfa` is not this fork's starting point.
The reviewed range `fc1e4cd..c3824a7` contains **79 commits**, beginning with
`dbfa56e`; inherited features are not presented as new modernization work.
This versioning/notes change is added on top of that range.

Reproduce the review with:

```sh
git log --reverse --format='%h %s' fc1e4cd..c3824a7
git diff fc1e4cd..c3824a7 -- mimi-app chanlib build.gradle gradle scripts .github
git show fc1e4cd:mimi-app/build.gradle
```

| Changelog area | Representative implementation evidence |
| --- | --- |
| SDK/Android 12 startup, MP4/Media3, SAF and residual ad insertion | `dbfa56e`, `4e6051b` |
| Persistent batch jobs, cancellation, integrity and cleanup ordering | `4e6051b`, `2e5400e`, `2f82dde`, `50ba521` |
| Network libraries, platform TLS, pacing, malformed responses and cached fallback | `e85a736`, `0368603`, `431eb46`, `d6b9133`, `70fb24c`, `2405af4`, `1ee42a6`, `e02f5b7` |
| Board safety filters and complete themed catalog | `431eb46`, `7d36a6e`, `fba2cf9` |
| Gallery audio choice, nearby prefetch and two-download cap | `915fb9a`, `eac7444` |
| Image/video/GIF autoplay and visible controls | `fb2422c`, `50a3fc7`, `337eee6` |
| Room/preference migration, Lifecycle and off-main writes | `5497277`, `b0a7582`, `51d4f41`, `88cf779`, `1a45d7a` |
| ViewPager2, Back, system bars, rotation, tab limits and retry | `ae76f79`, `498589d`, `e786f12`, `972a2a2`, `1fc8066`, `741f8c4`, `b860b6d` |
| Confirmed cache clearing with persistent-data boundaries | `1d9a801`, `2cfa199` |
| Isolated Ruffle, Flash filename URLs, cards, 404 handling and lifecycle | `d379d6a`, `9466731`, `3228f4b` |
| Single free app, donation/store action and orphan-code removal | `0ef4400`, `60c5770`, `eeb2e31`, `faaf5a7`, `f5afe2f` |
| Manual CAPTCHA, explicit posting, retained draft and strict receipts | `5ff224a`, `6541737` |
| AndroidX UI, native PDF/GIF, viewport/lifecycle and real 16 KB gate | `04b3a42`, `e485a71`, `3c5357f`, `7b2f57c`, `fed11d2` |
| Browser, fast-scrolling, Material, Glide and filter-row replacements | `dc17d6d`, `db43b0b`, `cca6fa6`, `2cfa199`, `3dd4c9d` |
| More Info, verified authorship and offline local-document lifecycle | `f5afe2f`, `f1b63df` |
| Settings, color editor, dependency scoping and bounded resource cleanup | `53379f4`, `789608c`, `268fd5d`, `3c22d4d`, `62c24c3`, `f741d0b`, `f9568dc`, `8bff4e9`, `e84af87` |
| Release/R8/privacy, Android matrix and CI preparation fixes | `54cd463`, `86c7043`, `bf56528`, `2e7bcbb`, `eee2ebd`, `268fd5d` |
| Reproducible CycloneDX/inventory and vulnerability policy/SARIF | `627e265`, `c76cef8` |
| Commons Lang CVE remediation without quote-parser regression | `c3824a7` |

The roadmap and linked dependency, posting and test audits supply the detailed
acceptance history; documentation-only and test-fixture fixes are consolidated
under their feature rather than exposed as duplicate user features. This is a
reviewed summary, not a raw dump of commit messages or a claim of universal
compatibility. Current source behavior takes precedence over an older intermediate
commit (for example Kotlin 2.3.21, not the initial 1.9.24 migration).

## Explicit boundaries

- Keep 7.0.1 and 7.0.0 in-app notes and original Apache/copyright text. Replace
  only the historical notice's dead promotional website link with factual text.
- SWF cards are not movie thumbnails; CDN 404s and unsupported Ruffle features
  remain limitations. Broader ActionScript 3/touch/Android 10 checks are pending.
- SFW/NSFW classification applies to boards, not guaranteed filtering of posts.
- Text-reply evidence on Android 12 does not accept real attachments/new threads,
  every CAPTCHA provider, external-session transfer or new public test posts.
- Manual cache clearing and inherited version-upgrade maintenance differ: the
  latter also clears normal/archived cached post tables. Do not promise every
  offline database row survives, or touch the owner's saved media to test this.
- PDF password entry is not implemented. Four current ABIs are retained;
  obsolete MIPS/MIPS64 are not. Static alignment is not runtime acceptance.
- Do not credit this fork with upstream's earlier ad-service removal or Blatzar's
  centered-replies change. Preserve Eli Connelly's authorship and account links;
  a GitHub fork-network root is not proof of authorship or endorsement.
- The archived 7.0.1/230 internal APK's startup/data checks are historical evidence,
  not a signed 8.0 acceptance. No tag/date/public signing identity is created.
- CVE scanning has explicit native/WASM/build-tool/generic-component gaps.
  Notifications and further Gradle work remain post-8.0 proposals.

## Synchronization and verification

Root `gradle.properties` owns `mimiVersionName`/`mimiVersionCode`; the application
and SBOM consume those same providers, including explicit `-P` overrides. The
tracked SBOM must be regenerated, with its component/edge changes reviewed, not
hand-edited. Old artifact folders stay immutable and identify their old source.

`scripts/ci/test_release_notes.py` runs through Android CI's existing helper-test
discovery. It compares every current Markdown/HTML paragraph, heading and bullet
in order; verifies shared version properties/SBOM identity; retains historical
notes/license; and rejects missing/duplicate/empty/truncated sections and active
embedded resources. `LocalDocumentTest` checks visible 8.0 and historical text
through both current/legacy fragment entry points and recreation.

Local validation for this preparation:

- 128 app/library unit tests (109 + 19), zero failures/errors; 104 CI-helper
  tests (96 existing + eight release-note checks), zero failures/errors.
- Debug and instrumentation APKs, minified unsigned release APK and release AAB
  build successfully; lint has zero errors (977 warnings and one hint remain).
- Both packaged APKs contain the exact reviewed changelog bytes, including
  historical entries. SDK metadata reports 8.0 / 231, minSdk 23 and target 36;
  release remains non-debuggable with the original application ID.
- SBOM generation, six negative/schema checks and freshness validation pass.
  Comparing with `c3824a7` changes only application root identity/code; all 150
  component records/hashes, normalized edges and the 71-declaration inventory
  are unchanged. Explicit quoted `-P` overrides produce a probe identity and
  default generation is restored and verified afterward; no probe is committed.
- Static ELF/ZIP/JNI checks pass for 24 libraries across four ABIs on debug and
  release; SDK `zipalign -c -P 16 4` passes. This is not another native runtime run.
- A fresh actual Trivy 0.76.1 scan of the committed 8.0 SBOM recognizes 148/148
  Maven packages, returns zero known findings and uses no exceptions. Keep its
  explicit generic/GitHub/native/WASM/tooling exclusions.

An initial unquoted PowerShell override command failed before generation; quoted
`-P` arguments succeed, and the documented example uses that form. The default
SBOM was restored/verified after the probe. No failing application test is hidden
by a retry. The new instrumentation source compiles but is not executed locally
in this block: do not update/clear the owner's beta or archived internal install
just to test documentation. Require this commit's own remote Android/security
workflows and the remaining human release checks. No phone package, permission,
screen setting or `Pictures/Mimi` file is changed by this block.
