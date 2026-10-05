package com.emogoth.android.phone.mimi.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GalleryDownloadQueueTest {
    @Test
    fun prioritizesPendingItemsByDistanceFromVisiblePosition() {
        val queue = GalleryDownloadQueue(items(7), 7, 3)

        val orderedIds = queue.takeAvailable(true, emptySet()).map { it.id }

        assertEquals(listOf(3L, 4L, 2L, 5L, 1L, 6L, 0L), orderedIds)
    }

    @Test
    fun neverExceedsTheConfiguredConcurrencyLimit() {
        val queue = GalleryDownloadQueue(items(6), 2, 2)

        assertEquals(listOf(2L, 3L), queue.takeAvailable(true, emptySet()).map { it.id })
        assertTrue(queue.takeAvailable(true, emptySet()).isEmpty())

        queue.finished(2L)

        assertEquals(listOf(1L), queue.takeAvailable(true, emptySet()).map { it.id })
    }

    @Test
    fun downloadsOnlyRequestedItemsWhenPreloadingIsDisabled() {
        val queue = GalleryDownloadQueue(items(6), 2, 2)

        assertTrue(queue.takeAvailable(false, emptySet()).isEmpty())
        assertEquals(
                listOf(3L, 0L),
                queue.takeAvailable(false, setOf(0L, 3L)).map { it.id }
        )
    }

    @Test
    fun reprioritizesRemainingItemsWhenVisiblePositionChanges() {
        val queue = GalleryDownloadQueue(items(8), 2, 0)
        assertEquals(listOf(0L, 1L), queue.takeAvailable(true, emptySet()).map { it.id })

        queue.prioritize(7)
        queue.finished(0L)
        queue.finished(1L)

        assertEquals(listOf(7L, 6L), queue.takeAvailable(true, emptySet()).map { it.id })
    }

    private fun items(count: Int): List<DownloadItem> {
        return (0 until count).map { index ->
            DownloadItem(
                    index.toLong(),
                    "https://example.test/$index",
                    "https://example.test/${index}s",
                    1,
                    1,
                    File("$index.bin"),
                    "$index.bin"
            )
        }
    }
}
