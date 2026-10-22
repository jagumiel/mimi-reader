package com.emogoth.android.phone.mimi.widget

import android.content.Intent
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.TabsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Production layout/controller, synthetic posts and real touch dispatch; no thread fetch/post. */
@RunWith(AndroidJUnit4::class)
class ThreadFastScrollTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private class Rows(var count: Int) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = count
        override fun onCreateViewHolder(parent: ViewGroup, type: Int) = object : RecyclerView.ViewHolder(TextView(parent.context)) {}
        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val density = holder.itemView.resources.displayMetrics.density
            holder.itemView.layoutParams = RecyclerView.LayoutParams(-1, ((if (position % 3 == 0) 180 else 64) * density).toInt())
            (holder.itemView as TextView).text = "Fixture post ${position + 1}"
        }
    }
    private class Fixture(val root: View, val list: RecyclerView, val scroller: ThreadFastScroll) {
        val layout get() = list.layoutManager as LinearLayoutManager
        val refresh: SwipeRefreshLayout = root.findViewById(R.id.swipe_refresh_layout)
        val refreshes = AtomicInteger()
        var downTime = 0L
        init { refresh.setOnRefreshListener { refreshes.incrementAndGet(); refresh.isRefreshing = false } }
    }
    private fun fixture(activity: TabsActivity, theme: Int = R.style.Theme_Mimi_Light_Toolbar_Default,
                        rtl: Boolean = false, widthDp: Int = 320): Fixture {
        val themed = ContextThemeWrapper(activity, theme)
        val root = LayoutInflater.from(themed).inflate(R.layout.fragment_thread_detail, null)
        root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        root.findViewById<View>(R.id.loading_layout).visibility = View.GONE
        val list = root.findViewById<RecyclerView>(R.id.thread_list)
        list.layoutManager = LinearLayoutManager(themed)
        list.adapter = Rows(80)
        val scroller = ThreadFastScroll.attach(list)
        val density = themed.resources.displayMetrics.density
        activity.addContentView(root, FrameLayout.LayoutParams((widthDp * density).toInt(), (400 * density).toInt()))
        return Fixture(root, list, scroller)
    }
    private fun host() = ActivityScenario.launch<TabsActivity>(Intent(context, TabsActivity::class.java))
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
    private fun touch(f: Fixture, action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) f.downTime = now
        val event = MotionEvent.obtain(f.downTime, now, action, x, y, 0)
        try { f.refresh.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    @Test fun variableHeightPostsReachBothEndsAndMiddleByIndex() {
        val scenario = host()
        lateinit var f: Fixture
        try {
            scenario.onActivity { f = fixture(it) }
            waitFor(scenario, "Long list must expose a scroll range") { f.scroller.scrollRange > f.list.height }
            scenario.onActivity { f.scroller.scrollTo((f.scroller.scrollRange - f.list.height) / 2) }
            waitFor(scenario, "Halfway must target post 41, independent of row heights") { f.layout.findFirstVisibleItemPosition() == 40 }
            scenario.onActivity { assertEquals("41 / 80", f.scroller.popupText); f.scroller.scrollTo(Int.MAX_VALUE) }
            waitFor(scenario, "End must reach the final post") { !f.list.canScrollVertically(1) && f.scroller.popupText == "80 / 80" }
            scenario.onActivity { f.scroller.scrollTo(0) }
            waitFor(scenario, "Start must reach the first post") { !f.list.canScrollVertically(-1) && f.scroller.popupText == "1 / 80" }
        } finally { scenario.close() }
    }

    @Test fun realDraggingShowsTheCounterAcrossThemesWidthsAndDirections() {
        val scenario = host()
        try {
            for (theme in listOf(R.style.Theme_Mimi_Light_Toolbar_Default, R.style.Theme_Mimi_Dark_Toolbar_Default,
                R.style.Theme_Mimi_Black_Toolbar_Default)) for (rtl in listOf(false, true)) {
                lateinit var f: Fixture
                scenario.onActivity { f = fixture(it, theme, rtl, if (rtl) 480 else 320) }
                waitFor(scenario, "Scrollbar must lay out before touch") { f.list.height > 0 && f.scroller.scrollRange > f.list.height }
                scenario.onActivity {
                    val x = if (rtl) 5f else f.list.width - 5f
                    touch(f, MotionEvent.ACTION_DOWN, x, 60f)
                    touch(f, MotionEvent.ACTION_MOVE, x, f.list.height * 0.6f)
                    touch(f, MotionEvent.ACTION_MOVE, x, f.list.height * 0.85f)
                }
                waitFor(scenario, "Dragging must move posts and show position / total") {
                    f.layout.findFirstVisibleItemPosition() > 0 && f.scroller.popup.alpha > 0.9f &&
                            f.scroller.popup.text.endsWith("/ 80")
                }
                scenario.onActivity {
                    assertFalse(f.refresh.isRefreshing)
                    assertEquals(0, f.refreshes.get())
                    assertTrue(f.scroller.popup.left >= 0 && f.scroller.popup.right <= f.list.width)
                    assertTrue(f.scroller.popup.top >= 0 && f.scroller.popup.bottom <= f.list.height)
                    touch(f, MotionEvent.ACTION_UP, if (rtl) 5f else f.list.width - 5f, f.list.height * 0.85f)
                    (f.root.parent as ViewGroup).removeView(f.root)
                    assertEquals(0f, f.scroller.popup.alpha, 0.01f)
                }
            }
        } finally { scenario.close() }
    }

    @Test fun contentPullToRefreshStillWorksAfterScrollbarCancellation() {
        val scenario = host()
        lateinit var f: Fixture
        try {
            scenario.onActivity { f = fixture(it) }
            waitFor(scenario, "List must lay out") { f.scroller.scrollRange > f.list.height }
            scenario.onActivity {
                touch(f, MotionEvent.ACTION_DOWN, f.list.width - 5f, 60f)
                touch(f, MotionEvent.ACTION_MOVE, f.list.width - 5f, f.list.height * 0.7f)
                touch(f, MotionEvent.ACTION_CANCEL, f.list.width - 5f, f.list.height * 0.7f)
                f.scroller.scrollTo(0)
            }
            waitFor(scenario, "Return to top before refresh") { !f.list.canScrollVertically(-1) }
            scenario.onActivity {
                val x = f.list.width / 2f
                val density = f.list.resources.displayMetrics.density
                touch(f, MotionEvent.ACTION_DOWN, x, 20f)
                for (dp in listOf(40, 120, 240, 320)) touch(f, MotionEvent.ACTION_MOVE, x, dp * density)
                touch(f, MotionEvent.ACTION_UP, x, 320 * density)
            }
            waitFor(scenario, "Content gesture must still trigger refresh after cancellation") { f.refreshes.get() > 0 }
        } finally { scenario.close() }
    }

    @Test fun emptyShortAndReplacedAdaptersAndReattachmentDoNotDuplicateControllers() {
        val scenario = host()
        lateinit var f: Fixture
        try {
            scenario.onActivity { f = fixture(it) }
            waitFor(scenario, "Initial data must lay out") { f.scroller.scrollRange > f.list.height }
            scenario.onActivity {
                val decorations = f.list.itemDecorationCount
                assertSame(f.scroller, ThreadFastScroll.attach(f.list))
                assertEquals(decorations, f.list.itemDecorationCount)
                val parent = f.root.parent as ViewGroup
                parent.removeView(f.root); parent.addView(f.root)
                assertSame(f.scroller, ThreadFastScroll.attach(f.list))
                assertEquals(decorations, f.list.itemDecorationCount)
                touch(f, MotionEvent.ACTION_DOWN, f.list.width - 5f, 60f)
                touch(f, MotionEvent.ACTION_MOVE, f.list.width - 5f, f.list.height * 0.7f)
            }
            waitFor(scenario, "Start dragging before data is cleared") { f.scroller.popup.alpha > 0.9f }
            scenario.onActivity {
                val rows = f.list.adapter as Rows
                rows.count = 0
                rows.notifyDataSetChanged()
            }
            waitFor(scenario, "Empty adapter must cancel dragging and remove popup") {
                f.scroller.scrollRange == f.list.height && f.scroller.popupText == null && f.scroller.popup.alpha == 0f
            }
            scenario.onActivity { f.list.adapter = Rows(1) }
            waitFor(scenario, "One post must not enable fast scrolling") { f.scroller.scrollRange == f.list.height }
            scenario.onActivity { f.list.adapter = Rows(2) }
            waitFor(scenario, "Two posts that fit on screen must not enable fast scrolling") { f.scroller.scrollRange == f.list.height }
            scenario.onActivity { f.list.adapter = Rows(120) }
            waitFor(scenario, "Replacement adapter must be reflected in popup and range") {
                f.scroller.scrollRange > f.list.height && f.scroller.popupText?.endsWith("/ 120") == true
            }
            scenario.onActivity {
                touch(f, MotionEvent.ACTION_DOWN, f.list.width - 5f, 60f)
                touch(f, MotionEvent.ACTION_MOVE, f.list.width - 5f, f.list.height * 0.7f)
            }
            waitFor(scenario, "Dragging must recover after data replacement") {
                f.layout.findFirstVisibleItemPosition() > 0 && f.scroller.popup.alpha > 0.9f
            }
            scenario.onActivity { f.list.adapter = Rows(0) }
            waitFor(scenario, "Replacing the adapter during a drag must cancel it too") {
                f.scroller.scrollRange == f.list.height && f.scroller.popup.alpha == 0f
            }
            scenario.onActivity { touch(f, MotionEvent.ACTION_UP, f.list.width - 5f, f.list.height * 0.7f) }
        } finally { scenario.close() }
    }
}
