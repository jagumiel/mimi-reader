package com.emogoth.android.phone.mimi.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.emogoth.android.phone.mimi.db.MimiDatabase
import com.mimireader.chanlib.models.ChanBoard


@Entity(tableName = MimiDatabase.BOARDS_TABLE, indices = [Index(value = [Board.NAME], unique = true)])
data class Board(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = ID)
        val id: Int? = null,

        @ColumnInfo(name = TITLE)
        val title: String = "",

        @ColumnInfo(name = NAME)
        val name: String = "",

        @ColumnInfo(name = ACCESS_COUNT)
        val accessCount: Int = 0,

        @ColumnInfo(name = POST_COUNT)
        val postCount: Int = 0,

        @ColumnInfo(name = CATEGORY)
        val category: Int = 0,

        @ColumnInfo(name = LAST_ACCESSED)
        val lastAccessed: Long = 0,

        @ColumnInfo(name = FAVORITE)
        val favorite: Boolean = false,

        @ColumnInfo(name = NSFW)
        val nsfw: Boolean = false,

        @ColumnInfo(name = POSTS_PER_PAGE)
        val perPage: Int = 0,

        @ColumnInfo(name = NUMBER_OF_PAGES)
        val pages: Int = 0,

        @ColumnInfo(name = VISIBLE)
        val visible: Boolean = false,

        @ColumnInfo(name = ORDER_INDEX)
        val orderIndex: Int = 0,

        @ColumnInfo(name = MAX_FILESIZE)
        val maxFileSize: Int = 0
) {
    companion object {
        const val ID = "id"
        const val TITLE = "board_name"
        const val NAME = "board_path"
        const val CATEGORY = "board_category"
        const val ACCESS_COUNT = "access_count"
        const val POST_COUNT = "post_count"
        const val LAST_ACCESSED = "last_accessed"
        const val FAVORITE = "favorite" // 1 for favorite
        const val NSFW = "nsfw" // 1 for sfw
        const val POSTS_PER_PAGE = "per_page"
        const val NUMBER_OF_PAGES = "pages"
        const val VISIBLE = "visible"
        const val ORDER_INDEX = "order_index"
        const val MAX_FILESIZE = "max_file_size"

        val ORDERBY_NONE = 0
        val ORDERBY_NAME = 1
        val ORDERBY_PATH = 2
        const val ORDERBY_CATEGORY = 8
        val ORDERBY_ACCESS_COUNT = 4
        val ORDERBY_POST_COUNT = 5
        val ORDERBY_LAST_ACCESSED = 6
        val ORDERBY_FAVORITE = 7 // Order by path as a secondary method
    }

    fun toChanBoard(): ChanBoard {
        val newBoard = ChanBoard()
        val boardName = this.name // Path is now Name
        val boardTitle = this.title // Name is now Title
        val ws = if (this.nsfw) 0 else 1
        val favorite = if (this.favorite) 1 else 0
        val postsPerPage = this.perPage
        val pages = this.pages

        newBoard.name = boardName
        newBoard.title = boardTitle
        newBoard.wsBoard = ws
        newBoard.isFavorite = favorite == 1
        newBoard.perPage = postsPerPage
        newBoard.pages = pages

        return newBoard
    }
}
