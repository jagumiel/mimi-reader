package com.emogoth.android.phone.mimi.db

import android.util.Log
import com.emogoth.android.phone.mimi.db.MimiDatabase.Companion.getInstance
import com.emogoth.android.phone.mimi.db.models.HiddenThread
import io.reactivex.Single
import java.util.concurrent.TimeUnit

object HiddenThreadTableConnection {
    val LOG_TAG: String = HiddenThreadTableConnection::class.java.simpleName

    @JvmStatic
    fun fetchHiddenThreads(boardName: String): Single<List<HiddenThread>> {
        return getInstance()?.hiddenThreads()?.getHiddenThreadsForBoard(boardName)
                ?.onErrorReturn { throwable: Throwable? ->
                    Log.e(LOG_TAG, "Error loading hidden threads from the database", throwable)
                    emptyList()
                } ?: Single.just(emptyList())
    }

    @JvmStatic
    fun hideThread(boardName: String, threadId: Long, sticky: Boolean): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val hiddenThread = HiddenThread()
            hiddenThread.boardName = boardName
            hiddenThread.threadId = threadId
            hiddenThread.time = System.currentTimeMillis()
            hiddenThread.sticky = (if (sticky) 1 else 0)
            val hiddenThreadDao = getInstance()?.hiddenThreads() ?: return@singleOnIo false
            hiddenThreadDao.upsert(hiddenThread)
            true
        }
    }

    @JvmStatic
    fun clearAll(): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val hiddenThreadDao = getInstance()?.hiddenThreads() ?: return@singleOnIo false
            hiddenThreadDao.clear()
            true
        }
    }

    @JvmStatic
    fun prune(days: Int): Single<Boolean> {
        return DatabaseUtils.singleOnIo {
            val oldestTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong())
            val hiddenThreadDao = getInstance()?.hiddenThreads() ?: return@singleOnIo false
            hiddenThreadDao.prune(oldestTime)
            true
        }
    }
}
