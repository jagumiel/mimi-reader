package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.Post
import com.mimireader.chanlib.models.ChanPost
import com.mimireader.chanlib.models.ChanThread
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.Function

object PostTableConnection {
    val LOG_TAG = PostTableConnection::class.java.simpleName

    @JvmStatic
    fun fetchThread(threadId: Long): Single<List<Post>> {
        return getInstance()?.posts()?.getThread(threadId)?.firstOrError()
                ?: Single.just(emptyList())
    }

    @JvmStatic
    fun watchThread(threadId: Long): Flowable<List<Post>> {
        return getInstance()?.posts()?.getThread(threadId) ?: Flowable.just(emptyList())
    }

    @JvmStatic
    fun mapDbPostsToChanThread(boardName: String, threadId: Long): Function<List<Post>, ChanThread> {
        return Function { Posts: List<Post> -> convertDbPostsToChanThread(boardName, threadId, Posts) }
    }

    fun convertDbPostsToChanThread(boardName: String, threadId: Long, posts: List<Post>): ChanThread {
        if (posts.isEmpty()) {
            return ChanThread.empty()
        }
        val p: ArrayList<ChanPost> = ArrayList(posts.size)
        for (dbPost in posts) {
            p.add(dbPost.toPost())
        }
        return ChanThread(boardName, threadId, p)
    }

    @JvmStatic
    fun putThread(thread: ChanThread): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val postDao = getInstance()?.posts() ?: return@singleOnIo false
            postDao.replaceThread(thread.threadId, convertToPosts(thread)).all { it > 0 }
        }
    }

    private fun convertToPosts(thread: ChanThread): List<Post> {
        if (thread.posts.size == 0) {
            return emptyList()
        }
        val posts: MutableList<Post> = ArrayList(thread.posts.size)
        for (i in thread.posts.indices) {
            posts.add(Post(thread.boardName, thread.threadId, thread.posts[i]))
        }
        return posts
    }

    @JvmStatic
    fun removeThreads(threads: List<Long>): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val postDao = getInstance()?.posts() ?: return@singleOnIo false
            postDao.deleteThreads(threads)
            true
        }
    }

    @JvmStatic
    fun removeThread(thread: Long): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val postDao = getInstance()?.posts() ?: return@singleOnIo false
            postDao.deleteThread(thread)
            true
        }
    }

    @JvmStatic
    fun fetchThreadIds(): Single<List<Long>> {
        return getInstance()?.posts()?.getFirstPosts()?.firstOrError()
                ?.flatMap { posts: List<Post> ->
                    if (posts.isEmpty()) {
                        return@flatMap Single.just(emptyList<Long>())
                    }
                    val values: ArrayList<Long> = ArrayList(posts.size)
                    for (p in posts) {
                        values.add(p.toPost().no)
                    }
                    Single.just(values)
                } ?: Single.just(emptyList())
    }

    @JvmStatic
    fun removeAllThreads(): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val postDao = getInstance()?.posts() ?: return@singleOnIo false
            postDao.clear()
            true
        }
    }
}
