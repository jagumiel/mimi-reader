package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.CatalogPost
import com.mimireader.chanlib.models.ChanCatalog
import com.mimireader.chanlib.models.ChanPost
import io.reactivex.Single
import io.reactivex.functions.Function

object CatalogTableConnection {
    val LOG_TAG = CatalogTableConnection::class.java.simpleName
    @JvmStatic
    fun fetchPosts(boardName: String): Single<List<CatalogPost>> {
        return getInstance()!!.catalog().getAll(boardName).firstOrError()
    }

    @JvmStatic
    fun convertDbPostsToChanPosts(): Function<List<CatalogPost>, List<ChanPost>> {
        return Function { CatalogPostModels: List<CatalogPost> ->
            val posts: ArrayList<ChanPost> = ArrayList(CatalogPostModels.size)
            for (dbPost in CatalogPostModels) {
                posts.add(dbPost.toPost())
            }
            posts
        }
    }

    @JvmStatic
    fun replacePosts(catalog: ChanCatalog): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val boardName = catalog.boardName
            if (boardName.isNullOrBlank()) {
                return@singleOnIo false
            }
            val posts = catalog.posts ?: return@singleOnIo false
            val catalogPosts: MutableList<CatalogPost> = ArrayList(posts.size)
            for (i in posts.indices) {
                catalogPosts.add(CatalogPost(boardName, posts[i]))
            }
            val resultsList = getInstance()!!.catalog().replace(boardName, catalogPosts)
            var success = true
            for (value in resultsList) {
                if (success) {
                    success = value > 0
                }
            }
            success
        }
    }

    @JvmStatic
    fun removeThread(boardName: String, threadId: Long): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val catalogDao = getInstance()?.catalog() ?: return@singleOnIo false
            catalogDao.removeThread(boardName, threadId)
            true
        }
    }

    @JvmStatic
    fun clear(boardName: String): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val catalogDao = getInstance()?.catalog() ?: return@singleOnIo false
            catalogDao.clear(boardName)
            true
        }
    }
}
