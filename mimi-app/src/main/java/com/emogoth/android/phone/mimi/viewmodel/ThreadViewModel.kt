package com.emogoth.android.phone.mimi.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import com.emogoth.android.phone.mimi.db.HistoryTableConnection
import com.emogoth.android.phone.mimi.db.models.History
import com.emogoth.android.phone.mimi.util.MimiPrefs
import com.mimireader.chanlib.models.ChanThread
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import java.util.*

class ThreadViewModel(val boardName: String, val threadId: Long) : ViewModel() {
    private val TAG = "ThreadViewModel"
    private val dataSource = ChanDataSource()

    private val thread: ChanThread = ChanThread(boardName, threadId, Collections.emptyList())
    private var unread = 0
    private var lastReadPosition = -1
    private var bookmarked = false

    var firstFetchComplete = false
    var userPosts = dataSource.userPosts

    fun watchThread(): Flowable<ChanThread> {
        return Flowable.combineLatest(
                dataSource.watchThread(boardName, threadId),
                HistoryTableConnection.observeThread(boardName, threadId)
                        .startWith(History()),
                BiFunction<ChanThread, History, ChanThread> { updatedThread, history ->
                    lastReadPosition = history.lastReadPosition
                    bookmarked = history.watched
                    if (this.thread.posts.size < updatedThread.posts.size) {
                        this.thread.posts.clear()
                        this.thread.posts.addAll(updatedThread.posts)
                    }
                    unread = (this.thread.posts.size - lastReadPosition).coerceAtLeast(0)

                    updatedThread
                })
    }

    fun fetchThread(force: Boolean = true): Single<ChanThread> {
        val size = if (force) 0 else thread.posts.size
        return dataSource.fetchThread(boardName, threadId, size)
    }

    fun setLastReadPosition(pos: Int, visibleItems: Int): Single<Boolean> {
        if (pos > this.lastReadPosition && pos > 0) {
            this.lastReadPosition = pos
            this.unread = thread.posts.size - pos - visibleItems
            Log.e(TAG, "Unread: ${this.unread}, Last Read Position: ${this.lastReadPosition}")
            return dataSource.updateHistoryLastRead(boardName, threadId, pos, unread)
        }

        return Single.just(false)
    }

    fun setBookmarked(bookmarked: Boolean): Single<Boolean> {
        this.bookmarked = bookmarked
        return dataSource.updateHistoryBookmark(boardName, threadId, bookmarked)
    }

    fun unread(): Int {
        return this.unread
    }

    fun bookmarked(): Boolean {
        return this.bookmarked
    }

    fun lastReadPos(): Int {
        return this.lastReadPosition
    }

    override fun onCleared() {
        super.onCleared()
        thread.posts.clear()
        thread.threadId = 0
        thread.boardName = ""
    }
}
