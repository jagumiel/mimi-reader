# Legacy settings resource audit

## Baseline: CI run 35

[Run 35 for `f1b63df`](https://github.com/jagumiel/mimi-reader/actions/runs/39930834599)
passes all six jobs: build/unit/lint/SBOM/release, ordinary instrumentation on
API 29/31/34/36 and the separate native 16 KB job. API 29 logs confirm native
`WebViewCommandLine 'disable-features'` includes `MojoUseBinder` before the
78-case suite. Its original-provider capability and pinned startup preflight
also pass. This accepts the fifteenth block, not subsequent commits.

## Sixteenth block: evidence before deletion

`res/xml/board_prefs.xml` and `posting_prefs.xml` are identical copies of the
old About/Website/Privacy/Licenses/Version screen. Neither contains actual
board options, a posting form or CAPTCHA handling. No production source, XML,
manifest, variant, build configuration or R8 rule references either resource.
There is no production `getIdentifier` or reflective preference-resource lookup.
Debug lint independently reports both resources unused; that report alone
would not justify deletion.

The only settings root is `prefs.SettingsFragment`, which inflates
`preference_headers.xml`. Its six live entries route explicitly through
`SettingsActivity.onPreferenceStartFragment` to General, Gallery, Appearance,
History, Advanced and More Info. Each fragment inflates its own real XML.
Neither debug nor release source sets override the deleted XML. Keep all six
live screens and their resource/class names for restoration and R8.

Remove the two XML copies and five confirmed orphan strings:

| Resource | Evidence |
| --- | --- |
| `about_pref` | Only consumers were the two deleted copies; live About uses `project_status_pref`. |
| `posting_pref`, `board_pref` | Definitions only; neither is a live header or preference key. |
| `more_info_website` | Only the deleted copies used the dead `MimiReader.com` label. |
| `more_info_version` | Definition only; the live version row retains its existing title and `version_pref`. |

Remove the commented `PreferencesHC$PostingPrefsHCFragment` and
`PreferencesHC$ThreadPrefsHCFragment` header examples: neither class exists,
and comments are not a supported navigation entry point. Keep the original
copyright/license header of the live XML.

Keep shared strings such as `more_info_about`, `website_pref`, `privacy_pref`,
`licenses_pref`, `changelog_pref` and `version_pref`: they have current consumers.
Do not infer that an old label, preference definition or file is disposable
without checking each consumer. Remaining unused-resource warnings are a
candidate list, not permission for a bulk delete.

## Persistence and behavioral boundaries

Only resource definitions and dead comments are removed. Do not change storage
keys, migrations, preference schema, Room data, permissions, posting/CAPTCHA,
downloads, lifecycle production code, dependencies, R8 rules or app versions.
Removing a string resource is not an instruction to delete its value from a
user's SharedPreferences. Existing unknown/legacy values remain untouched.

Two new mandatory instrumented cases cover:

- Actual header clicks into every live settings category, required controls,
  activity recreation, restored preference tree, Back stack and return to the
  same six root entries. Existing preference values must remain equal; missing
  XML defaults may initialize on a cold installation. No control that clears
  data, changes settings, logs in or launches another app is clicked.
- The existing migration against an isolated test preference file containing
  stored `about_pref`, `posting_pref`, `board_pref` and unknown string-set data.
  Preserve all values and verify idempotence; never inject those fixtures into
  the user's actual preferences.

The ordinary suite becomes **80 cases**, retaining the same three opt-in skips;
the dedicated native 16 KB job stays at six. Existing posting, migration,
document and More Info tests remain mandatory. The cleaned commit needs its
own green six-job CI run; run 35 is not evidence for unpushed changes.

## Local validation, 23 October 2026

- The full Edge 20 / Android 12 suite passes **80 distinct cases: 77 passed,
  three existing opt-in skips, zero failures/errors**. Counts come from JUnit
  XML, not the console progress counter (which ended at 83). Both new cases
  pass; all six category recreation/Back paths and the existing posting,
  document, media and persistence coverage remain. Restore and verify the
  phone's original stay-awake value **0**. No new public post, browser launch
  or human CAPTCHA acceptance is claimed.
- The scoped API 29 run passes **five cases, no failures/errors/skips**: all six
  live categories through the new navigation test and all four migration cases.
  This is an existing test-AVD installation, not a new physical Android 10 or
  cold-install acceptance. Restore its original stay-awake value **1**, verify
  no temporary WebView flags exist and shut down the owned emulator afterward.
- **110 app/library unit tests**, **54 CI-helper tests**, actionlint, variant-ID
  checks, SBOM freshness and six malformed-input cases pass. Debug/test APKs,
  minified release APK/AAB and vital lint build successfully. No CI retry, skip,
  deadline or workflow change is introduced.
- Debug lint has **zero errors, 1,005 warnings and one hint**, including **475
  UnusedResources** findings: seven fewer than the preceding 1,012 warnings / 482
  unused-resource findings. No new suppression, baseline or fixture warning is
  introduced. The remaining findings still require consumer review.
- Dump both APKs' actual resource tables: the two XML and five string names are
  absent; the root, six live XML screens and shared More Info keys remain. All
  seven live settings fragment names are retained in release mapping. These
  checks complement the source audit; they do not make debug instrumentation
  into minified-release UI execution.
- Both APKs pass static ELF/ZIP guards (24 native libraries / four ABIs); release
  also passes JNI seeds and SDK 16 KB zipalign. The baseline run 35's actual
  16 KB success is not a runtime result for this new commit.
- Compared with `f1b63df`, release APK changes from **41,426,571 to 41,423,679
  bytes** and AAB from **31,144,361 to 31,142,992 bytes**. Do not infer startup or
  memory improvements from these sizes. SBOM remains **152 components / 75
  declarations**; dependency files, Ruffle, versions and legal documents are
  unchanged.

The initial local build stopped in R8 because Windows locked an existing
`classes.dex`, not because an application test failed. Preserve that failed
attempt in `build/settings-orphans-validation.log`; after stopping the idle
Gradle daemon, the full build succeeds in `build/settings-orphans-verified.log`.
No source fix or test relaxation is used to hide the environment error.

## Next bounded review

The follow-up layout audit is recorded below. Its next bounded task is the live
color editor's preview lifecycle and state restoration, not deletion of its
still-used layout or color-picker dependency. Keep manifest destinations,
font/migration assets and legal notices unless there is separate evidence for a
safe change. CVE scanning stays behind the agreed cleanup gates; the 8.0
changelog requires owner consultation and the full README rewrite remains last.

## Seventeenth block: settings layouts and generated bindings

[Run 36 for `53379f4`](https://github.com/jagumiel/mimi-reader/actions/runs/39936759280)
passes all six jobs, accepting the sixteenth block, including API 29 and the
dedicated native 16 KB job. The new layout cleanup needs its own remote run.

The settings-named layouts have different consumers:

| Layout | Actual consumer / decision |
| --- | --- |
| `settings_activity.xml` | `prefs.SettingsActivity.setContentView`; preserve its `settings` container, shared toolbar and six fragment routes. |
| `activity_theme_settings.xml` | Manifest-registered `ThemeSettingsActivity.setContentView`, reached from Appearance; preserve its color controls, preview container and layout. |
| `mimi_settings_activity.xml` | No source/XML/manifest/variant/R8/dynamic-name consumer; remove this abandoned toolbar/container shell. |

The last layout's only exclusive ID is `settings_container`; no caller uses it.
View Binding previously generated `MimiSettingsActivityBinding`, but no source
or layout consumes that generated class. Release `usage.txt` already lists it
as removed. Automatic generation is not proof that a layout is live. Debug lint
independently reports the layout unused. No variants override it. Delete the
source layout only, allowing Android's generator to remove the dead binding;
do not manually edit generated files or add keep/discard rules.

Extend the existing navigation case to bind the actual settings hierarchy and
require its toolbar title and Back icon before/after recreation. Add one
mandatory case inflating both retained layouts through their generated bindings
for three themes, 320/480 dp widths and LTR/RTL. Check toolbar/container geometry,
all ten color swatches, spinner and preview container, detached parents and
unchanged preferences. This yields **81 ordinary cases**, with the same three
opt-in skips and separate six native cases. Inflating a detached color layout
does not execute the color editor's activity callbacks, mutate colors or prove
its interactive/restoration behavior; that remains a separate follow-up.

No production activity logic, retained layout content, stored keys/values,
dependencies, R8, app versions, legal documents or SBOM records change. No
performance claim follows from removing a layout already shrunk in release.
The next bounded review should cover `ThemeSettingsActivity`'s preview
subscription, selection/recreation and obsolete sample link with tests that
isolate or restore color preferences. Do not remove its live color picker.

### Seventeenth-block local checks, 23 October 2026

The full Edge 20 / Android 12 suite passes **81 distinct JUnit cases: 78 passed,
three existing opt-in skips, zero failures/errors**. Use XML counts, not the
console progress total (84). Both settings cases pass within the complete suite,
alongside existing posting, document, persistence and media regressions. The
phone's original stay-awake value **0** is restored and verified; no public
posting or new human CAPTCHA acceptance is claimed. This is debug-device
acceptance, not the new commit's remote matrix or minified-release UI execution.

The two settings cases pass first on the Edge 20 / Android 12. The API 29
emulator separately passes **six scoped cases with no failures/errors/skips**:
the two settings cases and four migration cases. These are existing device/AVD
installations, not cold-install or physical Android 10 acceptance. No real color
editor is launched or color preference changed. The emulator's observed
pre-test stay-awake value is **3** this boot; leave it unchanged and stop the
owned emulator, rather than assuming an earlier boot's value.

Also pass **110 app/library unit tests**, **54 CI-helper tests**, actionlint,
variant IDs, SBOM freshness and six malformed-input cases, debug/test APKs and
minified release APK/AAB with vital lint. Debug lint has **zero errors, 1,004
warnings and one hint**, including **474 UnusedResources** findings (one fewer
than the preceding block). No suppressions, baselines, test retries or skip
changes are added. No failed build or assertion is accepted as passing.

Inspect both actual APK resource tables and DEX: the deleted layout, its ID and
`MimiSettingsActivityBinding` descriptor are absent; retained settings layouts,
toolbar and control IDs remain. The unused generated Java source is gone while
the two retained bindings generate/compile. Release mapping preserves the live
settings fragment names. Debug binding tests are not minified-release UI tests.

Both APKs pass static ELF/ZIP checks (24 native libraries / four ABIs); release
also passes JNI seed checks and SDK 16 KB zipalign. Run 36's real 16 KB result
does not certify this unpushed commit. SBOM stays at **152 components / 75
declarations**, with unchanged native/Ruffle artifacts, dependencies, versions
and legal contents. Compared with `53379f4`, release APK changes from
**41,423,679 to 41,423,143 bytes** and AAB from **31,142,992 to 31,142,409 bytes**.
Do not infer memory or startup performance from these small size changes.

## Twentieth block: orphan rating-selector closure (R01)

Review the four-node resource closure before removal:

| Resource | All source edges found before removal |
| --- | --- |
| `drawable/app_rating_selector` | Definition only; no external caller. |
| `drawable/app_rating_selector_normal` | Only referenced by `app_rating_selector`; an empty selector. |
| `drawable/app_rating_selector_pressed` | Only referenced by `app_rating_selector`. |
| `color/rating_button_selected` | Only referenced by the pressed selector. |

Search both modules' main/debug/test source sets, qualified resources, theme/style
XML, manifests, build scripts, generated binding source and explicit R8 files.
No production `getIdentifier` or reflective class lookup is present. Generated
resource-symbol declarations and old lint reports are not callers: ignore stale
`freeDebug` outputs left by previous flavors and inspect newly built debug/release
artifacts instead. No binding is generated for these drawable selectors.

Delete the three XML files and the single color entry as one closed set.
The actual market/rating preference and helper were already removed together
in the fourteenth block. Keep `rating_background` and `rating_text`: they are
outside this reviewed closure, not implicitly approved for removal. Leave all
other themes, arrays, layouts, preference keys, attribution, bundled license
notices and CAPTCHA paths unchanged. Deleted resource sources are recoverable
from Git; no user cache, downloads, preferences or database data is removed.

Reuse existing regression coverage rather than adding test-only production
lookups: `SettingsNavigationTest` covers both retained layouts in light/dark/black,
two widths and RTL/LTR, plus all six real settings categories; `MoreInfoPrefsTest`
covers absent rating/email entries, current credits/links, local notices,
recreation and Back. External intents are intercepted. Theme-layout inflation
is not a claim that the real More Info screen runs in all three themes, and
debug instrumentation is not minified-release UI execution.

The remotely observable green run at the start of this block is
[run 39](https://github.com/jagumiel/mimi-reader/actions/runs/40034700649), all six
jobs for `b28bce6`. Local Git reports `3c22d4d` one commit ahead of origin and
the API returns no workflow for that SHA; do not transfer run 39's status to
D01 or this resource cleanup. Neither a push nor a remote CI success is claimed.
The new commits still need their own complete six-job workflow.

### R01 build and artifact checks

Pass 121 app/library unit cases (102 + 19; the unchanged library suite is
up-to-date), 59 CI-helper tests, SBOM freshness and six malformed-input checks,
variant IDs and the standalone library dependency boundary. Debug/test APKs
and minified release APK/AAB build successfully in 1m32s. Lint has zero errors,
995 warnings and one hint, with 470 `UnusedResources` findings: exactly four
fewer than the preceding 999 / 474 baseline, without suppressions or baselines.

Inspect actual debug and release APK resource tables: all four removed names
are absent. Both settings layouts, More Info XML and privacy/license/historical
changelog resources remain. Both APKs pass static ELF/ZIP checks (24 libraries /
four ABIs); release also passes JNI seed checks and SDK 16 KB zipalign.
Release APK is **41,422,919 bytes**, versus 41,423,143; AAB is **31,141,766
bytes**, versus 31,141,845. These tiny changes are not performance measurements.
SBOM snapshots, dependencies, minSdk/target, IDs, versions and R8 rules do not
change. No fresh actual-16-KB runtime or minified-release UI run is claimed.

The first phone attempt is **failed/interrupted, not accepted**: Android enters
sleep due to timeout before instrumentation starts; subsequent preference and
activity tests fail on saved/stopped state, then the phone is confirmed Dozing
with keyguard locked despite stay-awake 7. Stop only the debug app/test runner,
preserve logs/XML under ignored `build/` and restore/verify stay-awake 0. After
the owner unlocks the phone, require Awake/unlocked before a fresh complete
suite. Do not change assertions, introduce retries or suppress lifecycle errors
to convert that run into a pass.

After the owner unlocks the Edge 20 / Android 12, the fresh complete suite passes
in **1m30s: 86 distinct XML/JUnit cases, 83 passed, three existing opt-in skips,
zero failures/errors**. All six existing More Info/settings cases are present
and pass, including the detached three-theme layout case. The console's 89
completion events are not 89 unique cases. Preserve the failed and successful
reports separately; restore and verify stay-awake **0** after both attempts.
No public post, live CAPTCHA, physical Android 10/32-bit or remote acceptance is
inferred from this result.

## Twenty-first block: unused reply-option arrays (R02)

[Run 40](https://github.com/jagumiel/mimi-reader/actions/runs/40038285565) passes
all six jobs for `62c24c3`, accepting both D01 and R01, including the independent
library boundary, API 29/31/34/36 instrumentation and native 16 KB gate. This
supersedes the twentieth block's pending remote status, not the requirement for
the next commit's own workflow.

`res/values/arrays.xml` contains only `reply_entries` (`Reply`, `Reply to all`)
and `reply_values` (`reply`, `reply_all`). Both were reported unused by baseline
lint. Before deletion, the only production matches are these definitions; no
code, preference `entries`/`entryValues`, qualified XML, manifest, binding or R8
rule references them. Neither main source tree has a name-based resource or
reflective class lookup. Keep all other arrays: settings, board catalogs,
gallery options, theme colors and link menus have actual consumers.

Delete that single file and its two definitions, not a feature or a preference
migration. The live `ThreadDetailFragment` reply/quote/selection callbacks
construct `replyComment` and pass it with `Extras.EXTRAS_POST_REPLY`; they do not
load these arrays. Leave reply handlers, posting forms, CAPTCHA/submission,
stored keys, migration versions and unrelated reply strings unchanged. The
removed file remains recoverable in Git; no user data is deleted.

Extend the existing isolated preference-preservation case with synthetic
unknown keys named `reply_entries` and `reply_values`, holding `reply` and
`reply_all`. Verify every seeded value survives migration and a second migration
is idempotent. These are defensive test inputs, not evidence that historical
installations used the resource names as real preference keys. No user-default
preferences are edited by this fixture and no new test-case count is claimed.
Reuse the existing offline posting/CAPTCHA, settings and migration suites;
never enable the live CAPTCHA opt-in or publish a message for this cleanup.

### R02 local acceptance and limits

Debug/test APKs and minified release APK/AAB build in 1m17s. Pass 121 app/library
unit cases (102 + 19; unchanged library cases are up-to-date), including offline
posting protocol contracts, 59 CI-helper cases, SBOM freshness/negative checks,
variant IDs and the independent library boundary. Lint has zero errors, 993
warnings and one hint, including **468 unused resources**, exactly two fewer
than the preceding 995 / 470 baseline. No suppression or dependency changes.

Both actual APK resource tables omit the two retired array names and retain
live board/theme/gallery/bookmark arrays, settings XML and local legal notices.
Both APKs pass static ELF/ZIP checks (24 libraries / four ABIs); release also
passes JNI seed checks and SDK 16 KB zipalign. APK size is **41,422,903 bytes**
(previously 41,422,919); AAB is **31,141,726 bytes** (previously 31,141,766).
These sizes do not establish performance improvements. SBOM snapshots remain
unchanged at 152 components / 75 declarations.

The phone becomes Dozing/locked during compilation despite temporary stay-awake
7. The preflight refuses to start UI instrumentation and restores/verifies the
original value **0**. This is not a failed or passing UI suite: it never starts.
The separately scoped `PreferenceMigrationTest` then runs without activities
behind the keyguard: **four passed, no skips/failures/errors**, including the
expanded isolated preservation/idempotence case (19s). No unlock bypass or
default-preference changes are used. The full UI suite is initially pending user
unlock; the previous block's 86-case result must not be transferred to this change.

After the owner unlocks the phone on 24 October, the fresh complete suite for
`f741d0b` passes in **1m31s: 86 distinct XML/JUnit cases, 83 passed, three existing
opt-in skips, zero failures/errors**. This includes the expanded preference
fixture and existing offline posting/settings regressions; live CAPTCHA stays
opt-in and no message is published. Preserve the scoped migration and full-suite
reports separately. Restore and verify the original stay-awake value **0**.
Use the 86 unique XML cases, not the console's inflated 89 completion events.
Overall case counts remain 86 ordinary and six separate native. This closes
the local full-suite gate, not the new commit's remote matrix or physical
Android 10/32-bit release acceptance. The follow-up changes documentation only.

No public posting, live CAPTCHA, fresh actual-16-KB runtime, minified-release UI
or physical Android 10/32-bit acceptance is claimed. Require the new commit's
own six-job workflow. Next review D02's dependency declaration ownership;
leave D03/R03, CVE/GHSA activation, owner-approved 8.0 changelog and final README
work separately scoped.

## Twenty-fourth block: closed legacy theme-resource subtrees (R03)

Review all source sets in both modules, qualified resources, manifests, binding
inputs and R8 rules before removal. The following roots have no external
consumer; the only drawable edges are internal to these two obsolete groups:

| Removed group | Closure and scope |
| --- | --- |
| `basic_card_selector_{light,dark,black}` | Three selector XMLs and their six `basic_card_background_*_{pressed,focused}` XMLs. Default backgrounds are shared with retained styles, so are **not** part of this removal. |
| `ab_background_textured_thememimi{light,dark,black}` | Three bitmap XMLs and both `ab_texture_tile_thememimi{light,dark}` PNGs in mdpi/hdpi/xhdpi/xxhdpi. The black wrapper uses the dark tile; there is no separate black tile. |

This is **20 files / 14 resource names**, not a deletion of all `ab_*` or
`basic_card_*` assets. Git retains the removed source/binaries; no user data is
deleted. No main Java/Kotlin name-based lookup, XML reference outside the
closures, generated binding consumer, manifest entry or resource keep rule
requires these names. Lint's prior warnings corroborate, rather than establish,
this reachability analysis. Leave colors untouched: pressed/focused color names
are also used by retained grid resources and color-state lists.

The three `Theme.Mimi.*.CardBackground` styles still resolve to their original
`basic_card_background_{light,dark,black}`. Keep the complete theme hierarchy,
`ActionBar.Transparent.Mimi.Light` / `ab_transparent_thememimilight`, solid action
bars, grid selectors, third-party notices and all remaining resources. Catalog
CardViews use `?cardBackgroundSelector`, which resolves through each theme's
`CardBackgroundSelector` style to `light_item_selector`, `dark_item_selector` or
`black_item_selector`, **not** the similarly named removed drawable selectors.
The first/reply-post layouts also consume this style. No production Kotlin/Java,
layout, style, preference, dependency declaration or R8 rule changes.

Add one mandatory `ThemeResourceCompatibilityTest` case. Before and after host
recreation it resolves retained plain/grid background styles in all three
themes, draws normal/pressed/focused drawable states, inflates the four real
catalog/thread post layouts at 320/480 dp in LTR/RTL, and checks dimensions,
direction inheritance and catalog click/focus/foreground/color-state behavior.
Views stay detached, without adapters, remote-media loading or preference
writes. Existing settings/navigation and media tests remain enabled. This raises
ordinary instrumentation from 88 to **89** distinct cases, with the same three
opt-in skips; separate native coverage remains six cases.

### R03 local artifact acceptance and limits

Debug/test APKs and minified release APK/AAB build in **3m24s**. Pass 121 unit
cases (102 app / 19 library, including unchanged up-to-date library results),
59 CI-helper cases, independent library dependency-boundary checks, application
variant guards and SBOM freshness/negative tests: six malformed documents
rejected, asset hash mismatch rejected, canonical output stable. Both tracked
snapshots remain unchanged at 150 components / 71 declarations. No toolchain,
dependency, Ruffle or native input update is made.

App lint has **zero errors, 977 warnings and one hint**, including **454 unused
resources** (previously 993 warnings / 468 unused resources). Library lint also
has no errors. Do not translate those remaining findings into a removal list;
no suppression, baseline or test skip is added. Actual debug/release APK resource
tables omit every one of the 14 removed names and retain all nine checked live
plain backgrounds, catalog color selectors and grid selectors. The new test
class is absent from both release DEX files.

Both APKs pass static ELF/ZIP checks (24 native libraries / four ABIs); release
also passes JNI seed checks and SDK 16 KB zipalign. Release APK is **41,421,465
bytes** (previously 41,422,537), AAB **31,149,005 bytes** (previously 31,149,408).
Small packaging reductions are not startup, memory or security benchmarks. No
fresh actual-16-KB runtime, minified-release UI or physical Android 10/32-bit
acceptance is inferred. Require this commit's complete remote workflow;
previous CI is deliberately not inspected. No public post or live CAPTCHA is
authorized by this resource cleanup.

The complete Edge 20 / Android 12 suite passes in **1m45s: 89 distinct XML/JUnit
cases, 86 passed, three existing opt-in skips, zero failures/errors**. The new
resource case passes (1.382s), including recreation. Do not count the console's
92 completion events as unique tests. Preserve the XML under the ignored local
`build/reports/theme-resources/edge20-final/` report directory. The phone remained
awake/unlocked for the suite, and its original stay-awake **0** was restored and
verified afterward. No user preferences or public posts are changed by the new
fixture. Local success does not replace the new remote matrix or the remaining
physical/human release gates.
