package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.adapter.TabPagerAdapter
import com.emogoth.android.phone.mimi.fragment.HistoryFragment
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabsActivityNavigationTest {
    @Test
    fun mainTabsUseViewPager2AndTrackDynamicItems() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, TabsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as TabsActivity

        try {
            instrumentation.waitForIdleSync()
            val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
            val tabLayout = activity.findViewById<TabLayout>(R.id.tab_layout)
            val adapter = pager.adapter

            assertTrue(adapter is TabPagerAdapter)
            adapter as TabPagerAdapter
            assertEquals(1, adapter.itemCount)
            assertEquals(1, tabLayout.tabCount)

            instrumentation.runOnMainSync {
                adapter.addItem(
                        TabPagerAdapter.TabItem(
                                TabPagerAdapter.TabType.HISTORY,
                                Bundle(),
                                4242L,
                                "History",
                                null
                        )
                )
            }
            instrumentation.waitForIdleSync()

            assertEquals(2, adapter.itemCount)
            assertEquals(2, tabLayout.tabCount)

            instrumentation.runOnMainSync { pager.setCurrentItem(1, false) }
            instrumentation.waitForIdleSync()
            assertTrue(adapter.getActiveFragment(1) is HistoryFragment)

            val originalId = adapter.getItemId(1)
            instrumentation.runOnMainSync {
                adapter.setItemAtIndex(
                        1,
                        TabPagerAdapter.TabItem(
                                TabPagerAdapter.TabType.HISTORY,
                                Bundle(),
                                4343L,
                                "Bookmarks",
                                null
                        )
                )
            }
            instrumentation.waitForIdleSync()

            assertNotEquals(originalId, adapter.getItemId(1))
            assertEquals("BOOKMARKS", tabLayout.getTabAt(1)?.text)

            instrumentation.runOnMainSync {
                pager.setCurrentItem(0, false)
                adapter.removeItemAtIndex(1)
            }
            instrumentation.waitForIdleSync()

            assertEquals(1, adapter.itemCount)
            assertEquals(1, tabLayout.tabCount)
        } finally {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
        }
    }
}
