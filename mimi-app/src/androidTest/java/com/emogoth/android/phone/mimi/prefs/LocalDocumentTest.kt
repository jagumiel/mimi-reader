package com.emogoth.android.phone.mimi.prefs

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.widget.Button
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.util.AppCacheCleaner
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real local documents/WebViews; never contact an external service or publish content. */
@RunWith(AndroidJUnit4::class)
class LocalDocumentTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun host() = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))

    private fun show(scenario: ActivityScenario<SettingsActivity>, fragment: LocalDocumentFragment) {
        scenario.onActivity { activity ->
            LocalDocumentFragment.showDocument(activity.supportFragmentManager, fragment, "local-document-test")
            activity.supportFragmentManager.executePendingTransactions()
        }
    }

    private fun document(activity: SettingsActivity) =
        activity.supportFragmentManager.findFragmentByTag("local-document-test") as LocalDocumentFragment

    private fun ready(scenario: ActivityScenario<SettingsActivity>): WebView {
        var result: WebView? = null
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity { activity ->
                val root = document(activity).requireView()
                assertEquals("A bundled document must not fail or lose its renderer", View.GONE,
                    root.findViewById<View>(R.id.local_document_error).visibility)
                val view = root.findViewById<WebView>(R.id.licensesFragmentWebView)
                if (view != null && view.isShown && view.width > 0 && view.height > 0 && view.contentHeight > 0 &&
                    root.findViewById<View>(R.id.licensesFragmentIndeterminateProgress).visibility == View.GONE) result = view
            }
            result?.let { return it }
            SystemClock.sleep(25)
        }
        throw AssertionError("Bundled document must render visibly within 15 seconds")
    }

    private fun contains(view: WebView, text: String) {
        val done = CountDownLatch(1)
        val matches = AtomicInteger()
        instrumentation.runOnMainSync {
            view.setFindListener { _, count, complete ->
                if (complete) { matches.set(count); done.countDown() }
            }
            view.findAllAsync(text)
        }
        assertTrue("Document text search must complete", done.await(5, TimeUnit.SECONDS))
        assertTrue("The actual rendered document must contain $text", matches.get() > 0)
        instrumentation.runOnMainSync { view.clearMatches(); view.setFindListener(null) }
    }

    private class Requests : Instrumentation.ActivityMonitor() {
        val intents = mutableListOf<Intent>()
        var failure: RuntimeException? = null
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            intents += Intent(intent)
            failure?.let { throw it }
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    private fun request(link: String, gesture: Boolean = true, mainFrame: Boolean = true) =
        object : WebResourceRequest {
            override fun getUrl() = Uri.parse(link)
            override fun isForMainFrame() = mainFrame
            override fun isRedirect() = false
            override fun hasGesture() = gesture
            override fun getMethod() = "GET"
            override fun getRequestHeaders() = emptyMap<String, String>()
        }

    @Test fun documentsAndLegacyClassNamesRenderActualTextAfterRecreation() {
        val fixtures = listOf(
            LicensesFragment.newInstance(R.raw.licenses) to "Adam Speakman",
            PrivacyPolicyFragment.newInstance() to "Mimi Reader privacy policy",
            LicensesFragment.newInstance(R.raw.changelog, "Historical changes") to "Mimi 8.0",
            com.emogoth.android.phone.mimi.fragment.LicensesFragment.newInstance(R.raw.changelog, "Legacy changes") to "Mimi 8.0",
            com.emogoth.android.phone.mimi.fragment.PrivacyPolicyFragment.newInstance() to "Mimi Reader privacy policy"
        )
        for ((fragment, text) in fixtures) {
            val scenario = host()
            try {
                show(scenario, fragment)
                contains(ready(scenario), text)
                if (fragment.arguments?.getInt("raw_res_extra") == R.raw.changelog) {
                    contains(ready(scenario), "Mimi 7.0.1")
                    contains(ready(scenario), "Mimi 7.0.0")
                }
                scenario.recreate()
                contains(ready(scenario), text)
                scenario.onActivity { activity ->
                    assertEquals(fragment.javaClass, document(activity).javaClass)
                    assertEquals(fragment.arguments?.getInt("raw_res_extra"),
                        document(activity).arguments?.getInt("raw_res_extra"))
                    assertEquals(fragment.arguments?.getString("title_extra"),
                        document(activity).arguments?.getString("title_extra"))
                    assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
                    document(activity).dismiss()
                    activity.supportFragmentManager.executePendingTransactions()
                    assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
                }
            } finally { scenario.close() }
        }
    }

    @Test fun documentWebViewIsOfflineAndOnlyUserWebLinksLeaveForTheBrowser() {
        val scenario = host()
        val outgoing = Requests()
        try {
            show(scenario, LicensesFragment.newInstance(R.raw.licenses))
            val view = ready(scenario)
            instrumentation.addMonitor(outgoing)
            try {
                scenario.onActivity {
                    with(view.settings) {
                        assertFalse(javaScriptEnabled); assertFalse(domStorageEnabled)
                        assertFalse(allowFileAccess); assertFalse(allowContentAccess)
                        assertTrue(blockNetworkLoads)
                    }
                    val client = WebViewCompat.getWebViewClient(view)
                    val documentUrl = view.url
                    assertFalse(client.shouldOverrideUrlLoading(view, request(LocalDocumentFragment.DOCUMENT_URL + "#notice")))
                    for (link in listOf("file:///data/local/fixture", "content://fixture/private", "javascript:alert(1)",
                        "intent://fixture#Intent;end", "data:text/html,fixture", "mailto:test@example.invalid")) {
                        assertTrue(client.shouldOverrideUrlLoading(view, request(link)))
                    }
                    val url = "https://example.invalid/license#notice"
                    assertTrue(client.shouldOverrideUrlLoading(view, request(url, gesture = false)))
                    assertTrue(client.shouldOverrideUrlLoading(view, request(url, mainFrame = false)))
                    assertTrue(outgoing.intents.isEmpty())
                    assertTrue(client.shouldOverrideUrlLoading(view, request(url)))
                    assertEquals(url, outgoing.intents.single().dataString)
                    assertEquals(Intent.ACTION_VIEW, outgoing.intents.single().action)
                    assertNull(outgoing.intents.single().extras)
                    for (failure in listOf(ActivityNotFoundException("fixture"), SecurityException("fixture"))) {
                        outgoing.failure = failure
                        assertTrue(client.shouldOverrideUrlLoading(view, request(url)))
                    }
                    assertEquals(3, outgoing.intents.size)
                    val blocked = client.shouldInterceptRequest(view, request("https://example.invalid/tracker.png"))!!
                    assertEquals(403, blocked.statusCode)
                    blocked.data.close()
                    val local = client.shouldInterceptRequest(view, request(LocalDocumentFragment.DOCUMENT_URL))!!
                    assertEquals(200, local.statusCode)
                    assertTrue(local.responseHeaders["Content-Security-Policy"]!!.contains("default-src 'none'"))
                    assertTrue(local.data.bufferedReader().use { it.readText() }.contains("Adam Speakman"))
                    assertEquals("External links must not replace the local document", documentUrl, view.url)
                }
            } finally { instrumentation.removeMonitor(outgoing) }
            contains(view, "Adam Speakman")
        } finally { scenario.close() }
    }

    @Test fun invalidDocumentShowsAnErrorAndExplicitRetryCanLoadAValidDocument() {
        val scenario = host()
        try {
            show(scenario, LicensesFragment.newInstance(0))
            val deadline = SystemClock.uptimeMillis() + 5000
            var visible = false
            while (!visible && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity { activity ->
                    visible = document(activity).requireView().findViewById<View>(R.id.local_document_error).isShown
                }
                if (!visible) SystemClock.sleep(25)
            }
            assertTrue("Missing resource must show a visible error instead of a blank document", visible)
            scenario.onActivity { activity ->
                val fragment = document(activity)
                assertEquals(View.GONE, fragment.requireView().findViewById<View>(R.id.licensesFragmentIndeterminateProgress).visibility)
                fragment.requireArguments().putInt("raw_res_extra", R.raw.licenses)
                fragment.requireView().findViewById<Button>(R.id.local_document_retry).performClick()
            }
            contains(ready(scenario), "Adam Speakman")
        } finally { scenario.close() }
    }

    @Test fun rendererFailureDiscardsTheAffectedWebViewAndRetryCreatesAnother() {
        val scenario = host()
        try {
            show(scenario, LicensesFragment.newInstance(R.raw.licenses))
            val oldView = ready(scenario)
            scenario.onActivity { activity ->
                val client = WebViewCompat.getWebViewClient(oldView)
                // Exercise recovery without crashing the real shared provider used by other tests.
                assertTrue(client.onRenderProcessGone(oldView, object : RenderProcessGoneDetail() {
                    override fun didCrash() = true
                    override fun rendererPriorityAtExit() = 0
                }))
                assertNull(oldView.parent)
                assertTrue(document(activity).requireView().findViewById<View>(R.id.local_document_error).isShown)
                document(activity).requireView().findViewById<Button>(R.id.local_document_retry).performClick()
            }
            val newView = ready(scenario)
            assertNotSame(oldView, newView)
            contains(newView, "Adam Speakman")
        } finally { scenario.close() }
    }

    class SlowDocumentFragment : LicensesFragment() {
        val started = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val reads = AtomicInteger()
        override fun readDocument(resources: Resources, resource: Int): String {
            if (reads.incrementAndGet() == 1) {
                started.countDown()
                try { CountDownLatch(1).await(10, TimeUnit.SECONDS) }
                catch (expected: InterruptedException) { interrupted.countDown() }
                return "<html><body>Retired read</body></html>"
            }
            return "<html><body>Current view only</body></html>"
        }
    }

    @Test fun destroyingTheViewCancelsPendingReadingAndDiscardsOldPageCallbacks() {
        val scenario = host()
        val fragment = SlowDocumentFragment()
        try {
            show(scenario, fragment)
            assertTrue(fragment.started.await(5, TimeUnit.SECONDS))
            scenario.onActivity { activity ->
                val oldView = fragment.requireView().findViewById<WebView>(R.id.licensesFragmentWebView)
                val oldClient = WebViewCompat.getWebViewClient(oldView)
                activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
                assertNull(fragment.view)
                assertNull(oldView.parent)
                activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
                oldClient.onPageFinished(oldView, LocalDocumentFragment.DOCUMENT_URL)
            }
            assertTrue("View destruction must cancel the pending I/O", fragment.interrupted.await(5, TimeUnit.SECONDS))
            contains(ready(scenario), "Current view only")
            assertEquals(2, fragment.reads.get())
        } finally { scenario.close() }
    }

    @Test fun clearingMediaCachePreservesTheLiveWebViewAndTheDocumentReloads() {
        val scenario = host()
        try {
            show(scenario, PrivacyPolicyFragment.newInstance())
            contains(ready(scenario), "Mimi Reader privacy policy")
            // Test thread, not UI: exercise the real cache clear after a WebView has been used.
            AppCacheCleaner.clear(context)
            scenario.recreate()
            contains(ready(scenario), "Mimi Reader privacy policy")
        } finally { scenario.close() }
    }
}
