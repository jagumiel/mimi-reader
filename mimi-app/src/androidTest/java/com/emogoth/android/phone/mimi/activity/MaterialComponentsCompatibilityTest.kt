package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.view.gallery.GalleryPager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual chrome/styles with synthetic pages only: no preference writes or public submissions. */
@RunWith(AndroidJUnit4::class)
class MaterialComponentsCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val themes = listOf(R.style.Theme_Mimi_Light_Toolbar_Default,
        R.style.Theme_Mimi_Dark_Toolbar_Default, R.style.Theme_Mimi_Black_Toolbar_Default)
    private fun host() = ActivityScenario.launch<TabsActivity>(Intent(instrumentation.targetContext, TabsActivity::class.java))
    private fun waitFor(scenario: ActivityScenario<TabsActivity>, message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail(message)
    }

    @Test fun productionChromeKeepsTabsPagerAndFabAcrossThemesWidthsAndDirections() {
        val scenario = host()
        try {
            for (theme in themes) for (rtl in listOf(false, true)) {
                lateinit var root: ViewGroup
                lateinit var tabs: TabLayout
                lateinit var pager: ViewPager2
                lateinit var appBar: AppBarLayout
                lateinit var fab: FloatingActionButton
                lateinit var mediator: TabLayoutMediator
                scenario.onActivity { activity ->
                    val themed = ContextThemeWrapper(activity, theme)
                    root = LayoutInflater.from(themed).inflate(R.layout.activity_tabs, null) as ViewGroup
                    root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                    tabs = root.findViewById(R.id.tab_layout)
                    pager = root.findViewById(R.id.tabs_pager)
                    appBar = root.findViewById(R.id.appbar)
                    fab = root.findViewById(R.id.fab_add_content)
                    pager.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                        override fun getItemCount() = 3
                        override fun onCreateViewHolder(parent: ViewGroup, type: Int) =
                            object : RecyclerView.ViewHolder(TextView(parent.context).apply {
                                layoutParams = ViewGroup.LayoutParams(-1, -1)
                            }) {}
                        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                            (holder.itemView as TextView).text = "Local page $position"
                        }
                    }
                    mediator = TabLayoutMediator(tabs, pager) { tab, position -> tab.text = "Local $position" }
                    mediator.attach()
                    val density = activity.resources.displayMetrics.density
                    activity.addContentView(root, FrameLayout.LayoutParams(
                        ((if (rtl) 480 else 320) * density).toInt(), (600 * density).toInt()))
                }
                try {
                    waitFor(scenario, "Production chrome must lay out") { fab.width > 0 && pager.height > 0 }
                    scenario.onActivity {
                        assertEquals(3, tabs.tabCount)
                        assertEquals("Local 2", tabs.getTabAt(2)!!.text.toString())
                        assertEquals(0, appBar.top)
                        assertTrue(tabs.bottom <= appBar.height)
                        assertTrue(pager.top >= appBar.bottom)
                        assertTrue(fab.left >= 0 && fab.right <= root.width)
                        assertTrue(fab.top >= 0 && fab.bottom <= root.height)
                        var clicks = 0
                        fab.setOnClickListener { clicks++ }
                        assertTrue(fab.performClick())
                        assertEquals(1, clicks)
                        tabs.getTabAt(2)!!.select()
                    }
                    waitFor(scenario, "Selecting a tab must update ViewPager2") { pager.currentItem == 2 }
                    scenario.onActivity { pager.setCurrentItem(0, false) }
                    waitFor(scenario, "Selecting a page must update TabLayout") { tabs.selectedTabPosition == 0 }
                    scenario.onActivity { appBar.setExpanded(false, false) }
                    waitFor(scenario, "Collapsed app bar must move the FAB out") { appBar.top < 0 && fab.translationY > 0f }
                    scenario.onActivity { appBar.setExpanded(true, false) }
                    waitFor(scenario, "Expanded app bar must restore the FAB") { appBar.top == 0 && fab.translationY == 0f }
                } finally {
                    scenario.onActivity {
                        mediator.detach()
                        pager.adapter = null
                        (root.parent as ViewGroup).removeView(root)
                    }
                }
            }
        } finally { scenario.close() }
    }

    @Test fun dialogChoicesAndSnackbarActionsDispatchOnceInAllThemes() {
        val scenario = host()
        try {
            for (theme in themes) {
                lateinit var root: FrameLayout
                lateinit var snackbar: Snackbar
                var actions = 0
                var confirmations = 0
                scenario.onActivity { activity ->
                    val themed = ContextThemeWrapper(activity, theme)
                    var choice = -1
                    val dialog = MaterialAlertDialogBuilder(themed)
                        .setTitle("Local choice fixture")
                        .setSingleChoiceItems(arrayOf("First", "Second"), 0) { _, which -> choice = which }
                        .setPositiveButton(android.R.string.ok) { _, _ -> confirmations++ }.create()
                    try {
                        dialog.show()
                        dialog.listView.performItemClick(dialog.listView.getChildAt(1), 1, 1)
                        assertEquals(1, choice)
                        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
                    } finally { dialog.dismiss() }
                    root = FrameLayout(themed)
                    activity.addContentView(root, FrameLayout.LayoutParams(-1, -1))
                    snackbar = Snackbar.make(root, "Local action fixture", Snackbar.LENGTH_INDEFINITE)
                        .setAction("Action") { actions++ }
                    snackbar.show()
                }
                try {
                    // AlertDialog dispatches its positive callback through the main queue.
                    waitFor(scenario, "Dialog confirmation must dispatch") { confirmations > 0 }
                    waitFor(scenario, "Snackbar must become visible") { snackbar.isShown }
                    scenario.onActivity {
                        assertEquals(1, confirmations)
                        val action = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action)
                        assertEquals(View.VISIBLE, action.visibility)
                        assertTrue(action.performClick())
                        assertEquals(1, actions)
                    }
                    waitFor(scenario, "Action must dismiss its snackbar") { !snackbar.isShownOrQueued }
                } finally {
                    scenario.onActivity { snackbar.dismiss(); (root.parent as ViewGroup).removeView(root) }
                }
            }
        } finally { scenario.close() }
    }

    @Test fun galleryConstraintContainersFillBothWidthsWithoutClipping() {
        val scenario = host()
        try {
            scenario.onActivity { activity ->
                for (theme in themes) for (rtl in listOf(false, true)) {
                    val themed = ContextThemeWrapper(activity, theme)
                    val density = activity.resources.displayMetrics.density
                    val width = ((if (rtl) 480 else 320) * density).toInt()
                    val height = (600 * density).toInt()
                    for (layout in listOf(R.layout.content_gallery2_grid, R.layout.content_gallery2_pager)) {
                        val root = LayoutInflater.from(themed).inflate(layout, null) as ConstraintLayout
                        root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
                        val child = root.getChildAt(0)
                        try {
                            root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                            root.layout(0, 0, width, height)
                            assertEquals(0, child.left)
                            assertEquals(0, child.top)
                            assertEquals(width, child.right)
                            assertEquals(height, child.bottom)
                        } finally { (child as? GalleryPager)?.release() }
                    }
                }
            }
        } finally { scenario.close() }
    }
}
