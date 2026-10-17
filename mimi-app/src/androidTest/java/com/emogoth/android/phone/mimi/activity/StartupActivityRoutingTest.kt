package com.emogoth.android.phone.mimi.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupActivityRoutingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val preferences: SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)
    private val preferenceKey = context.getString(R.string.start_activity_pref)
    private var originalPreference: Any? = null

    @Before
    fun rememberStartupPreference() {
        originalPreference = preferences.all[preferenceKey]
    }

    @After
    fun restoreStartupPreference() {
        val editor = preferences.edit()
        when (val value = originalPreference) {
            is String -> editor.putString(preferenceKey, value)
            else -> editor.remove(preferenceKey)
        }
        editor.commit()
    }

    @Test
    fun defaultStartupOpensTabsAndForwardsExtras() {
        preferences.edit().remove(preferenceKey).commit()

        val tabs = launchStartupAndWaitFor(TabsActivity::class.java, "default")
        try {
            assertEquals("default", tabs.intent.getStringExtra(TEST_EXTRA))
        } finally {
            finish(tabs)
        }
    }

    @Test
    fun configuredBrowserLayoutIsHonored() {
        preferences.edit()
                .putString(preferenceKey, StartupActivity.BROWSER_ACTIVITY)
                .commit()

        val browser = launchStartupAndWaitFor(PostItemListActivity::class.java, "browser")
        try {
            assertEquals("browser", browser.intent.getStringExtra(TEST_EXTRA))
        } finally {
            finish(browser)
        }
    }

    @Test
    fun unknownStartupPreferenceFallsBackToTabs() {
        preferences.edit().putString(preferenceKey, "removed-layout").commit()

        val tabs = launchStartupAndWaitFor(TabsActivity::class.java, "fallback")
        try {
            assertEquals("fallback", tabs.intent.getStringExtra(TEST_EXTRA))
        } finally {
            finish(tabs)
        }
    }

    private fun <T : Activity> launchStartupAndWaitFor(
            destination: Class<T>,
            extraValue: String
    ): T {
        val monitor = instrumentation.addMonitor(destination.name, null, false)
        val startupIntent = Intent(context, StartupActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(TEST_EXTRA, extraValue)

        val startup = instrumentation.startActivitySync(startupIntent)
        val destinationActivity = try {
            instrumentation.waitForMonitorWithTimeout(monitor, TIMEOUT_MS)
        } finally {
            instrumentation.removeMonitor(monitor)
        }

        assertTrue(startup.isFinishing)
        assertNotNull("Startup did not open ${destination.simpleName}", destinationActivity)
        return checkNotNull(destination.cast(destinationActivity))
    }

    private fun finish(activity: Activity) {
        instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
        instrumentation.waitForIdleSync()
    }

    private companion object {
        const val TEST_EXTRA = "startup-routing-test-extra"
        const val TIMEOUT_MS = 5_000L
    }
}
