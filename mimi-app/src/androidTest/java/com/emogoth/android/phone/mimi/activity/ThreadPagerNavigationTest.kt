package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.adapter.ThreadPagerAdapter
import com.emogoth.android.phone.mimi.model.ThreadInfo
import com.emogoth.android.phone.mimi.util.Extras
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadPagerNavigationTest {
    @Test
    fun threadCollectionUsesViewPager2AndActivatesSelectedThread() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val threads = arrayListOf(
                ThreadInfo(1001L, "g", "Technology", false),
                ThreadInfo(1002L, "g", "Technology", false)
        )
        val intent = Intent(instrumentation.targetContext, PostItemDetailActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putParcelableArrayListExtra(Extras.EXTRAS_THREAD_LIST, threads)
                .putExtra(Extras.EXTRAS_POSITION, 0)
                .putExtra(Extras.EXTRAS_BOARD_NAME, "g")
                .putExtra(Extras.EXTRAS_BOARD_TITLE, "Technology")
        val activity = instrumentation.startActivitySync(intent) as PostItemDetailActivity

        try {
            instrumentation.waitForIdleSync()
            val pager = activity.findViewById<ViewPager2>(R.id.thread_pager)
            val adapter = pager.adapter

            assertTrue(adapter is ThreadPagerAdapter)
            adapter as ThreadPagerAdapter
            assertEquals(2, adapter.itemCount)
            assertEquals(0, pager.currentItem)
            assertEquals(1001L, adapter.getActiveFragment(0)?.arguments
                    ?.getLong(Extras.EXTRAS_THREAD_ID))

            instrumentation.runOnMainSync { pager.setCurrentItem(1, false) }
            instrumentation.waitForIdleSync()

            val selectedFragment = adapter.getActiveFragment(1)
            assertNotNull(selectedFragment)
            assertEquals(1002L, selectedFragment?.arguments?.getLong(Extras.EXTRAS_THREAD_ID))
        } finally {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
        }
    }
}
