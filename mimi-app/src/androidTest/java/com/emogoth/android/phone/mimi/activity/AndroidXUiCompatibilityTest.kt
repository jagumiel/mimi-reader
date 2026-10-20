package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.view.View
import android.widget.EditText
import android.widget.Space
import androidx.appcompat.view.ContextThemeWrapper
import androidx.gridlayout.widget.GridLayout
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
import com.emogoth.android.phone.mimi.view.FilterView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidXUiCompatibilityTest {
    @Test fun filterEditorUsesPlatformSpacersAndPreservesControlsInAllThemes() {
        val scenario = ActivityScenario.launch<TabsActivity>(Intent(
            InstrumentationRegistry.getInstrumentation().targetContext, TabsActivity::class.java))
        try {
            scenario.onActivity { activity ->
                for (theme in intArrayOf(R.style.Theme_Mimi_Light_Toolbar_Default,
                    R.style.Theme_Mimi_Dark_Toolbar_Default, R.style.Theme_Mimi_Black_Toolbar_Default)) {
                    // Keep the editor detached: no board fetches or writes to the user's filters.
                    val editor = FilterView(ContextThemeWrapper(activity, theme))
                    val grid = editor.getChildAt(0) as GridLayout
                    val spacers = (0 until grid.childCount).map { grid.getChildAt(it) }
                        .filterIsInstance<Space>()
                    assertEquals(5, spacers.size)
                    assertEquals(10, grid.columnCount)
                    val density = activity.resources.displayMetrics.density
                    assertEquals(listOf(10, 10, 20, 10, 10).map { (it * density + 0.5f).toInt() },
                        spacers.map { it.layoutParams.height })
                    assertTrue(spacers.all { it.visibility == View.INVISIBLE })

                    editor.setFilterName("Local fixture")
                    editor.setFilterRegex("fixture.*")
                    var editClicks = 0
                    var cancelClicks = 0
                    editor.setClickListener(object : FilterView.ButtonClickListener {
                        override fun onSaveClicked(view: View) { fail("Test must not save a filter") }
                        override fun onEditClicked(view: View) { editClicks++ }
                        override fun onCancelClicked(view: View) { cancelClicks++ }
                    })
                    for (widthDp in intArrayOf(320, 480)) {
                        val width = (widthDp * density + 0.5f).toInt()
                        editor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                        editor.layout(0, 0, editor.measuredWidth, editor.measuredHeight)
                        assertTrue(editor.measuredHeight > 0)
                        for (id in intArrayOf(R.id.active_board, R.id.filter_name, R.id.filter_text,
                            R.id.save_button, R.id.edit_button, R.id.cancel_button)) {
                            val control = editor.findViewById<View>(id)
                            assertEquals(View.VISIBLE, control.visibility)
                            assertTrue(control.isEnabled)
                            assertTrue(control.width > 0 && control.height > 0)
                            assertTrue("Control $id overflows the grid", control.left >= 0 && control.right <= grid.width)
                        }
                        val name = editor.findViewById<View>(R.id.filter_name)
                        val text = editor.findViewById<View>(R.id.filter_text)
                        val save = editor.findViewById<View>(R.id.save_button)
                        val edit = editor.findViewById<View>(R.id.edit_button)
                        val cancel = editor.findViewById<View>(R.id.cancel_button)
                        assertTrue("Inputs must not overlap", name.bottom <= text.top)
                        assertTrue("Actions must be below the inputs", text.bottom <= save.top)
                        assertEquals(save.top, edit.top)
                        assertEquals(edit.top, cancel.top)
                        assertTrue("Actions must not overlap", save.right <= edit.left && edit.right <= cancel.left)
                        assertTrue("Actions must fit vertically", cancel.bottom <= grid.height)
                    }
                    assertEquals("Local fixture", editor.findViewById<EditText>(R.id.filter_name).text.toString())
                    assertEquals("fixture.*", editor.findViewById<EditText>(R.id.filter_text).text.toString())
                    editor.findViewById<View>(R.id.edit_button).performClick()
                    editor.findViewById<View>(R.id.cancel_button).performClick()
                    assertEquals(1, editClicks)
                    assertEquals(1, cancelClicks)
                }
            }
        } finally { scenario.close() }
    }

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
