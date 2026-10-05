package com.emogoth.android.phone.mimi.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.emogoth.android.phone.mimi.db.MimiDatabase
import com.emogoth.android.phone.mimi.db.models.Post
import io.reactivex.Flowable

@Dao
abstract class PostAccess : BaseDao<Post>() {
    @Query("SELECT * FROM ${MimiDatabase.POSTS_TABLE}")
    abstract fun getAll(): Flowable<List<Post>>

    @Query("SELECT * FROM ${MimiDatabase.POSTS_TABLE} WHERE ${Post.THREAD_ID} = :threadId")
    abstract fun getThread(threadId: Long): Flowable<List<Post>>

    @Query("SELECT * FROM ${MimiDatabase.POSTS_TABLE} WHERE ${Post.THREAD_ID} = ${Post.POST_ID}")
    abstract fun getFirstPosts(): Flowable<List<Post>>

    @Query("DELETE FROM ${MimiDatabase.POSTS_TABLE} WHERE ${Post.THREAD_ID} in (:ids)")
    abstract fun deleteThreads(ids: List<Long>)

    @Query("DELETE FROM ${MimiDatabase.POSTS_TABLE} WHERE ${Post.THREAD_ID} = :id")
    abstract fun deleteThread(id: Long)

    @Query("DELETE FROM ${MimiDatabase.POSTS_TABLE}")
    abstract fun clear()

    @Transaction
    open fun replaceThread(threadId: Long, posts: List<Post>): List<Long> {
        deleteThread(threadId)
        return insert(posts)
    }
}
