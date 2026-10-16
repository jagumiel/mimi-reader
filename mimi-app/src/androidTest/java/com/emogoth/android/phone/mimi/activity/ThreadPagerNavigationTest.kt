package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.adapter.ThreadPagerAdapter
import com.emogoth.android.phone.mimi.fragment.NavDrawerFragment
import com.emogoth.android.phone.mimi.fragment.ThreadPagerFragment
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
    fun threadCollectionRestoresSelectedThreadWithoutDuplicateFragments() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val threads = arrayListOf(
                ThreadInfo(1001L, "g", "Technology", false),
                ThreadInfo(1002L, "g", "Technology", false)
        )
        val intent = Intent(instrumentation.targetContext, PostItemDetailActivity::class.java)
                .putParcelableArrayListExtra(Extras.EXTRAS_THREAD_LIST, threads)
                .putExtra(Extras.EXTRAS_POSITION, 0)
                .putExtra(Extras.EXTRAS_BOARD_NAME, "g")
                .putExtra(Extras.EXTRAS_BOARD_TITLE, "Technology")
        val scenario = ActivityScenario.launch<PostItemDetailActivity>(intent)

        try {
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.thread_pager)
                val adapter = pager.adapter

                assertTrue(adapter is ThreadPagerAdapter)
                adapter as ThreadPagerAdapter
                assertEquals(2, adapter.itemCount)
                assertEquals(0, pager.currentItem)
                assertEquals(1001L, adapter.getActiveFragment(0)?.arguments
                        ?.getLong(Extras.EXTRAS_THREAD_ID))

                pager.setCurrentItem(1, false)
            }
            instrumentation.waitForIdleSync()

            scenario.recreate()
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.thread_pager)
                val adapter = pager.adapter as ThreadPagerAdapter
                val fragments = activity.supportFragmentManager.fragments

                assertEquals(2, adapter.itemCount)
                assertEquals(1, pager.currentItem)
                val selectedFragment = adapter.getActiveFragment(1)
                assertNotNull(selectedFragment)
                assertEquals(1002L, selectedFragment?.arguments
                        ?.getLong(Extras.EXTRAS_THREAD_ID))
                assertEquals(1, fragments.filterIsInstance<ThreadPagerFragment>().size)
                assertEquals(1, fragments.filterIsInstance<NavDrawerFragment>().size)
            }
        } finally {
            scenario.close()
        }
    }
}
