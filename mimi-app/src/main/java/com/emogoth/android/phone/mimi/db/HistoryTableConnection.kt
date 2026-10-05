package com.emogoth.android.phone.mimi.db

import android.text.TextUtils
import android.util.Log
import com.emogoth.android.phone.mimi.db.ArchivedPostTableConnection.removeAllThreads
import com.emogoth.android.phone.mimi.db.ArchivedPostTableConnection.removeThreads
import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.History
import com.mimireader.chanlib.models.ChanPost
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function
import java.util.*
import java.util.concurrent.TimeUnit

object HistoryTableConnection {
    val LOG_TAG: String = HistoryTableConnection::class.java.simpleName
    const val HISTORY = 1
    const val BOOKMARKS = 2

    @JvmStatic
    fun fetchPost(boardName: String, threadId: Long): Single<History> {
        return observeThread(boardName, threadId).first(History())
    }

    @JvmStatic
    fun observeThread(boardName: String, threadId: Long): Flowable<History> {
        return getInstance()?.history()?.getHistoryByThread(boardName, threadId)
                ?: Flowable.just(History())
    }

    @JvmStatic
    fun fetchHistory(): Single<List<History>> {
        return observeHistory().first(emptyList()) ?: Single.just(emptyList())
    }

    @JvmStatic
    fun observeHistory(): Flowable<List<History>> {
        return getInstance()?.history()?.getAll() ?: Flowable.just(emptyList())
    }

    @JvmStatic
    fun fetchHistory(watched: Boolean): Single<List<History>> {
        return if (watched) {
            getInstance()?.history()?.getBookmarks()?.first(emptyList()) ?: Single.just(emptyList())
        } else {
            getInstance()?.history()?.getHistory()?.first(emptyList()) ?: Single.just(emptyList())
        }
    }

    @JvmStatic
    fun observeHistory(watched: Boolean): Flowable<List<History>> {
        return if (watched) {
            getInstance()?.history()?.getBookmarks() ?: Flowable.just(emptyList())
        } else {
            getInstance()?.history()?.getHistory() ?: Flowable.just(emptyList())
        }
    }

    @JvmStatic
    fun observeHistory(watched: Boolean, count: Int): Flowable<List<History>> {
        return if (watched) {
            getInstance()?.history()?.getBookmarks(count)
                    ?: Flowable.just(emptyList())
        } else {
            getInstance()?.history()?.getHistory(count)
                    ?: Flowable.just(emptyList())
        }
    }

    @JvmStatic
    fun fetchActiveBookmarks(count: Int): Single<List<History>> {
        return if (count > 0) {
            getInstance()?.history()?.getActiveBookmarks(count)?.first(emptyList())
                    ?: Single.just(emptyList())
        } else {
            getInstance()?.history()?.getActiveBookmarks()?.first(emptyList())
                    ?: Single.just(emptyList())
        }
    }

    @JvmStatic
    fun watchActiveBookmarks(count: Int): Flowable<List<History>> {
        return if (count > 0) {
            getInstance()?.history()?.getActiveBookmarks(count) ?: Flowable.just(emptyList())
        } else {
            getInstance()?.history()?.getActiveBookmarks() ?: Flowable.just(emptyList())
        }
    }

    private fun fetchHistorySortedByLastAccess(): Single<List<History>> {
        return getInstance()?.history()?.getAllByLastAccess()?.first(emptyList())
                ?: Single.just(emptyList())
    }

//    @JvmStatic
//    fun setThreadActive(boardName: String, threadId: Long, active: Boolean): Single<Boolean> {
//        return getInstance()?.history()?.threadActive(boardName, threadId, active)?.map { value: Int -> value > 0 }
//                ?: Single.just(false)
//    }

    @JvmStatic
    fun setHistoryRemovedStatus(boardName: String, threadId: Long, removed: Boolean): Single<Boolean> {
        return getInstance()?.history()?.threadRemoved(boardName, threadId, removed)?.map { value: Int -> value > 0 }
                ?: Single.just(false)
    }

    @JvmStatic
    fun setBookmark(boardName: String, threadId: Long, watched: Boolean): Single<Boolean> {
        return getInstance()?.history()?.watched(boardName, threadId, watched)?.map { value: Int -> value > 0 }
                ?: Single.just(false)
    }

    @JvmStatic
    fun setThreadSize(boardName: String, threadId: Long, size: Int): Single<Boolean> {
        return getInstance()?.history()?.threadSize(boardName, threadId, size)?.map { value: Int -> value > 0 }
                ?: Single.just(false)
    }

    @JvmStatic
    fun keepLatest(numberOfThreadsToKeep: Int): Single<Boolean> {
        return fetchHistorySortedByLastAccess()
                .flatMap { histories: List<History> ->
                    if (histories.size > numberOfThreadsToKeep) {
                        val prunedHistories: MutableList<History> = ArrayList(histories.size - numberOfThreadsToKeep)
                        var i = numberOfThreadsToKeep
                        while (i < histories.size) {
                            prunedHistories.add(histories[i])
                            i++
                        }
                        return@flatMap Single.just<List<History>>(prunedHistories)
                    } else {
                        return@flatMap Single.just<List<History>>(emptyList())
                    }
                }
                .flatMap { histories: List<History> ->
                    val threads: MutableList<Long> = ArrayList(histories.size)
                    for (history in histories) {
                        threads.add(history.threadId)
                    }
                    Single.just(threads)
                }
                .flatMap { threadList: List<Long> ->
                    if (threadList.isEmpty()) {
                        return@flatMap Single.just(false)
                    }
                    Single.zip(PostTableConnection.removeThreads(threadList), removeThreads(threadList),
                            BiFunction { aBoolean: Boolean, aBoolean2: Boolean -> aBoolean && aBoolean2 })
                }
    }

