package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Detached production resources only: no preference changes, adapters or remote media. */
@RunWith(AndroidJUnit4::class)
class ThemeResourceCompatibilityTest {
    @Test fun retainedCardStylesAndSelectorsRenderAcrossThemesAndRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scenario = ActivityScenario.launch<TabsActivity>(Intent(context, TabsActivity::class.java))
        try {
            repeat(2) { pass ->
                if (pass == 1) scenario.recreate()
                scenario.onActivity { activity ->
                    val themes = listOf(
                        intArrayOf(R.style.Theme_Mimi_Light_Toolbar_Default,
                            R.drawable.basic_card_background_light, R.color.row_item_background_light,
                            R.color.row_item_background_light_pressed),
                        intArrayOf(R.style.Theme_Mimi_Dark_Toolbar_Default,
                            R.drawable.basic_card_background_dark, R.color.row_item_background_dark,
                            R.color.row_item_background_dark_pressed),
                        intArrayOf(R.style.Theme_Mimi_Black_Toolbar_Default,
                            R.drawable.basic_card_background_black, R.color.row_item_background_black,
                            R.color.row_item_background_black_pressed))
                    for ((theme, background, normalColor, pressedColor) in themes) {
                        val themed = ContextThemeWrapper(activity, theme)
                        val style = TypedValue()
                        assertTrue(themed.theme.resolveAttribute(R.attr.cardBackground, style, true))
                        val attrs = themed.obtainStyledAttributes(style.resourceId,
                            intArrayOf(android.R.attr.background))
                        try { assertEquals(background, attrs.getResourceId(0, 0)) }
                        finally { attrs.recycle() }

                        // Exercise the live grid selector as well as the retained plain-card style.
                        assertTrue(themed.theme.resolveAttribute(R.attr.gridCardBackgroundSelector, style, true))
                        val grid = themed.obtainStyledAttributes(style.resourceId,
                            intArrayOf(android.R.attr.background))
                        val gridBackground = try { grid.getResourceId(0, 0) } finally { grid.recycle() }
                        assertTrue(gridBackground != 0)
                        for (drawableId in intArrayOf(background, gridBackground)) {
                            val drawable = requireNotNull(ContextCompat.getDrawable(themed, drawableId))
                            val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
                            try {
                                drawable.setBounds(0, 0, 64, 64)
                                for (state in arrayOf(intArrayOf(), intArrayOf(android.R.attr.state_pressed),
                                    intArrayOf(android.R.attr.state_focused))) {
                                    bitmap.eraseColor(android.graphics.Color.TRANSPARENT)
                                    drawable.state = state
                                    drawable.draw(Canvas(bitmap))
                                    assertTrue("Retained background must draw", bitmap.getPixel(32, 32) ushr 24 > 0)
                                }
                            } finally { bitmap.recycle() }
                        }

                        for (widthDp in intArrayOf(320, 480)) for (rtl in listOf(false, true)) {
                            val parent = FrameLayout(themed)
                            parent.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                            val width = (widthDp * themed.resources.displayMetrics.density + 0.5f).toInt()
                            for (layout in intArrayOf(R.layout.catalog_post_list_item, R.layout.catalog_post_grid_item,
                                R.layout.thread_first_post_item, R.layout.thread_post_item)) {
                                val view = LayoutInflater.from(themed).inflate(layout, parent, false)
                                parent.addView(view)
                                try {
                                    parent.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                                    parent.layout(0, 0, width, parent.measuredHeight)
                                    assertTrue("Production post layout must occupy space", view.width > 0 && view.height > 0)
                                    assertEquals(parent.layoutDirection, view.layoutDirection)
                                    if (view is CardView) {
                                        assertTrue(view.isClickable && view.isFocusable)
                                        assertNotNull(view.foreground)
                                        assertTrue(view.cardBackgroundColor.isStateful)
                                        assertEquals(ContextCompat.getColor(themed, normalColor),
                                            view.cardBackgroundColor.defaultColor)
                                        assertEquals(ContextCompat.getColor(themed, pressedColor),
                                            view.cardBackgroundColor.getColorForState(
                                                intArrayOf(android.R.attr.state_pressed), 0))
                                        view.isPressed = true
                                        assertTrue(view.drawableState.contains(android.R.attr.state_pressed))
                                        view.isPressed = false
                                    }
                                } finally { parent.removeView(view) }
                            }
                        }
                    }
                }
            }
        } finally { scenario.close() }
    }
}
