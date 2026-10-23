package com.emogoth.android.phone.mimi.prefs

import android.app.Activity
import android.app.Instrumentation
import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.annotation.StringRes
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercise the actual preferences; intercept every external intent, never email or browse. */
@RunWith(AndroidJUnit4::class)
class MoreInfoPrefsTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val links = linkedMapOf(
        R.string.website_pref to "https://github.com/jagumiel/mimi-reader",
        R.string.project_license_pref to "https://github.com/jagumiel/mimi-reader/blob/master/LICENSE",
        R.string.credit_eli_pref to "https://github.com/econnelly",
        R.string.credit_original_account_pref to "https://github.com/MimiReader",
        R.string.credit_blatzar_pref to "https://github.com/Blatzar",
        R.string.credit_maintainer_pref to "https://github.com/jagumiel",
        R.string.credit_provenance_pref to "https://github.com/maestro12"
    )

    private class OutgoingMonitor(var failure: RuntimeException? = null) : Instrumentation.ActivityMonitor() {
        val intents = mutableListOf<Intent>()
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult {
            intents += Intent(intent)
            failure?.let { throw it }
            return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
        }
    }

    private fun screen(activity: SettingsActivity): MoreInfoPrefsFragment =
        activity.supportFragmentManager.findFragmentById(R.id.settings) as MoreInfoPrefsFragment

    private fun preference(activity: SettingsActivity, @StringRes key: Int): Preference =
        screen(activity).findPreference<Preference>(activity.getString(key))!!

    private fun openScreen(activity: SettingsActivity) {
        val manager = activity.supportFragmentManager
        manager.executePendingTransactions()
        val root = manager.findFragmentById(R.id.settings) as SettingsFragment
        val moreInfo = (0 until root.preferenceScreen.preferenceCount)
            .map { root.preferenceScreen.getPreference(it) }
            .single { it.fragment == MoreInfoPrefsFragment::class.java.name }
        assertTrue(activity.onPreferenceStartFragment(root, moreInfo))
        manager.executePendingTransactions()
        assertEquals(1, manager.backStackEntryCount)
    }

    @Test fun linksAndCreditsUseCurrentDestinationsWithoutSharingDiagnostics() {
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        val monitor = OutgoingMonitor()
        instrumentation.addMonitor(monitor)
        try {
            scenario.onActivity { activity ->
                val before = PreferenceManager.getDefaultSharedPreferences(activity).all.toMap()
                openScreen(activity)
                assertTrue("Opening More Info must not launch a browser", monitor.intents.isEmpty())
                assertNull(screen(activity).findPreference<Preference>("rate_pref"))
                assertNull(screen(activity).findPreference<Preference>("feedback_pref"))
                assertFalse(preference(activity, R.string.project_status_pref).isSelectable)
                assertTrue(preference(activity, R.string.project_status_pref).summary!!.contains("no longer available on Google Play"))
                assertEquals("Eli Connelly (@econnelly)", preference(activity, R.string.credit_eli_pref).title)
                assertEquals("@jagumiel", preference(activity, R.string.credit_maintainer_pref).title)
                assertTrue(preference(activity, R.string.credit_provenance_pref).summary!!.contains("not an attribution of original authorship"))
                assertTrue(preference(activity, R.string.project_license_pref).summary!!.contains("Apache License 2.0"))
                assertEquals(BuildConfig.VERSION_NAME, preference(activity, R.string.version_pref).summary)
                for ((key, url) in links) {
                    val pref = preference(activity, key)
                    assertFalse(pref.isPersistent)
                    pref.performClick()
                    val intent = monitor.intents.last()
                    assertEquals(Intent.ACTION_VIEW, intent.action)
                    assertEquals(url, intent.dataString)
                    assertNull(intent.extras)
                    assertNull(intent.component)
                    assertNull(intent.`package`)
                    assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                assertEquals(links.size, monitor.intents.size)
                assertTrue("More Info must not modify saved preferences",
                    before == PreferenceManager.getDefaultSharedPreferences(activity).all)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            scenario.close()
        }
    }

    @Test fun absentOrBlockedBrowserDoesNotCrashOrFallBackToEmailOrMarket() {
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        val monitor = OutgoingMonitor()
        instrumentation.addMonitor(monitor)
        try {
            scenario.onActivity { activity ->
                openScreen(activity)
                val before = PreferenceManager.getDefaultSharedPreferences(activity).all.toMap()
                for (failure in listOf(ActivityNotFoundException("fixture: no browser"),
                    SecurityException("fixture: blocked browser"))) {
                    monitor.failure = failure
                    for (key in links.keys) preference(activity, key).performClick()
                }
                assertEquals(links.values.toList() + links.values.toList(), monitor.intents.map { it.dataString })
                assertTrue(monitor.intents.all { it.action == Intent.ACTION_VIEW && it.extras == null })
                assertTrue("Failed navigation must not modify saved preferences",
                    before == PreferenceManager.getDefaultSharedPreferences(activity).all)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            scenario.close()
        }
    }

    @Test fun recreatedScreenRetainsLinksAndBackReturnsToSettingsWithoutChangingPreferences() {
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        val monitor = OutgoingMonitor()
        instrumentation.addMonitor(monitor)
        var before: Map<String, *> = emptyMap<String, Any>()
        try {
            scenario.onActivity { activity ->
                before = PreferenceManager.getDefaultSharedPreferences(activity).all.toMap()
                openScreen(activity)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                assertEquals(1, activity.supportFragmentManager.backStackEntryCount)
                for ((key, url) in links) {
                    preference(activity, key).performClick()
                    assertEquals(url, monitor.intents.last().dataString)
                }
                activity.onBackPressedDispatcher.onBackPressed()
                activity.supportFragmentManager.executePendingTransactions()
                assertTrue(activity.supportFragmentManager.findFragmentById(R.id.settings) is SettingsFragment)
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
                assertTrue("Recreation and Back must not modify saved preferences",
                    before == PreferenceManager.getDefaultSharedPreferences(activity).all)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            scenario.close()
        }
    }

    @Test fun localPrivacyLicensesAndHistoricalChangelogStillOpenAndRestore() {
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        val monitor = OutgoingMonitor()
        instrumentation.addMonitor(monitor)
        try {
            scenario.onActivity { openScreen(it) }
            for (key in listOf(R.string.privacy_pref, R.string.licenses_pref, R.string.changelog_pref)) {
                scenario.onActivity { activity ->
                    preference(activity, key).performClick()
                    activity.supportFragmentManager.executePendingTransactions()
                    val dialog = activity.supportFragmentManager.fragments
                        .filterIsInstance<androidx.fragment.app.DialogFragment>().single()
                    assertTrue(dialog.requireDialog().isShowing)
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val manager = activity.supportFragmentManager
                    manager.executePendingTransactions()
                    val dialog = manager.fragments.filterIsInstance<androidx.fragment.app.DialogFragment>().single()
                    assertTrue(dialog.requireDialog().isShowing)
                    dialog.dismiss()
                    manager.executePendingTransactions()
                    assertEquals(1, manager.backStackEntryCount)
                    assertTrue(manager.findFragmentById(R.id.settings) is MoreInfoPrefsFragment)
                }
            }
            assertTrue("Local notices must not launch external apps", monitor.intents.isEmpty())
        } finally {
            instrumentation.removeMonitor(monitor)
            scenario.close()
        }
    }
}
