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

Audit remaining legacy settings layouts and their generated view-binding
consumers before removing any, beginning with `mimi_settings_activity.xml`.
Keep manifest destinations, restoration compatibility, font/migration assets
and legal notices unless there is separate evidence for a safe change. This
block does not close the full dependency/resource cleanup. CVE scanning stays
behind the agreed cleanup gates; the 8.0 changelog still requires owner
consultation and the full README rewrite remains last.
