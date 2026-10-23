package com.emogoth.android.phone.mimi.prefs

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Navigate real headers without clicking controls that modify data or launch other apps. */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    private val screens = linkedMapOf(
        GeneralPrefsFragment::class.java to listOf(R.string.nav_drawer_bookmark_count_pref),
        GalleryPrefsFragment::class.java to listOf(R.string.gallery_autoplay_pref, R.string.image_file_location_pref),
        AppearancePrefsFragment::class.java to listOf(R.string.theme_pref, R.string.start_activity_pref),
        HistoryPrefsFragment::class.java to listOf(R.string.save_history_pref, R.string.clear_history_pref),
        AdvancedPrefsFragment::class.java to listOf(R.string.clear_cache_pref, R.string.chanpass_login_pref),
        MoreInfoPrefsFragment::class.java to listOf(R.string.website_pref, R.string.privacy_pref, R.string.licenses_pref)
    )

    private fun headers(root: SettingsFragment): List<Preference> =
        (0 until root.preferenceScreen.preferenceCount).map { root.preferenceScreen.getPreference(it) }

    private fun keys(group: PreferenceGroup): List<String> = buildList {
        for (index in 0 until group.preferenceCount) {
            val preference = group.getPreference(index)
            preference.key?.let { add(it) }
            if (preference is PreferenceGroup) addAll(keys(preference))
        }
    }

    @Test fun everyLiveSettingsHeaderOpensRecreatesAndReturnsWithoutChangingExistingValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        try {
            var original: Map<String, *> = emptyMap<String, Any>()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                original = PreferenceManager.getDefaultSharedPreferences(activity).all.toMap()
                val root = activity.supportFragmentManager.findFragmentById(R.id.settings) as SettingsFragment
                assertEquals(screens.keys.map { it.name }, headers(root).map { it.fragment })
                assertTrue(headers(root).all { !it.title.isNullOrBlank() })
                assertEquals(0, activity.supportFragmentManager.backStackEntryCount)
            }
            for ((type, requiredKeys) in screens) {
                var originalKeys: List<String> = emptyList()
                scenario.onActivity { activity ->
                    val manager = activity.supportFragmentManager
                    val root = manager.findFragmentById(R.id.settings) as SettingsFragment
                    headers(root).single { it.fragment == type.name }.performClick()
                    manager.executePendingTransactions()
                    val screen = manager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
                    assertEquals(type, screen.javaClass)
                    assertEquals(1, manager.backStackEntryCount)
                    for (key in requiredKeys) assertNotNull(screen.findPreference<Preference>(activity.getString(key)))
                    originalKeys = keys(screen.preferenceScreen)
                    assertTrue(originalKeys.isNotEmpty())
                    assertNull(screen.findPreference<Preference>("about_pref"))
                    assertNull(screen.findPreference<Preference>("posting_pref"))
                    assertNull(screen.findPreference<Preference>("board_pref"))
                }
                scenario.recreate()
                scenario.onActivity { activity ->
                    val manager = activity.supportFragmentManager
                    manager.executePendingTransactions()
                    val restored = manager.findFragmentById(R.id.settings) as PreferenceFragmentCompat
                    assertEquals(type, restored.javaClass)
                    assertEquals(originalKeys, keys(restored.preferenceScreen))
                    assertEquals(1, manager.backStackEntryCount)
                    activity.onBackPressedDispatcher.onBackPressed()
                    manager.executePendingTransactions()
                    val root = manager.findFragmentById(R.id.settings) as SettingsFragment
                    assertEquals(screens.keys.map { it.name }, headers(root).map { it.fragment })
                    assertEquals(0, manager.backStackEntryCount)
                    // A cold installation may initialize missing XML defaults. Never overwrite
                    // any value that existed before navigation, including unrelated/legacy keys.
                    val current = PreferenceManager.getDefaultSharedPreferences(activity).all
                    for ((key, value) in original) assertEquals("Existing preference $key", value, current[key])
                }
            }
        } finally { scenario.close() }
    }
}
