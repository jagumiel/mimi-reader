package com.emogoth.android.phone.mimi.db

import com.emogoth.android.phone.mimi.db.models.QueueItem
import com.emogoth.android.phone.mimi.db.models.RefreshQueue
import io.reactivex.Maybe
import io.reactivex.Single

object RefreshQueueTableConnection {
    @JvmStatic
    fun fetchAllItems(): Single<List<QueueItem>> {
        return MimiDatabase.getInstance()?.refreshQueue()?.getAllQueueItems()
                ?: Single.just(emptyList())
    }

    @JvmStatic
    fun fetchItem(boardName: String, threadId: Long): Single<QueueItem> {
        return MimiDatabase.getInstance()?.refreshQueue()?.getQueueItem(boardName, threadId)
                ?: Single.just(QueueItem())
    }

    @JvmStatic
    fun nextItem(): Maybe<QueueItem> {
        return MimiDatabase.getInstance()?.refreshQueue()?.nextItem() ?: Maybe.just(QueueItem())
    }

    @JvmStatic
    fun addItem(historyId: Int, threadSize: Int, replyCount: Int, lastRefresh: Long, queueId: Int? = null): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val refreshQueueDao = MimiDatabase.getInstance()?.refreshQueue() ?: return@singleOnIo false
            val item = RefreshQueue(queueId, historyId, threadSize, replyCount, lastRefresh)
            refreshQueueDao.upsert(item)
            true
        }
    }

    @JvmStatic
    fun removeItem(boardName: String, threadId: Long): Single<Int> {
        return MimiDatabase.getInstance()?.refreshQueue()?.removeItem(boardName, threadId)
                ?: Single.just(-1)
    }

    @JvmStatic
    fun removeUnwatched(): Single<Int> {
        return MimiDatabase.getInstance()?.refreshQueue()?.removeUnwatched() ?: Single.just(-1)
    }

    @JvmStatic
    fun removeCompleted(): Single<Int> {
        return MimiDatabase.getInstance()?.refreshQueue()?.removeComplete() ?: Single.just(-1)
    }
}
