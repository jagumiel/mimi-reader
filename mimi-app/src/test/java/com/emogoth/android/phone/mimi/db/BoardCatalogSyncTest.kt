package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.models.Board
import com.emogoth.android.phone.mimi.util.BoardCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardCatalogSyncTest {
    @Test
    fun syncMakesNewBoardsVisibleAndPreservesExistingUserState() {
        val existing = listOf(
            Board(
                id = 1,
                title = "Old technology title",
                name = "g",
                accessCount = 12,
                favorite = true,
                visible = false,
                orderIndex = 4
            ),
            Board(
                id = 3,
                title = "Random",
                name = "b",
                visible = false,
                category = BoardCatalog.MISC
            ),
            Board(id = 2, title = "Removed", name = "removed", visible = true)
        )
        val incoming = listOf(
            Board(title = "Technology", name = "g", nsfw = false, pages = 10),
            Board(title = "Random", name = "b", nsfw = true, pages = 10),
            Board(title = "Virtual YouTubers", name = "vt", nsfw = false, pages = 10)
        )

        val merged = BoardTableConnection.mergeBoardCatalog(existing, incoming)
        val technology = merged.first { it.name == "g" }
        val virtualYouTubers = merged.first { it.name == "vt" }
        val random = merged.first { it.name == "b" }
        val removed = merged.first { it.name == "removed" }

        assertEquals(1, technology.id)
        assertEquals("Technology", technology.title)
        assertEquals(12, technology.accessCount)
        assertTrue(technology.favorite)
        assertTrue(technology.visible)
        assertEquals(4, technology.orderIndex)
        assertEquals(BoardCatalog.INTERESTS, technology.category)

        assertTrue(virtualYouTubers.visible)
        assertEquals(5, virtualYouTubers.orderIndex)
        assertEquals(BoardCatalog.JAPANESE_CULTURE, virtualYouTubers.category)
        assertFalse(random.visible)
        assertFalse(removed.visible)
    }

    @Test
    fun syncRemovesDuplicatesAndKeepsApiOnlyBoards() {
        val incoming = listOf(
            Board(title = "Technology", name = "g"),
            Board(title = "Technology duplicate", name = "g"),
            Board(title = "Adult Requests", name = "r", nsfw = true),
            Board(title = "Off-Topic", name = "trash", nsfw = true)
        )

        val merged = BoardTableConnection.mergeBoardCatalog(emptyList(), incoming)

        assertEquals(listOf("g", "r", "trash"), merged.map { it.name })
        assertTrue(merged.first { it.name == "r" }.visible)
        assertTrue(merged.first { it.name == "trash" }.visible)
    }
}
