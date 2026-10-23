package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.dialog.ThemeColorPickerDialog
import com.emogoth.android.phone.mimi.util.MimiUtil
import com.emogoth.android.phone.mimi.view.ColorImageView
import com.rarepebble.colorpicker.ColorPickerView
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Real editor lifecycle with a debug-only isolated storage backend, even if a test crashes. */
@RunWith(AndroidJUnit4::class)
class ThemeSettingsActivityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val preferences get() = context.getSharedPreferences(ThemeSettingsTestActivity.PREFERENCES, Context.MODE_PRIVATE)
    private val userPreferences get() = PreferenceManager.getDefaultSharedPreferences(context)
    private val keys get() = listOf(R.string.quote_color_pref, R.string.reply_color_pref,
        R.string.highlight_color_pref, R.string.link_color_pref).map(context::getString)
    private lateinit var stored: Map<String, Any?>

    private fun colors(): List<Int> = keys.map { preferences.getInt(it, 0) }

    @Before fun seedIsolatedColorPreferences() {
        stored = keys.associateWith { userPreferences.all[it] }
        val editor = preferences.edit().clear()
        listOf(0xff102030.toInt(), 0xff203040.toInt(), 0xff304050.toInt(), 0xff405060.toInt())
            .forEachIndexed { index, color -> editor.putInt(keys[index], color) }
        assertTrue(editor.commit())
    }

    @After fun clearOnlyIsolatedStorageAndVerifyUserColorsWereNeverChanged() {
        assertTrue(preferences.edit().clear().commit())
        for ((key, value) in stored) {
            assertEquals(value != null, userPreferences.contains(key))
            assertEquals(value, userPreferences.all[key])
        }
    }

    private fun host(): ActivityScenario<ThemeSettingsActivity> =
        ActivityScenario.launch(Intent(context, ThemeSettingsTestActivity::class.java))

    private fun waitFor(scenario: ActivityScenario<ThemeSettingsActivity>, message: String,
                        condition: (ThemeSettingsActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            scenario.onActivity { done = condition(it) }
            if (done) return
            SystemClock.sleep(25)
        }
        fail(message)
    }

    private fun select(scenario: ActivityScenario<ThemeSettingsActivity>, slot: Int) {
        scenario.onActivity { it.findViewById<Spinner>(R.id.color_type_spinner).setSelection(slot) }
        waitFor(scenario, "Color selection $slot must update its swatch") {
            it.findViewById<Spinner>(R.id.color_type_spinner).selectedItemPosition == slot &&
                it.findViewById<ColorImageView>(R.id.current_color).backgroundColor == colors()[slot]
        }
    }

    private fun dialog(activity: ThemeSettingsActivity): ThemeColorPickerDialog =
        activity.supportFragmentManager.findFragmentByTag(ThemeColorPickerDialog.TAG) as ThemeColorPickerDialog

    private fun picker(view: View): ColorPickerView? {
        if (view is ColorPickerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) picker(view.getChildAt(index))?.let { return it }
        return null
    }

    private fun openPicker(scenario: ActivityScenario<ThemeSettingsActivity>, slot: Int) {
        select(scenario, slot)
        scenario.onActivity {
            it.findViewById<View>(R.id.current_color).performClick()
            it.findViewById<View>(R.id.current_color).performClick()
            it.supportFragmentManager.executePendingTransactions()
            assertEquals(slot, dialog(it).requireArguments().getInt(ThemeColorPickerDialog.SLOT))
            assertTrue(dialog(it).requireDialog().isShowing)
        }
    }

    private fun assertPreview(activity: ThemeSettingsActivity) {
        val preview = activity.findViewById<ViewGroup>(R.id.container)
        assertEquals(1, preview.childCount)
        val comment = preview.findViewById<TextView>(R.id.comment)
        val text = comment.text as Spanned
        val url = activity.getString(R.string.project_repository_url)
        assertTrue(text.contains(url))
        assertFalse(text.contains("mimireader.com"))
        assertNull(comment.movementMethod)
        assertFalse(comment.linksClickable)
        for ((needle, color) in listOf(
            ">implying" to colors()[0], ">>87654321" to colors()[1],
            ">>88765432" to colors()[2], url to colors()[3])) {
            val start = text.toString().indexOf(needle)
            assertTrue("Missing preview token $needle", start >= 0)
            val spans = text.getSpans(start, start + needle.length, CharacterStyle::class.java)
            assertTrue("Missing color span for $needle", spans.isNotEmpty())
            val paint = TextPaint()
            spans.forEach { it.updateDrawState(paint) }
            assertEquals("Preview color for $needle", color, paint.color)
        }
    }

    @Test fun previewRendersAndReadOnlyLifecycleKeepsColorsAndSelectedSlot() {
        val original = preferences.all.toMap()
        val scenario = host()
        try {
            scenario.onActivity { assertPreview(it) }
            scenario.onActivity {
                val swatch = it.findViewById<ColorImageView>(R.id.current_color)
                val fill = swatch.drawable
                val border = swatch.background
                val bitmap = Bitmap.createBitmap(swatch.width, swatch.height, Bitmap.Config.ARGB_8888)
                try {
                    repeat(5) { swatch.draw(Canvas(bitmap)) }
                    assertSame(fill, swatch.drawable)
                    assertSame(border, swatch.background)
                    assertEquals(colors()[0], bitmap.getPixel(bitmap.width / 2, bitmap.height / 2))
                } finally { bitmap.recycle() }
            }
            select(scenario, 3)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity {
                assertEquals(3, it.findViewById<Spinner>(R.id.color_type_spinner).selectedItemPosition)
                assertPreview(it)
            }
        } finally { scenario.close() }
        assertEquals("Opening, backgrounding and closing must not write preferences", original, preferences.all)
    }

    @Test fun productionEditorReadOnlyLifecycleDoesNotAddOrOverwriteStoredColors() {
        val original = userPreferences.all.toMap()
        val scenario = ActivityScenario.launch<ThemeSettingsActivity>(Intent(context, ThemeSettingsActivity::class.java))
        try {
            scenario.onActivity {
                assertEquals(MimiUtil.getInstance().quoteColor, it.findViewById<ColorImageView>(R.id.current_color).backgroundColor)
                assertTrue(it.findViewById<TextView>(R.id.comment).text is Spanned)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
        } finally { scenario.close() }
        assertEquals(original, userPreferences.all)
    }

    @Test fun paletteChangesOnlyTheSelectedSlotAndPreviewSurvivesRecreation() {
        val original = preferences.all.toMap().toMutableMap()
        val scenario = host()
        try {
            for (slot in 0..3) {
                select(scenario, slot)
                scenario.onActivity {
                    val swatch = it.findViewById<ColorImageView>(R.id.material_blue)
                    original[keys[slot]] = swatch.backgroundColor
                    swatch.performClick()
                    assertPreview(it)
                    assertEquals(original, preferences.all)
                }
            }
            scenario.recreate()
            scenario.onActivity { assertPreview(it) }
            assertEquals(original, preferences.all)
        } finally { scenario.close() }
    }

    @Test fun pickerDraftAndTargetSurviveRecreationAndOnlyOkCommits() {
        val original = preferences.all.toMap().toMutableMap()
        val draft = 0xff123456.toInt()
        val scenario = host()
        try {
            openPicker(scenario, 3)
            scenario.onActivity { picker(dialog(it).requireDialog().window!!.decorView)!!.setColor(draft) }
            assertEquals(original, preferences.all)
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertEquals(1, it.supportFragmentManager.fragments.filterIsInstance<ThemeColorPickerDialog>().size)
                assertEquals(3, dialog(it).requireArguments().getInt(ThemeColorPickerDialog.SLOT))
                assertEquals(draft, picker(dialog(it).requireDialog().window!!.decorView)!!.color)
                assertEquals(original, preferences.all)
                (dialog(it).requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            waitFor(scenario, "Explicit OK must commit the target color") { colors()[3] == draft }
            original[keys[3]] = draft
            assertEquals(original, preferences.all)
            scenario.onActivity { assertPreview(it) }
        } finally { scenario.close() }
    }

    @Test fun cancellationDuplicateClicksAndInvalidResultsCannotChangeColors() {
        val original = preferences.all.toMap()
        val scenario = host()
        try {
            openPicker(scenario, 2)
            scenario.onActivity {
                it.findViewById<View>(R.id.current_color).performClick()
                it.supportFragmentManager.executePendingTransactions()
                assertEquals(1, it.supportFragmentManager.fragments.filterIsInstance<ThemeColorPickerDialog>().size)
                picker(dialog(it).requireDialog().window!!.decorView)!!.setColor(0xffabcdef.toInt())
                (dialog(it).requireDialog() as AlertDialog).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            scenario.onActivity { it.supportFragmentManager.executePendingTransactions() }
            openPicker(scenario, 2)
            scenario.recreate()
            scenario.onActivity {
                dialog(it).requireDialog().cancel()
                for (slot in listOf(-1, 4)) {
                    it.supportFragmentManager.setFragmentResult(ThemeColorPickerDialog.RESULT, Bundle().apply {
                        putInt(ThemeColorPickerDialog.SLOT, slot); putInt(ThemeColorPickerDialog.COLOR, 0)
                    })
                }
                it.supportFragmentManager.setFragmentResult(ThemeColorPickerDialog.RESULT, Bundle().apply {
                    putInt(ThemeColorPickerDialog.SLOT, 0)
                })
            }
        } finally { scenario.close() }
        assertEquals(original, preferences.all)
    }
}
