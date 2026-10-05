package com.emogoth.android.phone.mimi.db.dao

import androidx.room.Dao
import androidx.room.Query
import com.emogoth.android.phone.mimi.db.MimiDatabase
import com.emogoth.android.phone.mimi.db.models.CatalogPost
import io.reactivex.Flowable

@Dao
abstract class CatalogAccess : BaseDao<CatalogPost>() {

    @Query("SELECT * FROM ${MimiDatabase.CATALOG_TABLE} WHERE ${CatalogPost.BOARD_NAME} = :boardName")
    abstract fun getAll(boardName: String): Flowable<List<CatalogPost>>

    @Query("DELETE FROM ${MimiDatabase.CATALOG_TABLE} WHERE ${CatalogPost.BOARD_NAME} = :boardName AND ${CatalogPost.POST_ID} = :threadId")
    abstract fun removeThread(boardName: String, threadId: Long): Int

    @Query("DELETE FROM ${MimiDatabase.CATALOG_TABLE} WHERE ${CatalogPost.BOARD_NAME} = :boardName")
    abstract fun clear(boardName: String)

    @androidx.room.Transaction
    open fun replace(boardName: String, posts: List<CatalogPost>): List<Long> {
        clear(boardName)
        return insert(posts)
    }
}
