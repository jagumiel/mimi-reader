package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import androidx.appcompat.view.ContextThemeWrapper
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.preference.ListPreference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.prefs.GeneralPrefsFragment
import com.emogoth.android.phone.mimi.prefs.SettingsActivity
import com.emogoth.android.phone.mimi.prefs.SettingsFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidXUiCompatibilityTest {
    @Test fun materialDialogsHaveLifecycleOwnersInAllThreeThemes() {
        val scenario = ActivityScenario.launch<TabsActivity>(Intent(
            InstrumentationRegistry.getInstrumentation().targetContext, TabsActivity::class.java))
        try {
            scenario.onActivity { activity ->
                for (theme in intArrayOf(R.style.Theme_Mimi_Light_Toolbar_Default,
                    R.style.Theme_Mimi_Dark_Toolbar_Default, R.style.Theme_Mimi_Black_Toolbar_Default)) {
                    val dialog = MaterialAlertDialogBuilder(ContextThemeWrapper(activity, theme))
                        .setTitle("Local compatibility fixture").setMessage("No network or preference changes")
                        .setPositiveButton(android.R.string.ok, null).create()
                    try {
                        dialog.show()
                        assertTrue(dialog.isShowing)
                        assertNotNull(dialog.window!!.decorView.findViewTreeLifecycleOwner())
                    } finally { dialog.dismiss() }
                }
            }
        } finally { scenario.close() }
    }

    @Test fun preferenceDialogAndNestedScreenSurviveRecreationAndBack() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(instrumentation.targetContext, SettingsActivity::class.java))
        var originalValue: String? = null
        try {
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val root = manager.findFragmentById(R.id.settings) as SettingsFragment
                val general = root.preferenceScreen.getPreference(0)
                assertTrue(activity.onPreferenceStartFragment(root, general))
                manager.executePendingTransactions()
                val screen = manager.findFragmentById(R.id.settings) as GeneralPrefsFragment
                val preference = screen.findPreference<ListPreference>(activity.getString(R.string.nav_drawer_bookmark_count_pref))!!
                originalValue = preference.value
                screen.onDisplayPreferenceDialog(preference)
                manager.executePendingTransactions()
                assertTrue(manager.fragments.any { it is PreferenceDialogFragmentCompat })
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val screen = manager.findFragmentById(R.id.settings) as GeneralPrefsFragment
                val preference = screen.findPreference<ListPreference>(activity.getString(R.string.nav_drawer_bookmark_count_pref))!!
                assertEquals(originalValue, preference.value)
                assertEquals(1, manager.backStackEntryCount)
                val dialogs = manager.fragments.filterIsInstance<PreferenceDialogFragmentCompat>()
                assertEquals(1, dialogs.size)
                assertTrue(dialogs.single().requireDialog().isShowing)
                dialogs.single().requireDialog().cancel()
                manager.executePendingTransactions()
                assertEquals(originalValue, preference.value)
                activity.onBackPressedDispatcher.onBackPressed()
                manager.executePendingTransactions()
                assertTrue(manager.findFragmentById(R.id.settings) is SettingsFragment)
                assertEquals(0, manager.backStackEntryCount)
            }
        } finally { scenario.close() }
    }
}
