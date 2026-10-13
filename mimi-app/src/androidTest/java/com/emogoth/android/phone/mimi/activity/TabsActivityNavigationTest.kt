package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.adapter.TabPagerAdapter
import com.emogoth.android.phone.mimi.fragment.HistoryFragment
import com.google.android.material.tabs.TabLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TabsActivityNavigationTest {
    @Test
    fun tabsAndSystemBarInsetsSurviveActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, TabsActivity::class.java)
        val scenario = ActivityScenario.launch<TabsActivity>(intent)
        var appBarTopPadding = -1
        var contentBottomPadding = -1
        var drawerTopPadding = -1

        try {
            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
                val adapter = pager.adapter as TabPagerAdapter
                adapter.addItem(
                        TabPagerAdapter.TabItem(
                                TabPagerAdapter.TabType.HISTORY,
                                Bundle(),
                                6262L,
                                "History",
                                null
                        )
                )
                pager.setCurrentItem(1, false)
            }
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val drawerLayout = activity.findViewById<DrawerLayout>(R.id.nav_drawer_container)
                val appBar = activity.findViewById<View>(R.id.appbar)
                val navDrawer = activity.findViewById<View>(R.id.nav_drawer)
                val mainContent = drawerLayout.getChildAt(0)
                val bars = ViewCompat.getRootWindowInsets(drawerLayout)?.getInsets(
                        WindowInsetsCompat.Type.systemBars() or
                                WindowInsetsCompat.Type.displayCutout()
                )

                assertTrue((bars?.top ?: 0) > 0)
                assertTrue(appBar.paddingTop >= (bars?.top ?: 0))
                assertTrue(mainContent.paddingBottom >= (bars?.bottom ?: 0))
                assertTrue(navDrawer.paddingTop >= (bars?.top ?: 0))
                appBarTopPadding = appBar.paddingTop
                contentBottomPadding = mainContent.paddingBottom
                drawerTopPadding = navDrawer.paddingTop
            }

            scenario.recreate()
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
                val adapter = pager.adapter as TabPagerAdapter
                val drawerLayout = activity.findViewById<DrawerLayout>(R.id.nav_drawer_container)
                val appBar = activity.findViewById<View>(R.id.appbar)
                val navDrawer = activity.findViewById<View>(R.id.nav_drawer)

                assertEquals(2, adapter.itemCount)
                assertEquals(1, pager.currentItem)
                assertEquals(appBarTopPadding, appBar.paddingTop)
                assertEquals(contentBottomPadding, drawerLayout.getChildAt(0).paddingBottom)
                assertEquals(drawerTopPadding, navDrawer.paddingTop)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun backDispatcherClosesDrawerBeforeNavigatingTabs() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, TabsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent) as TabsActivity

        try {
            instrumentation.waitForIdleSync()
            val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
            val adapter = pager.adapter as TabPagerAdapter
            val drawerLayout = activity.findViewById<DrawerLayout>(R.id.nav_drawer_container)
            val navDrawer = activity.findViewById<View>(R.id.nav_drawer)

            instrumentation.runOnMainSync {
                adapter.addItem(
                        TabPagerAdapter.TabItem(
                                TabPagerAdapter.TabType.HISTORY,
                                Bundle(),
                                5252L,
                                "History",
                                null
                        )
                )
                pager.setCurrentItem(1, false)
                drawerLayout.openDrawer(navDrawer, false)
            }
            instrumentation.waitForIdleSync()
            assertTrue(drawerLayout.isDrawerOpen(navDrawer))

            instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            waitUntil { !drawerLayout.isDrawerOpen(navDrawer) }
            instrumentation.waitForIdleSync()

            assertFalse(drawerLayout.isDrawerOpen(navDrawer))
            assertEquals(1, pager.currentItem)
            assertFalse(activity.isFinishing)

            instrumentation.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            instrumentation.waitForIdleSync()

            assertEquals(0, pager.currentItem)
            assertFalse(activity.isFinishing)
        } finally {
            instrumentation.runOnMainSync { activity.finishAndRemoveTask() }
        }
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
        }
    }

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
