package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.dialog.CaptchaDialog
import com.emogoth.android.phone.mimi.fourchan.PostingProtocol
import com.emogoth.android.phone.mimi.fragment.PostFragment
import com.emogoth.android.phone.mimi.util.Extras
import com.emogoth.android.phone.mimi.util.HttpClientFactory
import com.emogoth.android.phone.mimi.view.CaptchaWebView
import com.emogoth.android.phone.mimi.viewmodel.PostSubmissionViewModel
import io.reactivex.Single
import io.reactivex.subjects.SingleSubject
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** All challenges and submissions here are local fixtures, never production solves/posts. */
@RunWith(AndroidJUnit4::class)
class PostingFlowTest {
    private fun host() = ActivityScenario.launch<TabsActivity>(Intent(
        InstrumentationRegistry.getInstrumentation().targetContext, TabsActivity::class.java))

    class FixtureCaptchaDialog : CaptchaDialog() {
        override fun createCaptchaWebView(): CaptchaWebView = fixtureWebView(requireContext())
    }

    class FixturePostFragment : PostFragment() {
        val requests = AtomicInteger()
        val body = AtomicReference<String>()

        override fun createCaptchaDialog(board: String, thread: Long): CaptchaDialog = FixtureCaptchaDialog().apply {
            arguments = Bundle().apply { putString("board", board); putLong("thread", thread) }
        }

