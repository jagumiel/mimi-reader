package com.emogoth.android.phone.mimi.widget

import org.junit.Assert.*
import org.junit.Test

class ThreadScrollPositionTest {
    @Test fun labelsAreBoundedAndEmptyListsHaveNoPopup() {
        assertNull(ThreadScrollPosition.label(0, 0))
        assertEquals("1 / 80", ThreadScrollPosition.label(-1, 80))
        assertEquals("80 / 80", ThreadScrollPosition.label(100, 80))
        assertEquals("1 / 1", ThreadScrollPosition.label(0, 1))
    }
    @Test fun nonScrollableAndUnmeasuredViewsHaveNoRange() {
        for (count in listOf(0, 1, 80)) {
            assertEquals(0, ThreadScrollPosition.range(count, 400, false))
            assertEquals(0, ThreadScrollPosition.range(count, 0, true))
        }
        assertEquals(0, ThreadScrollPosition.range(1, 400, true))
    }
    @Test fun endpointsReachExactlyTheFirstAndLastPost() {
        val range = ThreadScrollPosition.range(80, 400, true)
        assertEquals(0, ThreadScrollPosition.position(0, range, 80))
        assertEquals(79, ThreadScrollPosition.position(range, range, 80))
        assertEquals(0, ThreadScrollPosition.offset(0, 0.0, range, 80))
        assertEquals(range, ThreadScrollPosition.offset(79, 0.0, range, 80))
    }
    @Test fun intermediateProgressIsBasedOnIndexNotRowHeight() {
        val range = ThreadScrollPosition.range(80, 400, true)
        assertEquals(40, ThreadScrollPosition.position(range / 2, range, 80))
        for (index in 0..79) {
            assertEquals(index, ThreadScrollPosition.position(
                ThreadScrollPosition.offset(index, 0.0, range, 80), range, 80))
        }
    }
    @Test fun invalidOffsetsAndPositionsAreClamped() {
        assertEquals(0, ThreadScrollPosition.position(-20, 1000, 80))
        assertEquals(79, ThreadScrollPosition.position(2000, 1000, 80))
        assertEquals(0, ThreadScrollPosition.offset(-1, -4.0, 1000, 80))
        assertEquals(1000, ThreadScrollPosition.offset(90, 0.0, 1000, 80))
        assertEquals(0, ThreadScrollPosition.position(3, 0, 0))
    }
    @Test fun largeCountsCannotOverflowTheRangeOrLoseTheLastItem() {
        val range = ThreadScrollPosition.range(Int.MAX_VALUE, 400, true)
        assertTrue(range > 0)
        assertEquals(Int.MAX_VALUE, range + 400)
        assertEquals(Int.MAX_VALUE - 1, ThreadScrollPosition.position(range, range, Int.MAX_VALUE))
    }
}
