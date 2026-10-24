# Commons Lang: bounded security update

## Accepted CI before this change

[Android CI run 45](https://github.com/jagumiel/mimi-reader/actions/runs/40133142211)
passes all six jobs for `c76cef8`: build/lint/unit tests, API 29/31/34/36
instrumentation and the separate actual-16-KB native gate. The preceding
R03 run 44 and D03 run 43 also pass. These results accept the previous changes,
not the dependency update in this document.

[SBOM security run 1](https://github.com/jagumiel/mimi-reader/actions/runs/40133142178)
passes both jobs for the same commit. Its scanner log recognizes **148/148**
Maven packages and reports the MEDIUM Commons Lang 3.11 finding with no waiver.
The publisher log validates/uploads SARIF and finishes with **Analysis upload
status is complete**. This closes the previous integration's remote workflow
and SARIF-ingestion gates, not the owner's required-status-check configuration.

## Scope and compatibility proof

The app declares `org.apache.commons:commons-lang3:3.11` directly; `chanlib`
does not declare or import it. The only two production consumers are
`PostOptionAdapter` (`StringUtils.containsIgnoreCase`) and
`FourChanCommentParser` (`StringUtils.isNumericSpace`). No direct production
`ClassUtils` call was found. That search is not proof that the old component
cannot be exploited through an indirect consumer.

Update only Commons Lang to **3.20.0**, after rejecting the initial 3.21.0
candidate on actual compatibility evidence. The reported CVE is fixed from
3.18.0, and 3.20.0 retains the Java 8 baseline. See
[Apache release notes](https://commons.apache.org/proper/commons-lang/changes.html)
and [CVE-2025-48924 / GHSA-j288-q9x7-2f5v](https://github.com/advisories/GHSA-j288-q9x7-2f5v).
Java 8 source compatibility alone does not establish Android runtime acceptance.

Keep both production call sites and their semantics unchanged, including the
now-deprecated `containsIgnoreCase` API rather than combining this update with
an API migration. Do not replace numeric classification with an ASCII regex or
case matching with locale-dependent lowercasing. Keep all other versions,
Gradle/AGP/Kotlin/JDK, SDK/minSdk, saved values, posting and R8/JNI rules unchanged.
Gradle's separate update remains deferred to 8.0.1.

Six new `CommonsLangContractTest` cases run against the old 3.11 first: nulls,
empty strings, mutable CharSequences, accents, Turkish I/default-locale
independence, Greek sigma, non-expanding German sharp-S, exact surrogate matches,
ASCII-space versus other whitespace, BMP Unicode digits versus surrogate digits,
and the parser's quote-token/OP suffix assumptions. Preserve their XML before
changing the dependency, then run the identical assertions with candidate 3.21.0
and selected 3.20.0.

The 3.21.0 attempt fails the unchanged surrogate-digit rejection assertion:
`isNumericSpace("\uD835\uDFD9")` now returns true. JDK/Android `Long.parseLong`
consumes UTF-16 digits and cannot parse that surrogate-pair digit. The parser
would take the numeric branch and throw. Upstream 3.21.0 deliberately changes
the predicates to code-point iteration; 3.20.0 retains character iteration. See
the [3.21.0 implementation](https://github.com/apache/commons-lang/blob/rel/commons-lang-3.21.0/src/main/java/org/apache/commons/lang3/StringUtils.java)
and [3.20.0 implementation](https://github.com/apache/commons-lang/blob/rel/commons-lang-3.20.0/src/main/java/org/apache/commons/lang3/StringUtils.java).
Keep all six original contract assertions, select the patched 3.20.0 baseline
and add the supplementary-digit quote to the actual-parser device fixture.
Do not waive the CVE, loosen the numeric contract or introduce a parser rewrite
just to select the numerically newest library. A future 3.21+ migration needs
an explicitly scoped numeric-parser/case-folding review first.

One additional `CommonsLangSecurityTest` exercises patched non-recursive lookup
with an oversized class name, 10,000 array dimensions and a 512-part missing
class name. On selected 3.20.0 all must fail with `ClassNotFoundException` within
a bounded test. This is its documented failure mode, not the added 3.21.0
`IllegalArgumentException` guards (which passed the rejected candidate's separate
security test). No untrusted class or network content is loaded, and these
synthetic calls are not introduced into production.

Two mandatory device cases use the actual consumers with synthetic data:
Android Filter publication plus detached displayed option rows, and full comment
parsing with numeric/Unicode/cross-board links, OP/You labels and highlight colors.
No Activity, Room write, stored user option, link click or public post is needed.
The initial parser fixture incorrectly passed an immutable replies list and
failed before the upgrade; the real parser adds discovered references. Preserve
that failed report, give the fixture its own mutable list and retain the exact
span/label/color expectations instead of weakening assertions or changing parser
production behavior. Standalone detached checks do not establish visible UI
acceptance behind the keyguard; the full suite requires an awake/unlocked phone.

## Dependency and security evidence

Both snapshots remain **150 components / 71 declarations**. Only Commons Lang's
component version/hash/upstream metadata changes, plus its node/application
root reference. After substituting that reference, all dependency edges are
identical. The other **149 complete component records**, including Ruffle and
all containing native AARs, are identical. The only selected-inventory changes
are Commons Lang in `releaseRuntimeClasspath` and `debugUnitTestRuntimeClasspath`;
all other configurations and build tools remain unchanged. No runtime transitive
dependency is introduced by the new Commons Lang POM. Its Apache 2.0 license is
retained; upstream additionally supplies a license URL in the generated record.

Actual pinned Trivy 0.76.1 scans before and after with current DB metadata,
full Maven coverage and no exceptions. Baseline 3.11 reports
`CVE-2025-48924`; selected 3.20.0 returns **148/148 recognized Maven packages and
zero known findings**. Raw JSON, original/normalized SARIF, policy JSON and
coverage/hash/DB evidence are retained under ignored
`build/reports/commons-lang/{baseline-scan,upgraded-scan}/`. This removes the
reported finding, not the native/WASM/generic/build-tool coverage limitations
in [VULNERABILITY_SCANNING.md](VULNERABILITY_SCANNING.md). Green local scans do
not pre-accept the new commit's remote workflows.

## Local acceptance and remaining gates

- Baseline 3.11: **six unit contracts pass** (7s task); after fixing the mutable
  fixture, **both actual-consumer device cases pass** (17s task). The latter
  detached checks run without requiring a visible activity. Keep initial failed
  fixture evidence under `build/reports/commons-lang/baseline-initial-failure/`.
- Rejected 3.21.0: the complete app unit task reports **109 cases, one failure**
  in the unchanged supplementary-digit assertion. The new security test passes,
  but that does not override a broken consumer contract. Keep its report under
  `build/reports/commons-lang/rejected-3.21/`. No APK from that failed attempt
  is installed as an accepted upgrade.
- Selected 3.20.0: **128 app/library unit cases (109 + 19)** pass; unchanged
  library cases are up-to-date. All six original contracts and the selected
  release's exact non-recursive failure expectations pass. Runtime StringUtils
  and ClassUtils classes have bytecode major **52** (Java 8). **96 CI-helper
  cases**, SBOM schema/freshness/negative tests, variant IDs and independent
  library-boundary checks pass. No test/CI retry or suppression is added.
- Debug/test APKs and minified release APK/AAB build in **1m19s**. App lint stays
  at **zero errors, 977 warnings, one hint / 454 unused resources**; library lint
  also has no errors. Existing deprecation notices are not suppressed.
- Full awake/unlocked Edge 20 / Android 12 suite: **91 distinct XML/JUnit cases,
  88 passed, three existing opt-in skips, zero failures/errors**, in **1m47s**.
  Both new consumers pass, including the final supplementary-digit quote.
  Use XML counts, not 94 console completion events. Preserve results under
  `build/reports/commons-lang/upgraded-full-suite/` and restore/verify the
  original stay-awake **0**. No user settings, database rows or public posts are
  changed by the fixtures.
- Both APKs pass static ELF/ZIP checks (24 libraries / four ABIs), plus release
  JNI seeds and SDK 16 KB zipalign. No fresh actual-16-KB runtime is inferred
  from unchanged native inputs. Release APK is **41,421,465 bytes**, the same
  size as the accepted local baseline; AAB **31,149,054 bytes**, previously
  31,149,005. The small AAB increase is recorded, not sold as a performance gain.
  The new device-test class is absent from release DEX.

Require this new commit's own six Android jobs and both SBOM-security jobs,
including SARIF ingestion of the updated/empty findings. Local success and
previous run 45 do not accept the new revision. Physical Android 10/32-bit media,
human CAPTCHA/keyboard/browser and minified-release UI acceptance remain release
gates; the 8.0 changelog needs consultation before drafting and the full README
rewrite stays last. A future 3.21+ numeric-parser migration is a separate review,
not an additional prerequisite to fix this CVE or ship the agreed cleanup.

## Remote acceptance confirmed on 25 October 2026

[Android CI 46](https://github.com/jagumiel/mimi-reader/actions/runs/40137617724)
passes all six jobs for `c3824a7`, including API 29/31/34/36 and the separate
six-case native 16 KB job. [SBOM security 2](https://github.com/jagumiel/mimi-reader/actions/runs/40137617737)
passes both jobs; its logs report 148/148 Maven packages, no known findings and
completed SARIF processing. These supersede the pending remote gates above,
not physical-device release acceptance or required-check configuration.
The next step is the [signed minified APK physical checklist](RELEASE_ACCEPTANCE.md),
with an explicit signing choice and no replacement/clearing of the user's beta.
