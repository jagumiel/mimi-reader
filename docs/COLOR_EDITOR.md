# Color editor lifecycle and CI run 37

## CI diagnosis

[Run 37 for `789608c`](https://github.com/jagumiel/mimi-reader/actions/runs/40026744999)
passes build/unit/lint/release and the four ordinary Android jobs. Only the
dedicated native 16 KB job fails, **before Gradle or any native test starts**:
`adb: unable to connect for root: closed`. The image had reported boot complete;
the preceding identity/API/page-size reads succeeded. This is a transport loss
around the adbd root request, not a failed PDF/GIF assertion or a regression in
the removed settings layout. The failed run does not supply native acceptance.

`android_native.py` now captures root-request output, tolerates only a known
closed/offline transport ambiguity, and polls reconnection with a bounded
deadline and short individual command timeouts. Require **uid 0 and completed
boot**, then revalidate emulator/API 36/actual 16384-byte pages before any
compatibility-property writes. Root denial, wrong devices/pages and an expired
deadline fail. Never restart the host ADB server, change a physical phone,
repeat the root request or retry Gradle/native assertions to obtain a pass.
`device.txt` records `adbd_uid=0` only after verification; compatibility remains
disabled and every native case still checks the requested runtime page size.

Five new CI-helper cases cover the observed closed-root response, root denial,
offline/nonroot/booting reconnection, deadline failure and changed page size.
There are **59 CI-helper tests**; the native suite remains six cases.

## Eighteenth cleanup: the live editor

The old `ThemeSettingsActivity` uses an Rx chain whose map already executes on
main, passes null board/reply data to a demo path with non-null Kotlin spans,
and writes a color in `onStop` without an explicit user edit. Its ad-hoc dialog
does not retain its draft/target across recreation. `ColorImageView.onDraw`
creates and installs new image/background drawables every frame, scheduling
further drawing and preventing instrumentation launch from becoming idle.

- Render the small fixed offline sample directly on resume and explicit edits,
  with a fresh mutable reply list and non-null board name. The parser collects
  reply IDs into that list. Reuse one preview view/holder; no Rx subscription,
  pending view callback or stop-time save is needed. Display the current
  repository URL instead of the dead website, with preview navigation disabled.
- Preserve Quote/Reply/Highlight/Link selection in saved instance state. Keep
  existing keys and `MimiUtil` storage/cache setters. Palette clicks save the
  selected slot immediately; simply opening, stopping, resuming or closing does
  not write preferences.
- `ThemeColorPickerDialog` is a restorable DialogFragment. Save its draft and
  carry the target slot in arguments/results; only explicit OK writes through
  the current activity. Cancel/Back and invalid results do not write. Synchronous
  dialog display prevents duplicate pending dialogs on rapid clicks.
- Create/mutate each swatch's two drawables once. Tint on color/border/selection
  changes, not inside drawing. Resolve XML defaults as actual colors rather
  than treating resource IDs as ARGB values. Preserve original attribution,
  swatch APIs, layouts and the live color-picker dependency.

## Test isolation and an explicitly recovered local failure

The first local implementation supplied an immutable reply list; parsing tried
to append to it and crashed. The initial test seeded the four real color keys
and relied on teardown, which could not run after process death. That fixture
was unsafe. The user confirmed they used defaults and authorized restoring them.
Stop only the debug app, remove only those four overrides, and compare every
other preference XML element before/after. The defaults were restored and all
other settings were verified unchanged. Do not infer or invent previous custom
values. Preserve the failed log (`build/color-editor-scoped.log`).

Subsequent editing tests use a **debug-only, non-exported test activity** with a
separate preference file and overridable color accessors. It executes the real
editor/dialog lifecycle without writing `MimiUtil` or the user's default store,
even if a test crashes. The fixture and manifest component are absent from
release. A separate real production-activity case opens/recreates/closes read
only and verifies all saved preferences stay equal.

The next local launch hung on the self-invalidating swatches; read-only thread
inspection showed instrumentation waiting for launch idle. Stop that failed
run and fix drawing, not the idle assertion. Keep its separate log
(`build/color-editor-scoped-isolated.log`) as failed evidence. No skip or timeout
increase hides either failure.

Five new mandatory cases cover actual preview text and all four span colors,
stable drawable identity/pixels, selection and read-only lifecycle, each palette
target, a draft/target restored across recreation/backgrounding and committed
only on OK, cancellation/rapid duplicate clicks/invalid results, and the real
production activity's read-only preferences. The ordinary suite becomes **86
cases**, with the same three opt-in skips; native 16 KB remains six cases.

## Acceptance limits and next gate

### Local verification on 24 October 2026

The final full Edge 20 / Android 12 run passes **86 distinct JUnit cases: 83
passed, three existing opt-in skips, zero failures/errors**. Use XML counts,
not the console progress total (89). All five editor cases pass with the final
rapid-click guard. The phone's color overrides remain absent (defaults), and
its original stay-awake value **0** is restored and verified. No new public
post or human CAPTCHA acceptance is claimed.

The five scoped editor cases pass on the Edge 20 / Android 12 with no skips,
including the separate production read-only case. The real local API 36
`google_apis_ps16k` emulator also passes **all six native cases**, no failures/
errors/skips, through the actual updated `android_native.py --test-16k` helper.
Its proof records **PAGE_SIZE=16384, adbd_uid=0, backcompat=false** and the
per-case runtime assertion remains enabled. This is not a mocked-only helper
pass, ordinary 4 KB emulation or inferred alignment result. Afterward restore
its original blank compatibility properties, adbd uid 2000 and stay-awake 1,
verify them and stop the owned emulator. The personal phone never receives
root or compatibility-property changes.

Also pass **110 app/library unit tests**, **59 CI-helper tests**, actionlint,
SBOM freshness/six malformed-input checks, variant IDs, debug/test APKs and
minified release APK/AAB. Lint has **zero errors, 999 warnings and one hint**,
with the same 474 unused-resource findings; no baseline/suppression is added.
Both APKs pass ELF/ZIP checks (24 native libraries / four ABIs); release also
passes JNI seeds and SDK 16 KB zipalign. Inspect manifests to require the debug
fixture's `exported=false` and absence from release; release mapping retains
the restorable picker fragment name. Compared with `789608c`, release APK stays
at **41,423,143 bytes** and AAB changes from **31,142,409 to 31,141,796 bytes**.
These are artifact sizes, not a performance benchmark.

Require this commit's own complete six-job remote workflow; do not transfer
run 36's green status or claim native acceptance from run 37. Physical Android
10/32-bit and human CAPTCHA/keyboard/browser checks still apply before release.
Dependencies, R8 rules, version 230 / 7.0.1, minSdk 23 / target 36, SBOM
152 components / 75 declarations, native/Ruffle artifacts and legal documents
remain unchanged. No startup, memory or frame-rate benchmark is claimed.

After remote confirmation, consolidate the remaining dependency/resource audit
into a prioritized inventory before enabling the deferred CVE/GHSA scan. Do not
bulk-delete the remaining lint findings or declare all cleanup complete. The
8.0 changelog requires consultation and the full README rewrite remains last.

Remote confirmation: [run 38](https://github.com/jagumiel/mimi-reader/actions/runs/40032230880)
for `268fd5d` passes all six jobs, including native 16 KB and ordinary API
29/31/34/36. The subsequent documentation-only review consolidates remaining
work in [CLEANUP_INVENTORY.md](CLEANUP_INVENTORY.md); it does not claim new device
acceptance or change the isolated editor test storage.
