package com.emogoth.android.phone.mimi.activity

import android.content.Intent
import androidx.slidingpanelayout.widget.SlidingPaneLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.fragment.BoardItemListFragment
import com.emogoth.android.phone.mimi.fragment.NavDrawerFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyActivityRestorationTest {
    @Test
    fun listActivityRestoresHomeFragmentWithoutDuplicates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, PostItemListActivity::class.java)
        val scenario = ActivityScenario.launch<PostItemListActivity>(intent)

        try {
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val fragments = manager.fragments

                assertEquals(0, manager.backStackEntryCount)
                assertEquals(1, fragments.filterIsInstance<BoardItemListFragment>().size)
                assertEquals(1, fragments.filterIsInstance<NavDrawerFragment>().size)
                assertTrue(manager.findFragmentById(R.id.postitem_list) is BoardItemListFragment)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun slidingPanelRestoresFragmentsWithoutDuplicates() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = Intent(instrumentation.targetContext, SlidingPanelActivity::class.java)
        val scenario = ActivityScenario.launch<SlidingPanelActivity>(intent)

        try {
            instrumentation.waitForIdleSync()
            scenario.recreate()
            instrumentation.waitForIdleSync()

            scenario.onActivity { activity ->
                val manager = activity.supportFragmentManager
                manager.executePendingTransactions()
                val fragments = manager.fragments

                assertTrue(activity.findViewById<SlidingPaneLayout>(R.id.panel_layout).isOpen)
                assertEquals(1, fragments.filterIsInstance<BoardItemListFragment>().size)
                assertEquals(1, fragments.filterIsInstance<NavDrawerFragment>().size)
            }
        } finally {
            scenario.close()
        }
    }

}