        override fun createPostingClient(): okhttp3.OkHttpClient = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            // Stop before the network. This fake CAPTCHA must never reach the site.
            requests.incrementAndGet()
            val buffer = okio.Buffer()
            chain.request().body!!.writeTo(buffer)
            body.set(buffer.readUtf8())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("<span id='errmsg'>Local simulated rejection</span>".toResponseBody("text/html".toMediaType())).build()
        }.build()
    }

    class FixtureCaptchaWebView(context: android.content.Context) : CaptchaWebView(context) {
        var loaded = CountDownLatch(1)
        override fun loadCaptchaPage(board: String, thread: Long) {
            loaded = CountDownLatch(1)
            super.loadCaptchaPage(board, thread)
        }
        init {
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.toString() == PostingProtocol.WIDGET_URL) {
                        return WebResourceResponse("application/javascript", "UTF-8", ByteArrayInputStream(FIXTURE.toByteArray()))
                    }
                    // Exercise the production document/resource policy, not a
                    // permissive test-only replacement for the local document.
                    return interceptCaptchaRequest(request)
                }
                override fun onPageFinished(view: WebView?, url: String?) { loaded.countDown() }
            }
        }
    }

    companion object {
        private const val FIXTURE = """
            window.TCaptcha={
              init:function(node){this.node=node;this.challengeIdNode=document.createElement('input');this.respNode=document.createElement('input');node.textContent='Local test challenge only';},
              setErrorCb:function(callback){this.error=callback;},
              buildFromJson:function(data){this.challengeIdNode.value=data.challenge;this.respNode.value='';this.tasks=data.tasks;this.done=false;},
              onReloadClick:function(){this.buildFromJson({challenge:'fixture-only',ttl:120,tasks:[{}]});},
              isDone:function(){return this.done;}
            };
        """
        fun fixtureWebView(context: android.content.Context): CaptchaWebView = FixtureCaptchaWebView(context)
    }

    private fun js(scenario: ActivityScenario<TabsActivity>, view: CaptchaWebView, code: String): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        scenario.onActivity { view.evaluateJavascript(code) { result.set(it); latch.countDown() } }
        assertTrue("WebView result timed out", latch.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun ready(scenario: ActivityScenario<TabsActivity>, view: CaptchaWebView) {
        assertTrue("Local CAPTCHA document did not finish loading", (view as FixtureCaptchaWebView).loaded.await(10, TimeUnit.SECONDS))
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (js(scenario, view, "!!(window.TCaptcha && TCaptcha.challengeIdNode && TCaptcha.challengeIdNode.value)") == "true") return
            SystemClock.sleep(50)
        }
        fail("Local CAPTCHA fixture did not load")
    }

    private fun confirmReady(scenario: ActivityScenario<TabsActivity>, dialog: CaptchaDialog) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var enabled = false
        while (!enabled && SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { enabled = (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled }
            SystemClock.sleep(25)
        }
        assertTrue("Completed verification must enable the explicit Send message button", enabled)
        scenario.onActivity { (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick() }
    }

    private fun assertFullyVisible(view: View) {
        val bounds = Rect()
        val ancestry = generateSequence(view) { it.parent as? View }.joinToString(" -> ") {
            "${it.javaClass.simpleName}(h=${it.height},measured=${it.measuredHeight},min=${it.minimumHeight})"
        }
        assertTrue("${view.resources.getResourceEntryName(view.id)} must be on screen: $ancestry", view.getGlobalVisibleRect(bounds))
        assertEquals("The control must not be clipped horizontally", view.width, bounds.width())
        assertEquals("The control must not be clipped vertically", view.height, bounds.height())
        val frame = Rect()
        val location = IntArray(2)
        view.getWindowVisibleDisplayFrame(frame)
        view.getLocationOnScreen(location)
        assertTrue("The control must remain inside the visible window: $frame",
            frame.contains(Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)))
    }

    private fun assertActionsVisible(dialog: CaptchaDialog) {
        val alert = dialog.requireDialog() as AlertDialog
        for (which in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEUTRAL, AlertDialog.BUTTON_NEGATIVE)) {
            assertFullyVisible(alert.getButton(which))
        }
    }

    @Test fun manualAnswerAndExpiryAreReadFromTheWidgetWithoutAnAndroidBridge() {
        val scenario = host()
        lateinit var view: CaptchaWebView
        try {
            scenario.onActivity { activity ->
                view = fixtureWebView(activity)
                activity.setContentView(view)
                view.loadCaptchaPage("g", 42)
                assertFalse(view.settings.allowFileAccess)
                assertFalse(view.settings.allowContentAccess)
            }
            ready(scenario, view)
            val pending = js(scenario, view, "JSON.stringify(window.mimiCaptchaSnapshot())")
            assertThrows(RuntimeException::class.java) { PostingProtocol.answerFromWebView(pending, "g", 42, System.currentTimeMillis()) }
            assertEquals("\"undefined\"", js(scenario, view, "typeof Android"))
            js(scenario, view, "TCaptcha.respNode.value='fixture-user-answer'; TCaptcha.done=true;")
            val solved = js(scenario, view, "JSON.stringify(window.mimiCaptchaSnapshot())")
            assertEquals("fixture-user-answer", PostingProtocol.answerFromWebView(solved, "g", 42, System.currentTimeMillis()).response)
            js(scenario, view, "TCaptcha.buildFromJson({challenge:'fixture-only',ttl:0.001,tasks:[{}]})")
            val expired = js(scenario, view, "JSON.stringify(window.mimiCaptchaSnapshot())")
            assertThrows(RuntimeException::class.java) { PostingProtocol.answerFromWebView(expired, "g", 42, System.currentTimeMillis()) }
        } finally {
            scenario.onActivity { view.destroy() }
            scenario.close()
        }
    }

    @Test fun captchaConfirmationRequiresACompletedAnswerAndCanReloadOrCancel() {
        val scenario = host()
        val results = AtomicInteger()
        lateinit var dialog: FixtureCaptchaDialog
        lateinit var view: CaptchaWebView
        try {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(CaptchaDialog.RESULT, activity) { _, _ -> results.incrementAndGet() }
                dialog = FixtureCaptchaDialog().apply { arguments = Bundle().apply { putString("board", "g"); putLong("thread", 42) } }
                dialog.showNow(activity.supportFragmentManager, "fixture-captcha")
                view = dialog.requireDialog().findViewById(R.id.captch_webview)
            }
            ready(scenario, view)
            scenario.onActivity { assertTrue("The CAPTCHA must be visible, not just loaded", view.height >= (160 * view.resources.displayMetrics.density).toInt()) }
            scenario.onActivity {
                assertActionsVisible(dialog)
                val visible = Rect()
                assertTrue(view.getGlobalVisibleRect(visible))
                assertTrue("At least 160 dp of the challenge must actually be visible",
                    visible.height() >= (160 * view.resources.displayMetrics.density).toInt())
            }
            scenario.onActivity {
                assertFalse((dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            SystemClock.sleep(200)
            scenario.onActivity { assertTrue(dialog.isAdded); assertEquals(0, results.get()) }
            js(scenario, view, "TCaptcha.respNode.value='fixture-user-answer';TCaptcha.done=true")
            scenario.onActivity { (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEUTRAL).performClick() }
            ready(scenario, view)
            assertThrows(RuntimeException::class.java) {
                PostingProtocol.answerFromWebView(js(scenario, view, "JSON.stringify(window.mimiCaptchaSnapshot())"), "g", 42, System.currentTimeMillis())
            }
            js(scenario, view, "TCaptcha.respNode.value='fixture-user-answer';TCaptcha.done=true")
            confirmReady(scenario, dialog)
            val deadline = SystemClock.uptimeMillis() + 2000
            while (results.get() == 0 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
            assertEquals(1, results.get()) // Only a result listener; there is no production submission here.
        } finally { scenario.close() }
    }

    @Test fun nestedCaptchaKeepsActionsAndBrowserReachableInAShortWindowAndAfterRecreation() {
        val scenario = host()
        lateinit var dialog: FixtureCaptchaDialog
        lateinit var view: CaptchaWebView
        try {
            scenario.onActivity { activity ->
                val fragment = FixturePostFragment().apply { arguments = Bundle().apply {
                    putString(Extras.EXTRAS_BOARD_NAME, "g"); putLong(Extras.EXTRAS_THREAD_ID, 42)
                    putString(Extras.EXTRAS_POST_COMMENT, "preserved compact-window draft")
                } }
                fragment.showNow(activity.supportFragmentManager, "compact-posting-fixture")
                fragment.requireView().findViewById<View>(R.id.submit_button).performClick()
                fragment.childFragmentManager.executePendingTransactions()
                dialog = fragment.childFragmentManager.findFragmentByTag("captcha_dialog") as FixtureCaptchaDialog
                view = dialog.requireDialog().findViewById(R.id.captch_webview)
            }
            ready(scenario, view)
            val resized = CountDownLatch(1)
            scenario.onActivity {
                assertActionsVisible(dialog)
                // Model the reduced height available with an IME/small window,
                // without changing the owner's keyboard or display settings.
                val height = (380 * view.resources.displayMetrics.density).toInt()
                dialog.requireDialog().window!!.decorView.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                    override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, ol: Int, ot: Int, or: Int, ob: Int) {
                        if (v.height == height) {
                            v.removeOnLayoutChangeListener(this)
                            resized.countDown()
                        }
                    }
                })
                dialog.requireDialog().window!!.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    height)
            }
            assertTrue("The short window must actually finish layout before checking controls", resized.await(5, TimeUnit.SECONDS))
            scenario.onActivity {
                assertActionsVisible(dialog)
                val scroll = dialog.requireDialog().findViewById<ScrollView>(R.id.captcha_content_scroll)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            scenario.onActivity {
                assertActionsVisible(dialog)
                assertFullyVisible(dialog.requireDialog().findViewById(R.id.captcha_open_browser))
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("compact-posting-fixture") as FixturePostFragment
                dialog = fragment.childFragmentManager.findFragmentByTag("captcha_dialog") as FixtureCaptchaDialog
                view = dialog.requireDialog().findViewById(R.id.captch_webview)
                assertEquals("preserved compact-window draft", fragment.saveState().getString(Extras.EXTRAS_POST_COMMENT))
                assertEquals(0, fragment.requests.get())
            }
            ready(scenario, view)
            scenario.onActivity {
                assertActionsVisible(dialog)
                (dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("compact-posting-fixture") as FixturePostFragment
                assertEquals("preserved compact-window draft", fragment.saveState().getString(Extras.EXTRAS_POST_COMMENT))
                assertEquals(0, fragment.requests.get())
            }
        } finally { scenario.close() }
    }

    @Test fun draftAndCachedAttachmentSurviveRecreationAndCaptchaCancellation() {
        val scenario = host()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.cacheDir, "upload/posting-fixture-${System.nanoTime()}").apply { mkdirs() }
        val attachment = File(directory, "fixture.png")
        attachment.outputStream().use { output ->
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        try {
            scenario.onActivity { activity ->
                val fragment = PostFragment().apply { arguments = Bundle().apply {
                    putString(Extras.EXTRAS_BOARD_NAME, "g"); putLong(Extras.EXTRAS_THREAD_ID, 42)
                    putString(Extras.EXTRAS_POST_COMMENT, "original draft"); putString("file", attachment.absolutePath)
                } }
                fragment.showNow(activity.supportFragmentManager, "posting-fixture")
                fragment.requireView().findViewById<EditText>(R.id.comment_input).setText("draft after edit")
                fragment.requireView().findViewById<EditText>(R.id.subject_input).setText("edited subject")
                val captcha = FixtureCaptchaDialog().apply { arguments = Bundle().apply { putString("board", "g"); putLong("thread", 42) } }
                captcha.showNow(fragment.childFragmentManager, "captcha_dialog")
                (captcha.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
                fragment.childFragmentManager.executePendingTransactions()
                assertEquals("draft after edit", fragment.saveState().getString(Extras.EXTRAS_POST_COMMENT))
                assertTrue(attachment.exists())
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("posting-fixture") as PostFragment
                assertEquals("draft after edit", fragment.requireView().findViewById<EditText>(R.id.comment_input).text.toString())
                assertEquals("edited subject", fragment.saveState().getString("subject"))
                assertEquals(attachment.absolutePath, fragment.saveState().getString("file"))
                assertEquals(View.VISIBLE, fragment.requireView().findViewById<View>(R.id.image_container).visibility)
            }
        } finally { scenario.close(); attachment.delete(); directory.delete() }
    }

    @Test fun captchaActionsRemainVisibleWithTheKeyboardAndAfterLandscapeRotation() {
        // Real IME acceptance is explicit: an emulator with a hardware keyboard
        // may intentionally suppress it. The short-window regression above
        // always runs and does not depend on an installed/enabled soft keyboard.
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("postingKeyboard") == "true")
        val scenario = host()
        lateinit var dialog: FixtureCaptchaDialog
        lateinit var view: CaptchaWebView
        var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        try {
            scenario.onActivity { activity ->
                originalOrientation = activity.requestedOrientation
                dialog = FixtureCaptchaDialog().apply { arguments = Bundle().apply {
                    putString("board", "g"); putLong("thread", 42)
                } }
                dialog.showNow(activity.supportFragmentManager, "keyboard-captcha-fixture")
                view = dialog.requireDialog().findViewById(R.id.captch_webview)
                view.requestFocus()
            }
            ready(scenario, view)
            // Only a synthetic local input: no site challenge is solved/sent.
            val originalFrame = Rect()
            scenario.onActivity { view.getWindowVisibleDisplayFrame(originalFrame) }
            js(scenario, view, "TCaptcha.respNode.style.cssText='position:fixed;top:8px;left:8px;width:180px;height:40px';document.body.appendChild(TCaptcha.respNode)")
            val point = IntArray(2)
            scenario.onActivity {
                dialog.requireDialog().findViewById<ScrollView>(R.id.captcha_content_scroll).scrollTo(0, view.top)
            }
            scenario.onActivity {
                view.getLocationOnScreen(point)
                point[0] += (30 * view.resources.displayMetrics.density).toInt()
                point[1] += (25 * view.resources.displayMetrics.density).toInt()
            }
            // Chromium requires a user gesture, not evaluateJavascript focus,
            // to initiate text entry. Tap the synthetic input as a user would.
            val down = SystemClock.uptimeMillis()
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    point[0].toFloat(), point[1].toFloat(), 0)
                try { InstrumentationRegistry.getInstrumentation().sendPointerSync(event) } finally { event.recycle() }
            }
            val deadline = SystemClock.uptimeMillis() + 5000
            var keyboardVisible = false
            val keyboardFrame = Rect()
            while (!keyboardVisible && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    view.getWindowVisibleDisplayFrame(keyboardFrame)
                    // Floating windows on older providers can omit IME insets;
                    // the visible display frame still reports the IME boundary.
                    keyboardVisible = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true ||
                        originalFrame.bottom - keyboardFrame.bottom > (100 * view.resources.displayMetrics.density).toInt()
                }
                SystemClock.sleep(25)
            }
            assertTrue("The local input must actually open the keyboard: $originalFrame -> $keyboardFrame", keyboardVisible)
            scenario.onActivity { activity ->
                assertActionsVisible(dialog)
                (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            val rotationDeadline = SystemClock.uptimeMillis() + 5000
            var landscape = false
            while (!landscape && SystemClock.uptimeMillis() < rotationDeadline) {
                scenario.onActivity { activity ->
                    landscape = activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                }
                SystemClock.sleep(25)
            }
            assertTrue("The activity must actually rotate to landscape", landscape)
            scenario.onActivity { activity ->
                dialog = activity.supportFragmentManager.findFragmentByTag("keyboard-captcha-fixture") as FixtureCaptchaDialog
                view = dialog.requireDialog().findViewById(R.id.captch_webview)
            }
            ready(scenario, view)
            scenario.onActivity {
                assertActionsVisible(dialog)
                assertFalse((dialog.requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                val scroll = dialog.requireDialog().findViewById<ScrollView>(R.id.captcha_content_scroll)
                scroll.scrollTo(0, scroll.getChildAt(0).height)
            }
            scenario.onActivity { assertFullyVisible(dialog.requireDialog().findViewById(R.id.captcha_open_browser)) }
        } finally {
            scenario.onActivity { activity ->
                val current = activity.supportFragmentManager.findFragmentByTag("keyboard-captcha-fixture") as? CaptchaDialog
                current?.dialog?.window?.decorView?.let {
                    (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(it.windowToken, 0)
                }
                activity.requestedOrientation = originalOrientation
            }
            scenario.close()
        }
    }

    @Test fun anInFlightAttemptSurvivesRotationAndRejectsADuplicateSubmission() {
        val scenario = host()
        val calls = AtomicInteger()
        val response = SingleSubject.create<PostingProtocol.Receipt>()
        lateinit var original: PostSubmissionViewModel
        try {
            scenario.onActivity { activity ->
                original = ViewModelProvider(activity)[PostSubmissionViewModel::class.java]
                original.submit(Single.defer { calls.incrementAndGet(); response })
                original.submit(Single.fromCallable { calls.incrementAndGet(); PostingProtocol.Receipt(42, 43) })
                assertTrue(original.isSending)
            }
            scenario.recreate()
            scenario.onActivity { activity -> assertSame(original, ViewModelProvider(activity)[PostSubmissionViewModel::class.java]) }
            response.onError(java.io.IOException("Local simulated rejection"))
            val deadline = SystemClock.uptimeMillis() + 3000
            while (original.state.value?.error == null && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(25)
            assertEquals(1, calls.get())
            assertNotNull(original.state.value?.error)
            scenario.onActivity { assertFalse(original.isSending) }
        } finally { scenario.close() }
    }

    @Test fun confirmingCaptchaSubmitsTheActualDraftOnceAndKeepsARejectedDraft() {
        val scenario = host()
        lateinit var fragment: FixturePostFragment
        lateinit var dialog: FixtureCaptchaDialog
        lateinit var view: CaptchaWebView
        try {
            scenario.onActivity { activity ->
                fragment = FixturePostFragment().apply { arguments = Bundle().apply {
                    putString(Extras.EXTRAS_BOARD_NAME, "g"); putLong(Extras.EXTRAS_THREAD_ID, 42)
                    putString(Extras.EXTRAS_POST_COMMENT, "offline posting fixture")
                } }
                fragment.showNow(activity.supportFragmentManager, "posting-pipeline-fixture")
                fragment.requireView().findViewById<View>(R.id.submit_button).performClick()
                fragment.childFragmentManager.executePendingTransactions()
                dialog = fragment.childFragmentManager.findFragmentByTag("captcha_dialog") as FixtureCaptchaDialog
                view = dialog.requireDialog().findViewById(R.id.captch_webview)
            }
            ready(scenario, view)
            js(scenario, view, "TCaptcha.respNode.value='fixture-user-answer';TCaptcha.done=true")
            confirmReady(scenario, dialog)
            val deadline = SystemClock.uptimeMillis() + 5000
            var rejected = false
            while (!rejected && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    rejected = fragment.requireView().findViewById<android.widget.TextView>(R.id.post_error).text.contains("Local simulated rejection")
                }
                SystemClock.sleep(25)
            }
            assertTrue("The CAPTCHA confirmation must reach the posting API", rejected)
            assertEquals(1, fragment.requests.get())
            assertTrue(Regex("name=\"t-response\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\nfixture-user-answer\\r\\n").containsMatchIn(fragment.body.get()))
            assertTrue(Regex("name=\"com\"\\r\\n(?:[^\\r\\n]+\\r\\n)*\\r\\noffline posting fixture\\r\\n").containsMatchIn(fragment.body.get()))
            scenario.onActivity {
                assertTrue(fragment.isAdded)
                assertEquals("offline posting fixture", fragment.requireView().findViewById<EditText>(R.id.comment_input).text.toString())
                assertTrue(fragment.requireView().findViewById<View>(R.id.submit_button).isEnabled)
                assertNull(fragment.childFragmentManager.findFragmentByTag("captcha_dialog"))
            }
        } finally { scenario.close() }
    }

    @Test fun postingCookiesAndBrowserHeadersStayOnTheExactPostingHost() {
        val client = HttpClientFactory.getInstance().getPostingClient("fixture-UA", "mimi_fixture=yes; pass_enabled=browser-only-stale", "https://boards.4chan.org/g/thread/42")
        assertFalse(client.followRedirects); assertFalse(client.followSslRedirects); assertFalse(client.retryOnConnectionFailure)
        assertTrue(client.cookieJar.loadForRequest("https://sys.4chan.org/g/post".toHttpUrl()).any { it.name == "mimi_fixture" })
        assertFalse(client.cookieJar.loadForRequest("https://evil.test/g/post".toHttpUrl()).any { it.name == "mimi_fixture" })
        assertFalse(client.cookieJar.loadForRequest("https://sys.4chan.org/g/post".toHttpUrl()).any { it.name == "pass_enabled" && it.value == "browser-only-stale" })
        val captured = AtomicReference<Request>()
        val noNetwork = client.newBuilder().addInterceptor { chain ->
            captured.set(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Fixture")
                .body("fixture only".toResponseBody("text/plain".toMediaType())).build()
        }.build()
        noNetwork.newCall(Request.Builder().url("https://sys.4chan.org/g/post").post("fixture".toRequestBody(null)).build()).execute().close()
        assertEquals("fixture-UA", captured.get().header("User-Agent"))
        assertEquals("https://boards.4chan.org", captured.get().header("Origin"))
        assertThrows(java.io.IOException::class.java) {
            noNetwork.newCall(Request.Builder().url("https://evil.test/g/post").build()).execute()
        }
        assertThrows(java.io.IOException::class.java) {
            noNetwork.newCall(Request.Builder().url("https://sys.4chan.org:8443/g/post").build()).execute()
        }
    }

    @Test fun liveCaptchaCanBeSolvedWithoutPosting() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("livePostingCaptcha") == "true")
        val scenario = host()
        val solved = CountDownLatch(1)
        try {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.setFragmentResultListener(CaptchaDialog.RESULT, activity) { _, result ->
                    // Only record that the user supplied a result. No posting API is called.
                    if (!result.getString("challenge").isNullOrEmpty()) solved.countDown()
                }
                val thread = InstrumentationRegistry.getArguments().getString("livePostingThread")?.toLongOrNull() ?: 0L
                val dialog = CaptchaDialog.newInstance("g", thread)
                dialog.showNow(activity.supportFragmentManager, "live-captcha-no-post")
                dialog.requireDialog().setTitle("CAPTCHA preview — no message will be posted")
            }
            assertTrue("Manual verification did not finish; no message was posted", solved.await(150, TimeUnit.SECONDS))
        } finally { scenario.close() }
    }
}
