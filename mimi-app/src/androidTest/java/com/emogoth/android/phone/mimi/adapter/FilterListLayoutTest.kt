package com.emogoth.android.phone.mimi.adapter

import android.content.Intent
import android.content.res.Configuration
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.TabsActivity
import com.emogoth.android.phone.mimi.db.models.Filter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/** Inflate/bind the real adapter with in-memory filters; never edit the user's database. */
@RunWith(AndroidJUnit4::class)
class FilterListLayoutTest {
    private val themes = listOf(R.style.Theme_Mimi_Light_Toolbar_Default,
        R.style.Theme_Mimi_Dark_Toolbar_Default, R.style.Theme_Mimi_Black_Toolbar_Default)
    private fun host() = ActivityScenario.launch<TabsActivity>(Intent(
        InstrumentationRegistry.getInstrumentation().targetContext, TabsActivity::class.java))
    private fun filter(name: String, regex: String) = Filter().apply {
        this.name = name; this.filter = regex; boardName = "g"
    }
    private fun layout(holder: FilterListAdapter.FilterViewHolder, width: Int, rtl: Boolean) {
        holder.root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val height = (48 * holder.root.resources.displayMetrics.density + 0.5f).toInt()
        holder.root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
        holder.root.layout(0, 0, width, height)
    }
    private fun checkLayout(rtl: Boolean) {
        val scenario = host()
        try {
            scenario.onActivity { activity ->
                for (theme in themes) for (fontScale in listOf(1f, 1.3f)) {
                    val config = Configuration(activity.resources.configuration).apply { this.fontScale = fontScale }
                    val themed = ContextThemeWrapper(activity.createConfigurationContext(config), theme)
                    val parent = RecyclerView(themed).apply { layoutManager = LinearLayoutManager(themed) }
                    val model = filter("A long local filter name ".repeat(5), "(?i)fixture.*".repeat(8))
                    val adapter = FilterListAdapter(mutableListOf(model))
                    val holder = adapter.onCreateViewHolder(parent, 0)
                    adapter.onBindViewHolder(holder, 0)
                    val metrics = themed.resources.displayMetrics
                    val colors = when (theme) {
                        R.style.Theme_Mimi_Light_Toolbar_Default -> R.color.text_color_light to R.color.subtext_color_light
                        R.style.Theme_Mimi_Dark_Toolbar_Default -> R.color.text_color_dark to R.color.subtext_color_dark
                        else -> R.color.text_color_black to R.color.subtext_color_black
                    }
                    // The extra pixel exercises rounding instead of assuming width divides by two.
                    for (width in listOf((320 * metrics.density).toInt(), (480 * metrics.density).toInt() + 1)) {
                        layout(holder, width, rtl)
                        val name = holder.name
                        val regex = holder.regex
                        assertEquals(width, holder.root.width)
                        assertEquals((48 * metrics.density + 0.5f).toInt(), holder.root.height)
                        assertEquals(holder.root.height, holder.root.layoutParams.height)
                        assertTrue(abs(name.width - regex.width) <= 1)
                        assertEquals(width, name.width + regex.width)
                        for (text in listOf(name, regex)) {
                            assertTrue(text.left >= 0 && text.right <= width)
                            assertEquals(0, text.top)
                            assertEquals(holder.root.height, text.bottom)
                            assertEquals(View.VISIBLE, text.visibility)
                            assertTrue(text.isEnabled)
                            assertEquals(if (rtl) Gravity.RIGHT else Gravity.LEFT,
                                Gravity.getAbsoluteGravity(text.gravity, holder.root.layoutDirection) and Gravity.HORIZONTAL_GRAVITY_MASK)
                        }
                        if (rtl) {
                            assertEquals(0, regex.left)
                            assertEquals(regex.right, name.left)
                            assertEquals(width, name.right)
                        } else {
                            assertEquals(0, name.left)
                            assertEquals(name.right, regex.left)
                            assertEquals(width, regex.right)
                        }
                        val padding = (16 * metrics.density + 0.5f).toInt()
                        assertEquals(padding, name.paddingStart)
                        assertEquals(0, name.paddingEnd)
                        assertEquals(0, regex.paddingStart)
                        assertEquals(padding, regex.paddingEnd)
                        // XML dimensions can round to the nearest physical pixel.
                        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 16f, metrics), name.textSize, 0.6f)
                        assertEquals(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14f, metrics), regex.textSize, 0.6f)
                        assertTrue(name.typeface?.isBold == true)
                        assertEquals(themed.getColor(colors.first), name.currentTextColor)
                        assertEquals(themed.getColor(colors.second), regex.currentTextColor)
                        assertEquals(model.name, name.text.toString())
                        assertEquals(model.filter, regex.text.toString())
                        assertTrue(holder.root.hasOnClickListeners())
                    }
                }
            }
        } finally { scenario.close() }
    }

    @Test fun rowsKeepTheirEqualColumnsAndTypographyInLtr() = checkLayout(false)
    @Test fun rowsMirrorTheirColumnsAndOuterPaddingInRtl() = checkLayout(true)

    @Test fun adapterReplacementAndRemovalKeepTextAndClickTargets() {
        val scenario = host()
        try {
            scenario.onActivity { activity ->
                for (theme in themes) for (rtl in listOf(false, true)) {
                    val parent = RecyclerView(ContextThemeWrapper(activity, theme)).apply {
                        layoutManager = LinearLayoutManager(context)
                    }
                    val first = filter("First fixture", "one.*")
                    val replacement = filter("Second fixture", "two.*")
                    val adapter = FilterListAdapter(mutableListOf(first))
                    val holder = adapter.onCreateViewHolder(parent, 0)
                    adapter.onBindViewHolder(holder, 0)
                    assertEquals(first.name, holder.name.text.toString())
                    var changes = 0
                    var removals = 0
                    val observer = object : RecyclerView.AdapterDataObserver() {
                        override fun onChanged() { changes++ }
                        override fun onItemRangeRemoved(start: Int, count: Int) {
                            assertEquals(0, start); assertEquals(1, count); removals++
                        }
                    }
                    adapter.registerAdapterDataObserver(observer)
                    try {
                        adapter.setFilters(mutableListOf(replacement))
                        adapter.onBindViewHolder(holder, 0)
                        layout(holder, (320 * parent.resources.displayMetrics.density).toInt(), rtl)
                        assertEquals(1, changes)
                        assertEquals(replacement.name, holder.name.text.toString())
                        assertEquals(replacement.filter, holder.regex.text.toString())
                        assertTrue(holder.root.hasOnClickListeners())
                        adapter.removeFilter(0)
                        assertEquals(0, adapter.itemCount)
                        assertEquals(1, removals)
                        adapter.setFilters(mutableListOf(first))
                        adapter.onBindViewHolder(holder, 0)
                        assertEquals(first.name, holder.name.text.toString())
                        assertEquals(first.filter, holder.regex.text.toString())
                        assertEquals(2, changes)
                    } finally { adapter.unregisterAdapterDataObserver(observer) }
                }
            }
        } finally { scenario.close() }
    }
}