    @JvmStatic
    fun putHistory(boardName: String, threadId: Long, firstPost: ChanPost, postCount: Int): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            if (firstPost.no <= 0 || firstPost.no != threadId) {
                return@singleOnIo false
            }
            Log.d(LOG_TAG, "putHistory: name=" + boardName + ", thread=" + firstPost.no + ", current watched=" + firstPost.isWatched)
            val history = History()
            history.boardName = boardName
            history.threadId = threadId
            history.userName = firstPost.name ?: ""
            history.tim = firstPost.tim ?: ""
            history.threadSize = postCount
            history.lastReadPosition = 0
            history.watched = false
            history.orderId = 0
            history.unreadCount = 0
            val text: String = if (!TextUtils.isEmpty(firstPost.subject)) {
                firstPost.subject.toString()
            } else if (!TextUtils.isEmpty(firstPost.com)) {
                firstPost.com.toString()
            } else {
                ""
            }
            history.text = text
            history.lastAccess = System.currentTimeMillis()
            val historyDao = getInstance()?.history() ?: return@singleOnIo false
            historyDao.insert(history) > 0
        }
    }

    @JvmStatic
    fun removeHistory(boardName: String, threadId: Long): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val historyDao = getInstance()?.history() ?: return@singleOnIo false
            historyDao.removeThread(boardName, threadId)
            true
        }
    }

    @JvmStatic
    fun removeAllHistory(watched: Boolean): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val historyDao = getInstance()?.history() ?: return@singleOnIo false
            historyDao.clear()
            true
        }
                .flatMap { PostTableConnection.removeAllThreads() }
                .flatMap { removeAllThreads() }
                .onErrorReturn { throwable: Throwable ->
                    Log.e(LOG_TAG, "Error deleting all history", throwable)
                    false
                }
    }

    @JvmStatic
    fun updateHistoryOrder(historyList: List<History>): Single<Boolean> {
        if (historyList.isEmpty()) {
            Log.e(LOG_TAG, "Cannot update history order: history list is blank")
            return Single.just(false)
        }
        return Flowable.defer { Flowable.just(historyList) }
                .flatMapIterable { histories: List<History> ->
                    for (i in histories.indices) {
                        histories[i].orderId = i
                    }
                    histories
                }
                .flatMap(Function { history: History -> setOrder(history.boardName, history.threadId, history.orderId).toFlowable() } as Function<History, Flowable<Boolean>>)
                .onErrorReturn { throwable: Throwable? ->
                    Log.e(LOG_TAG, "Error updating history order: size=" + historyList.size, throwable)
                    false
                }
                .toList()
                .map { booleans: List<Boolean> ->
                    var success = true
                    for (b in booleans) {
                        if (success) {
                            success = b
                        }
                    }
                    success
                }
    }

    @JvmStatic
    fun updateHistory(history: History): Single<Boolean> {
        return if (history.threadId == 0L) {
            Single.just(false)
        } else DatabaseUtils.singleOnIo { (getInstance()?.history()?.update(history) ?: 0) > 0 }
    }

    @JvmStatic
    fun setOrder(boardName: String, threadId: Long, order: Int): Single<Boolean> {
        return getInstance()?.history()?.order(boardName, threadId, order)?.map { value: Int -> value > 0 }
                ?: Single.just(false)
    }

    @JvmStatic
    fun pruneHistory(days: Int): Single<Boolean> {
        val oldestHistoryTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return DatabaseUtils.singleOnIo {
            val historyDao = getInstance()?.history() ?: return@singleOnIo false
            historyDao.prune(oldestHistoryTime, false)
            true
        }
    }

    @JvmStatic
    fun getHistoryToPrune(days: Int): Single<List<History>> {
        val oldestHistoryTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
        return getInstance()?.history()?.getOldHistory(oldestHistoryTime, false)
                ?.onErrorReturn { throwable: Throwable? ->
                    Log.e(LOG_TAG, "Error deleting history from $days day(s) ago", throwable)
                    emptyList()
                } ?: Single.just(emptyList())
    }

    @JvmStatic
    fun setLastReadPos(boardName: String, threadId: Long, pos: Int): Single<Boolean> {
        return getInstance()?.history()?.lastReadPos(boardName, threadId, pos)?.map { value: Int -> value > 0 }
                ?.onErrorReturn { throwable: Throwable? ->
                    Log.e(LOG_TAG, "Failed to set the last read position to $pos", throwable)
                    false
                } ?: Single.just(false)
    }

    @JvmStatic
    fun setUnreadCount(boardName: String, threadId: Long, unread: Int): Single<Boolean> {
        return getInstance()?.history()?.unreadCount(boardName, threadId, unread)?.map { value: Int -> value > 0 }
                ?.onErrorReturn { throwable: Throwable? ->
                    Log.e(LOG_TAG, "Failed to set the unread count to $unread", throwable)
                    false
                } ?: Single.just(false)
    }

//    @JvmStatic
//    fun nextRefresh(bookmarksOnly: Boolean): Single<History> {
//        return if (bookmarksOnly) {
//            getInstance()?.history()?.oldestActiveBookmark() ?: Single.just(History())
//        } else {
//            getInstance()?.history()?.oldestActiveHistory() ?: Single.just(History())
//        }
////        return getInstance()?.history()?.oldestActiveHistory() ?: Single.just(History())
//    }

    @JvmStatic
    private fun validateHistoryDeleted(): Function<List<History>, Single<Boolean>> {
        return Function { histories: List<History> -> Single.just(histories.isEmpty()) }
    }
}
