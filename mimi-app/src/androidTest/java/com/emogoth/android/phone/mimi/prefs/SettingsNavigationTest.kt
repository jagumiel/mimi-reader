package com.emogoth.android.phone.mimi.prefs

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.databinding.ActivityThemeSettingsBinding
import com.emogoth.android.phone.mimi.databinding.SettingsActivityBinding
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

    private fun assertLiveSettingsChrome(activity: SettingsActivity) {
        val container = activity.findViewById<View>(R.id.settings)
        val binding = SettingsActivityBinding.bind(container.parent as View)
        assertSame(container, binding.settings)
        val toolbar = binding.root.findViewById<Toolbar>(R.id.mimi_toolbar)
        assertEquals(activity.getString(R.string.settings), toolbar.title)
        assertNotNull(toolbar.navigationIcon)
    }

    @Test fun retainedSettingsLayoutsBindInEveryThemeWidthAndDirectionWithoutChangingPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        try {
            scenario.onActivity { activity ->
                val original = PreferenceManager.getDefaultSharedPreferences(activity).all.toMap()
                assertLiveSettingsChrome(activity)
                val themes = listOf(R.style.Theme_Mimi_Light_Toolbar_Default,
                    R.style.Theme_Mimi_Dark_Toolbar_Default, R.style.Theme_Mimi_Black_Toolbar_Default)
                val density = activity.resources.displayMetrics.density
                for (theme in themes) for (widthDp in listOf(320, 480)) for (rtl in listOf(false, true)) {
                    val inflater = LayoutInflater.from(ContextThemeWrapper(activity, theme))
                    val settings = SettingsActivityBinding.inflate(inflater)
                    val colors = ActivityThemeSettingsBinding.inflate(inflater)
                    val width = (widthDp * density).toInt()
                    val height = (640 * density).toInt()
                    for (root in listOf(settings.root, colors.root)) {
                        root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                        root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, width, height)
                        val toolbar = root.findViewById<Toolbar>(R.id.mimi_toolbar)
                        assertEquals(width, root.width)
                        assertEquals(height, root.height)
                        assertEquals(0, toolbar.left)
                        assertEquals(width, toolbar.right)
                        assertTrue(toolbar.height > 0)
                        assertTrue(toolbar.bottom < height)
                    }
                    assertEquals(settings.root.findViewById<Toolbar>(R.id.mimi_toolbar).bottom, settings.settings.top)
                    assertEquals(height, settings.settings.bottom)
                    assertEquals(width, settings.settings.width)
                    assertEquals(4, colors.pickerGrid.columnCount)
                    assertEquals(48, (colors.currentColor.height / density + 0.5f).toInt())
                    assertTrue(colors.colorTypeSpinner.width > 0)
                    assertSame(colors.root.findViewById<View>(R.id.container), colors.container)
                    assertEquals(0, colors.container.childCount)
                    val swatches = listOf(colors.defaultReply, colors.defaultHighlight, colors.defaultQuote,
                        colors.defaultLink, colors.materialBlue, colors.materialRed, colors.materialGreen,
                        colors.materialYellow, colors.materialLightGrey, colors.materialDarkGrey)
                    assertEquals(10, swatches.distinct().size)
                    assertTrue(swatches.all { it.parent === colors.pickerGrid && it.width > 0 && it.height > 0 })
                    // Detached inflation never instantiates ThemeSettingsActivity or clicks its
                    // color controls: onStop there would otherwise persist a selected color.
                    assertNull(settings.root.parent)
                    assertNull(colors.root.parent)
                }
                assertEquals(original, PreferenceManager.getDefaultSharedPreferences(activity).all)
            }
        } finally { scenario.close() }
    }

    @Test fun everyLiveSettingsHeaderOpensRecreatesAndReturnsWithoutChangingExistingValues() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<SettingsActivity>(Intent(context, SettingsActivity::class.java))
        try {
            var original: Map<String, *> = emptyMap<String, Any>()
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                assertLiveSettingsChrome(activity)
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
                    assertLiveSettingsChrome(activity)
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
