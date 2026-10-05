package com.emogoth.android.phone.mimi.db

import android.util.Log
import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.ArchivedPost
import com.mimireader.chanlib.models.ArchivedChanPost
import com.mimireader.chanlib.models.ArchivedChanThread
import io.reactivex.Flowable
import io.reactivex.Single
import java.util.*

object ArchivedPostTableConnection {
    fun getPosts(boardName: String, threadId: Long): Single<List<ArchivedPost>> {
        return getInstance()?.archivedPosts()?.getThread(boardName, threadId)?.firstOrError() ?: Single.just(emptyList())
    }

    fun watchPosts(boardName: String, threadId: Long): Flowable<List<ArchivedPost>> {
        return getInstance()?.archivedPosts()?.getThread(boardName, threadId)  ?: Flowable.just(emptyList())
    }

//    fun putThreadSingle(thread: ArchivedChanThread): Single<Boolean> {
//        return Single.defer(Callable<SingleSource<ArchivedChanThread>> { Single.just(thread) })
//                .map { obj: ArchivedChanThread? -> convertToArchivedPosts() }
//                .flatMap(Function<List<ArchivedPost?>, SingleSource<Boolean>> { archivedPosts: List<ArchivedPost?>? ->
//                    getInstance()!!.archivedPosts().insert(archivedPosts)
//                    Single.just(true)
//                })
//    }

    fun putThread(thread: ArchivedChanThread): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val archivedPostDao = getInstance()?.archivedPosts() ?: return@singleOnIo false
            val posts = convertToArchivedPosts(thread)
            archivedPostDao.insert(posts)
            Log.d("ArchivedPostsTable", "Put an archived thread into the database: saved " + posts.size + " posts")
            true
        }
    }

    @JvmStatic
    fun removeThread(boardName: String, threadId: Long): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val archivedPostDao = getInstance()?.archivedPosts() ?: return@singleOnIo false
            archivedPostDao.removeThread(boardName, threadId)
            true
        }
    }

    @JvmStatic
    fun removeThreads(threads: List<Long>): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val archivedPostDao = getInstance()?.archivedPosts() ?: return@singleOnIo false
            archivedPostDao.removeThreads(threads)
            true
        }
    }

    @JvmStatic
    fun removeAllThreads(): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val archivedPostDao = getInstance()?.archivedPosts() ?: return@singleOnIo false
            archivedPostDao.clear()
            true
        }
    }

    private fun convertToArchivedPosts(archivedChanThread: ArchivedChanThread): List<ArchivedPost> {
        val posts: MutableList<ArchivedPost> = ArrayList(archivedChanThread.posts.size)
        for (post in archivedChanThread.posts) {
            if (post is ArchivedChanPost) {
                val dbPost = ArchivedPost(null, post.no, archivedChanThread.threadId, archivedChanThread.boardName, post.mediaLink, post.thumbLink, archivedChanThread.name, archivedChanThread.domain)
                posts.add(dbPost)
            }
        }
        if (posts.size != archivedChanThread.posts.size) {
            throw Exception("Not all posts in ArchivedChanThread are of type ArchivedChanPost")
        }
        return posts
    }
}
