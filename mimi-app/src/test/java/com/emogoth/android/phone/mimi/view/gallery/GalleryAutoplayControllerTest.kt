package com.emogoth.android.phone.mimi.view.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryAutoplayControllerTest {
    @Test
    fun imageTimerOnlyRunsForReadyVisiblePageWhileResumed() {
        val controller = GalleryAutoplayController(enabled = true)
        controller.onPageSelected(position = 2, count = 5)

        assertFalse(controller.shouldScheduleImage(position = 2, contentReady = true))

        controller.onHostResume()
        assertFalse(controller.shouldScheduleImage(position = 2, contentReady = false))
        assertFalse(controller.shouldScheduleImage(position = 1, contentReady = true))
        assertTrue(controller.shouldScheduleImage(position = 2, contentReady = true))

        controller.onHostPause()
        assertFalse(controller.shouldScheduleImage(position = 2, contentReady = true))
    }

    @Test
    fun completionAdvancesOnceAndRejectsStaleEvents() {
        val controller = GalleryAutoplayController(enabled = true)
        controller.onHostResume()
        controller.onPageSelected(position = 1, count = 4)

        assertNull(controller.requestAdvance(position = 0))
        assertEquals(2, controller.requestAdvance(position = 1))
        assertNull(controller.requestAdvance(position = 1))

        controller.onPageSelected(position = 2, count = 4)
        assertEquals(3, controller.requestAdvance(position = 2))
    }

    @Test
    fun autoplayDoesNotWrapOrRunWhenDisabled() {
        val enabled = GalleryAutoplayController(enabled = true)
        enabled.onHostResume()
        enabled.onPageSelected(position = 2, count = 3)

        assertNull(enabled.requestAdvance(position = 2))

        val disabled = GalleryAutoplayController(enabled = false)
        disabled.onHostResume()
        disabled.onPageSelected(position = 0, count = 2)

        assertFalse(disabled.shouldScheduleImage(position = 0, contentReady = true))
        assertNull(disabled.requestAdvance(position = 0))
    }
}
