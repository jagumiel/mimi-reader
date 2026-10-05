package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.UserPost
import io.reactivex.Flowable
import io.reactivex.Single
import java.util.*
import java.util.concurrent.TimeUnit

object UserPostTableConnection {
    val LOG_TAG = UserPostTableConnection::class.java.simpleName

    @JvmStatic
    fun fetchPosts(boardName: String, threadId: Long): Single<List<UserPost>> {
        return watchPosts(boardName, threadId).first(emptyList())
    }

    fun watchPosts(boardName: String, threadId: Long): Flowable<List<UserPost>> {
        return getInstance()?.userPosts()?.getPosts(boardName, threadId)
                ?: Flowable.just(emptyList())
    }

    @JvmStatic
    fun addPost(boardName: String, threadId: Long, postId: Long): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val userPost = UserPost(null, threadId, postId, boardName, System.currentTimeMillis())
            val userPostDao = getInstance()?.userPosts() ?: return@singleOnIo false
            userPostDao.upsert(userPost)
            true
        }
    }

    @JvmStatic
    fun removePost(boardName: String, threadId: Long, postId: Long): Single<Boolean> {
        return getInstance()?.userPosts()?.removePost(boardName, threadId, postId)
                ?.map { integer: Int -> integer > 0 } ?: Single.just(false)
    }

    @JvmStatic
    fun prune(days: Int): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val userPostDao = getInstance()?.userPosts() ?: return@singleOnIo false
            userPostDao.prune(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
            true
        }
    }

    fun postIdList(userPosts: List<UserPost>): List<Long> {
        val postIds = ArrayList<Long>(userPosts.size)
        for ((_, _, postId) in userPosts) {
            postIds.add(postId)
        }
        return postIds
    }
}
