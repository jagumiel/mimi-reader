package com.emogoth.android.phone.mimi.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.net.Uri
import android.text.TextPaint
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.browser.customtabs.CustomTabsIntent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.TabsActivity
import com.emogoth.android.phone.mimi.span.LinkSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Intercept all outgoing intents: no real browser, web requests, or preference writes. */
@RunWith(AndroidJUnit4::class)
class BrowserNavigatorTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val url = "https://example.invalid/path?q=mimi%20reader#reply"

    private class RecordingContext(base: Context, failures: List<RuntimeException> = emptyList()) : ContextWrapper(base) {
        val intents = mutableListOf<Intent>()
        private val failures = failures.toMutableList()
        var externalPreference: Boolean? = null

        override fun startActivity(intent: Intent) {
            intents += Intent(intent)
            if (failures.isNotEmpty()) throw failures.removeAt(0)
        }

        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val actual = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by actual {
                override fun getBoolean(key: String?, defValue: Boolean): Boolean =
                    if (key == getString(R.string.open_links_externally_pref))
                        externalPreference ?: actual.getBoolean(key, defValue)
                    else actual.getBoolean(key, defValue)
            }
        }
    }

    @Test fun customTabIntentPreservesUrlAndToolbarColor() = instrumentation.runOnMainSync {
        val recording = RecordingContext(context)
        assertTrue(BrowserNavigator.open(recording, url, true, Color.CYAN))
        val intent = recording.intents.single()
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(url), intent.data)
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(Color.CYAN, intent.getIntExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR, 0))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertNull(intent.component)
        assertNull(intent.`package`)
    }

    @Test fun linkSpanHonorsExternalPreferenceWithoutChangingIt() = instrumentation.runOnMainSync {
        val actual = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.open_links_externally_pref)
        val before = actual.all[key]
        val recording = RecordingContext(context)
        val span = LinkSpan(url, Color.MAGENTA)
        for (external in listOf(false, true)) {
            recording.externalPreference = external
            span.onClick(TextView(recording))
            val intent = recording.intents.last()
            assertEquals(url, intent.dataString)
            assertEquals(!external, intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        }
        val paint = TextPaint()
        span.updateDrawState(paint)
        assertEquals(Color.MAGENTA, paint.color)
        assertTrue(paint.isUnderlineText)
        assertEquals(before, actual.all[key])
    }

    @Test fun nonWebUrisAndExplicitExternalRequestsRemainPlainIntents() = instrumentation.runOnMainSync {
        val recording = RecordingContext(context)
        for ((link, custom) in listOf(url to false, "mailto:test@example.invalid" to true,
            "tel:123" to true, "custom-test:fixture" to true)) {
            assertTrue(BrowserNavigator.open(recording, link, custom, Color.CYAN))
            val intent = recording.intents.last()
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(link, intent.dataString)
            assertFalse(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
            assertFalse(intent.hasExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR))
        }
        assertTrue(BrowserNavigator.open(recording, "HTTPS://example.invalid/", true, null))
        assertTrue(recording.intents.last().hasExtra(CustomTabsIntent.EXTRA_SESSION))
    }

    @Test fun failedCustomTabsFallBackAndMissingHandlersDoNotCrash() = instrumentation.runOnMainSync {
        for (error in listOf(ActivityNotFoundException("fixture"), SecurityException("fixture"))) {
            val fallback = RecordingContext(context, listOf(error))
            assertTrue(BrowserNavigator.open(fallback, url, true, Color.CYAN))
            assertEquals(2, fallback.intents.size)
            assertTrue(fallback.intents.first().hasExtra(CustomTabsIntent.EXTRA_SESSION))
            assertFalse(fallback.intents.last().hasExtra(CustomTabsIntent.EXTRA_SESSION))
            assertFalse(fallback.intents.last().hasExtra(CustomTabsIntent.EXTRA_TOOLBAR_COLOR))
            assertEquals(url, fallback.intents.last().dataString)

            val absent = RecordingContext(context, listOf(error, ActivityNotFoundException("fixture fallback")))
            assertFalse(BrowserNavigator.open(absent, url, true, null))
            assertEquals(2, absent.intents.size)
            val external = RecordingContext(context, listOf(error))
            assertFalse(BrowserNavigator.open(external, url, false, null))
            assertEquals(1, external.intents.size)
        }
    }

    @Test fun activityAndWrappedActivityContextsPreserveTheBackStack() {
        val scenario = ActivityScenario.launch<TabsActivity>(Intent(context, TabsActivity::class.java))
        try {
            scenario.onActivity { activity ->
                for (base in listOf(activity, ContextThemeWrapper(activity, R.style.Theme_Mimi_Light_Toolbar_Default))) {
                    val recording = RecordingContext(base)
                    for (custom in listOf(false, true)) {
                        assertTrue(BrowserNavigator.open(recording, url, custom, null))
                        assertEquals(0, recording.intents.last().flags and Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                }
            }
        } finally { scenario.close() }
    }
}
