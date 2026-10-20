# Manual CAPTCHA and posting

## Contract checked on 20 October 2026

The live [board form](https://boards.4chan.org/g/) posts multipart data to
`https://sys.4chan.org/{board}/post`. Its current verification widget is
[TCaptcha](https://s.4cdn.org/js/tcaptcha.min.7.js), not the old Google reCAPTCHA
integration. The required fields are `t-challenge` and `t-response`; some valid
challenges explicitly require no response. Replies include `resto`; new threads
omit it. Existing text fields and attachments use ordinary multipart parts.

The site's [official extension](https://s.4cdn.org/js/extension.min.1191.js)
recognizes the HTML comment `<!-- thread:NUMBER,no:NUMBER -->` as the posting
receipt. Mimi accepts that receipt only with a successful HTTP status and no
`#errmsg`. A generic HTTP 200, Cloudflare page or redirect is not confirmation.
IDs are stored as longs. An unrecognized/uncertain result keeps the draft and
asks the user to inspect the thread before retrying.

These are external, changeable contracts, not a guarantee that all boards or
future widgets support native posting. File restrictions and posting limits
remain controlled by the site.

## User flow

1. Write a reply or new thread and optionally select an attachment.
2. Send opens a dedicated CAPTCHA dialog unless the native Pass session bypasses
   verification. Complete the site's challenge manually, then explicitly confirm
   and send. The app never selects answers or solves challenges.
3. Reload requests a fresh challenge; cancelling verification retains the draft
   and attachment. Rotation retains form state and a single in-flight request.
4. Sending disables editing/cancellation. Only a verified posting receipt closes
   the form and records the confirmed post. Errors leave it available for retry
   with fresh verification; there is no automatic retry or redirect-following.
5. An interrupted process does not resubmit on restoration. Its saved state warns
   that delivery is uncertain. Check the thread before sending again.

The browser button opens the validated board/thread URL using the user's browser.
It is an alternative **posting page**, not a promise of cookie/token transfer
back into Mimi: browser sessions are separate and the native draft stays put.
If verification cannot run in WebView, finish posting manually in that browser.
[Cloudflare documents limited support for embedded WebViews](https://developers.cloudflare.com/cloudflare-challenges/reference/supported-browsers/).
Updating Android System WebView may help, but Mimi cannot guarantee acceptance
by anti-abuse services. This flow does not bypass them.

## Implementation and security boundaries

- `PostingProtocol` isolates board validation, bounded/expiring proof, multipart
  encoding and strict response parsing. A proof is bound to one board/thread and
  consumed for one explicit attempt. No responses or cookies are written to logs.
- `captcha/index.html` is locally authored glue, loading the current official
  widget remotely. Its HTTPS base matches the board site because the widget's
  iframe and verification cookies expect that site. There is no Android
  JavaScript bridge, local-file/content access, popup support or mixed content.
- CSP and native interception limit resources to the verification providers and
  permitted widget/image paths. Inline bitmap challenge images are allowed;
  arbitrary data HTML/SVG and local URLs are not. See Android's
  [WebViewClient interception contract](https://developer.android.com/reference/android/webkit/WebViewClient).
- The live CAPTCHA also requires Spur Monocle resources (`spur.us`/`mcl.io`),
  not just TCaptcha/hCaptcha/Cloudflare. Denying these produced the site's
  "Please unblock" error after the waiting period. The provider's
  [JavaScript integration](https://docs.spur.us/monocle/integrations/javascript)
  documents `js.mcl.io`; its [troubleshooting guide](https://docs.spur.us/knowledgebase/faqs/why-might-the-monocle-script-fail-to-load)
  confirms blocked domains prevent loading. HTTPS provider subdomains are allowed,
  but lookalike hosts, plain HTTP and arbitrary destinations remain denied.
  This is the site's anti-abuse/session assessment, not an app analytics SDK;
  its device/connection assessment is disclosed in the bundled privacy policy.
- JavaScript, DOM storage and third-party cookies are enabled only as necessary
  in the dedicated verification WebView. Browser tickets/provider cookies may
  persist. Native drafts never save CAPTCHA answers. Existing browser verification
  cookies are not overwritten when priming the widget from the app's cookie jar.
- The posting client copies WebView cookies only for HTTPS on `sys.4chan.org`;
  browser `pass_*` cookies cannot replace native Pass authentication. The actual
  request must target port 443 and `/{board}/post`. It uses the WebView user agent
  and board Origin/Referer, preserves existing API user-agent behavior, and never
  follows redirects or retries uncertain submissions.
- Attachments are copied into per-selection cache directories; a failed replacement
  preserves the previous copy. A missing/unreadable file causes an explicit error,
  never a silent text-only submission. Cleanup is limited to the app upload cache,
  not the user's source file. The existing picker copy remains synchronous; this
  is not a claim that large/cloud-provider attachment performance was modernized.
- The local deletion password is randomly generated and stored in app preferences
  instead of using a universal hard-coded password. No new Gradle dependencies or
  bundled third-party runtimes were introduced.

## Validation boundaries

`PostingProtocolTest` uses local responses and MockWebServer to cover expiry,
binding, multipart encoding, attachments, malformed/error responses and success
receipts. `PostingFlowTest` uses a local widget fixture for explicit confirmation,
reload, draft/attachment restoration, cancellation, rotation, duplicate-attempt
protection and cookie/header scoping. The dialog must have a visibly sized WebView,
not merely a loaded document. CI never requests real challenges or posts messages.

An optional physical-device preview can load the live challenge for a human:

```powershell
$env:ANDROID_SERIAL = "YOUR_DEVICE_SERIAL"
.\gradlew.bat :mimi-app:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=com.emogoth.android.phone.mimi.activity.PostingFlowTest#liveCaptchaCanBeSolvedWithoutPosting' `
  '-Pandroid.testInstrumentationRunnerArguments.livePostingCaptcha=true'
```

Optionally pass `-Pandroid.testInstrumentationRunnerArguments.livePostingThread=ID`
to preview verification for a reply to an existing `/g/` thread rather than a
new thread. The site may impose a waiting period or email verification before
issuing a challenge. Showing that restriction is not a completed verification.

This preview has **no posting listener/API attached**: its confirmation button
only checks that the user supplied a completed result. A real public submission
still requires agreement with the owner on content/destination and explicit
confirmation. Passing the fixture suite is not proof of successful public posting
or of the verification provider accepting every WebView/device/network.

### Physical-device result, 20 October 2026

The Motorola Edge 20 / Android 12 / WebView 155 loaded the real challenge after
the site's waiting period. The owner manually completed its four tasks; native
confirmation delivered the completed result and the isolated preview passed
(`OK, 1 test`). No public post was sent and the site's posting server did not
validate a submission. This confirms widget loading, human interaction and
native result delivery on this device, not successful public posting.

Earlier previews exposed a zero-height custom panel, interception of the local
bootstrap document, and the denial of Spur/Monocle after the waiting period.
Those attempts were interrupted or timed out, not counted as passing. The final
fixture uses the production document/resource path and asserts visible sizing;
protocol tests include provider hosts and lookalike-host rejection. Waiting
periods remain the site's rules and are neither shortened nor bypassed.

Local checks also passed: 94 app unit tests, eight library unit tests, 22 CI-helper
tests, and the complete physical-device run (`OK, 39 tests`: 36 mandatory checks,
three opt-in skips). Debug lint reports zero errors, 1,019 warnings and
one hint (487 unused-resource warnings). The dedicated widget explicitly enables
JavaScript with a scoped `SetJavaScriptEnabled` annotation; no lint error baseline
or broad suppression was added. The
obsolete CAPTCHA resources and JavaScript bridge are removed. The native posting
server, physical Android 10 interaction, and this commit's remote matrix remain
separate acceptance steps.

The final minified release APK and AAB also build successfully, including vital
lint. Local unsigned sizes are 59,400,570 and 36,459,792 bytes respectively;
these are artifact measurements, not a performance improvement claim. SBOM
freshness/negative validation passes with the same 154 packaged components and
unchanged dependency snapshots. `minSdk 23`, target API 36 and application IDs
remain unchanged. The updated debug APK was installed over the existing phone
installation without clearing user data; the original stay-awake value was
restored after testing.

### Authorized public reply and confirmation UX

Later on 20 October the owner explicitly authorized posting in `/g/110111399`
and prepared a reply in the actual thread composer. After the owner completed the
CAPTCHA manually, one native confirmation produced one POST and the official
receipt for post `110119159`. A separate read of the public thread JSON confirmed
that post existed. No further public test messages were sent. This establishes
one real text-only reply on Android 12, not attachment/new-thread coverage or
universal provider/device acceptance.

The original reported loop did not reproduce when using native confirmation:
the widget's **Done** completes verification, while **Get Captcha** requests
another challenge instead of sending the draft. To distinguish those actions,
the native button now says **Send message**, remains disabled until verification
is ready, and displays an explicit completion instruction. Readiness polling
returns only state, stops while the dialog is inactive, and never sends messages
or requests a replacement challenge. A new challenge invalidates pending reads.
The full proof is read only on the user's explicit send action.

An additional offline instrumented test now drives the real `PostFragment` Send
action through the local widget into Retrofit multipart submission. Its client
intercepts before the network, verifies the draft/CAPTCHA fields, returns a
simulated server rejection, and checks one attempt, retained draft, re-enabled
form and no unsolicited replacement dialog. The initial assertion incorrectly
assumed multipart parts had no intermediate headers; it was corrected to accept
valid part headers, not by weakening the request/field checks. Production factories
use the normal client/widget; only the test subclass substitutes these boundaries.

The updated source compiles and its 94 app unit tests and debug lint pass. An
attempt to run the 40-case device suite was stopped after the phone locked:
ActivityScenario could not reach RESUMED behind the keyguard. That interrupted
run is not a passing regression run. After the owner unlocked the phone, the
complete suite passed: 40 cases, 37 passed, three expected opt-in skips, zero
failures/errors. This includes all six offline posting-flow tests, the readiness
button behavior and the corrected multipart assertions. The same run passed all
94 app unit tests. The stay-awake setting was restored to its original value, 0.
No more public replies were sent during this regression run.
