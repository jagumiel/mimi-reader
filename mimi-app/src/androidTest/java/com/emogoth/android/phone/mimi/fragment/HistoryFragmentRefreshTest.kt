package com.emogoth.android.phone.mimi.fragment

import android.content.Intent
import android.os.SystemClock
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.TabsActivity
import com.emogoth.android.phone.mimi.adapter.TabPagerAdapter
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryFragmentRefreshTest {
    @Test
    fun historyErrorOffersInlineAndToolbarRefresh() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, TabsActivity::class.java)
        val scenario = ActivityScenario.launch<TabsActivity>(intent)

        try {
            waitUntil {
                var refreshAvailable = false
                scenario.onActivity { activity ->
                    val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
                    val adapter = pager.adapter as TabPagerAdapter
                    refreshAvailable = adapter.getActiveFragment(pager.currentItem) is BoardItemListFragment &&
                            activity.findViewById<Toolbar>(R.id.mimi_toolbar)
                                    .menu.findItem(R.id.refresh_menu) != null
                }
                refreshAvailable
            }

            scenario.onActivity { it.openHistoryPage(false) }
            instrumentation.waitForIdleSync()
            waitUntil {
                var ready = false
                scenario.onActivity { activity ->
                    val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
                    val adapter = pager.adapter as TabPagerAdapter
                    ready = adapter.getActiveFragment(pager.currentItem) is HistoryFragment
                }
                ready
            }

            scenario.onActivity { activity ->
                val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
                val adapter = pager.adapter as TabPagerAdapter
                val fragment = adapter.getActiveFragment(pager.currentItem) as HistoryFragment
                val retryButton = fragment.requireView().findViewById<View>(R.id.history_retry_button)

                fragment.showLoadError(IllegalStateException("Test history failure"))

                val snackbarAction = activity.findViewById<View>(
                        com.google.android.material.R.id.snackbar_action
                )
                assertTrue(
                        retryButton.visibility == View.VISIBLE ||
                                snackbarAction?.visibility == View.VISIBLE
                )
                assertNotNull(activity.findViewById<Toolbar>(R.id.mimi_toolbar)
                        .menu.findItem(R.id.refresh_menu))
                if (retryButton.visibility == View.VISIBLE) {
                    retryButton.performClick()
                } else {
                    snackbarAction.performClick()
                }
            }

            waitUntil {
                var hidden = false
                scenario.onActivity { activity ->
                    val pager = activity.findViewById<ViewPager2>(R.id.tabs_pager)
                    val fragment = (pager.adapter as TabPagerAdapter)
                            .getActiveFragment(pager.currentItem) as? HistoryFragment
                    hidden = fragment?.view
                            ?.findViewById<View>(R.id.history_retry_button)
                            ?.visibility == View.GONE
                }
                hidden
            }
        } finally {
            scenario.close()
        }
    }

    private fun waitUntil(timeoutMs: Long = 2_000L, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
        }
        assertTrue(condition())
    }
}
