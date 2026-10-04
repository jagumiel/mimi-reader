package com.emogoth.android.phone.mimi.autorefresh

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.room.EmptyResultSetException
import androidx.work.*
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.db.DatabaseUtils
import com.emogoth.android.phone.mimi.db.HistoryTableConnection
import com.emogoth.android.phone.mimi.db.RefreshQueueTableConnection
import com.emogoth.android.phone.mimi.db.models.QueueItem
import com.emogoth.android.phone.mimi.util.MimiPrefs
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit


class RefreshScheduler2 {

    fun foreground() {
        WorkManager.getInstance(MimiApplication.instance.applicationContext).cancelAllWorkByTag(REFRESH_WORKER_TAG)
        startRequest(false)
    }

    fun background() {
        WorkManager.getInstance(MimiApplication.instance.applicationContext).cancelAllWorkByTag(REFRESH_WORKER_TAG)
        RefreshQueueTableConnection.removeUnwatched()
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe(object : SingleObserver<Int> {
                    override fun onSuccess(t: Int) {
                        startRequest(true)
                    }

                    override fun onSubscribe(d: Disposable) {
                        // no op
                    }

                    override fun onError(e: Throwable) {
                        Log.e(LOG_TAG, "Error removing unwatched threads from the refresh queue", e)
                    }
                })

    }

    private fun createRequest(boardName: String, threadId: Long, threadSize: Int, delay: Long, background: Boolean): OneTimeWorkRequest {
        val inputData = workDataOf(BOARD_NAME to boardName, THREAD_ID to threadId, THREAD_SIZE to threadSize, BACKGROUND to background)
        return OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(inputData)
                .addTag(REFRESH_WORKER_TAG)
                .setBackoffCriteria(BackoffPolicy.LINEAR, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
    }

    fun startRequest(background: Boolean) {
        val maxDelay = (if (background) MimiPrefs.backgroundRefreshInterval() else MimiPrefs.refreshInterval()) * 1000L
        if (maxDelay == 0L) {
            return
        }

        Log.d(LOG_TAG, "startRequest() called")
        val sub = RefreshQueueTableConnection.nextItem()
                .defaultIfEmpty(QueueItem())
                .toSingle()
                .compose(DatabaseUtils.applySingleSchedulers())
                .subscribe({
                    if (it.threadId > 0) {
                        if (LOG_DEBUG) Log.d(LOG_TAG, "Starting request")
                        val diff = System.currentTimeMillis() - it.lastRefresh
                        val delay = maxDelay - diff
                        if (LOG_DEBUG) Log.d(LOG_TAG, "Delay for next refresh cycle is $diff ms")
                        val request = createRequest(it.boardName, it.threadId, it.threadSize, if (delay > 0) delay else maxDelay, background)
                        val workManager = WorkManager.getInstance(MimiApplication.instance.applicationContext)
                        val res = workManager.enqueue(request)
                    } else {
                        if (LOG_DEBUG) Log.d(LOG_TAG, "Not starting request")
                    }
                }, {
                    Log.e(LOG_TAG, "Error getting next item to refresh", it)
                })
    }

    companion object {
        private val LOG_DEBUG = true

        //        private val LOG_DEBUG = BuildConfig.DEBUG
        private const val LOG_TAG = "RefreshScheduler(2)"

        const val REFRESH_WORKER_TAG = "refresh_worker_tag"
        const val BOARD_NAME = "board_name"
        const val THREAD_ID = "thread_id"
        const val THREAD_SIZE = "thread_size"
        const val BACKGROUND = "background"

        val instance = RefreshScheduler2()

        @JvmStatic
        fun isActive(): Boolean {
            val instance = WorkManager.getInstance(MimiApplication.instance.applicationContext)
            val statuses = instance.getWorkInfosByTag(REFRESH_WORKER_TAG)
            return try {
                var running = false
                val workInfoList = statuses.get()
                for (workInfo in workInfoList) {
                    val state = workInfo.state
                    running = running || state == WorkInfo.State.RUNNING || state == WorkInfo.State.ENQUEUED
                }
                running
            } catch (e: ExecutionException) {
                Log.e(LOG_TAG, "Execution exception caught while checking if WorkManager is active", e)
                Log.e(LOG_TAG, "Caught exception", e)
                false
            } catch (e: InterruptedException) {
                Log.e(LOG_TAG, "WorkManager was interrupted while checking if active", e)
                Log.e(LOG_TAG, "Caught exception", e)
                false
            }
        }

        @JvmStatic
        fun addThread(boardName: String, threadId: Long) {
            val obs = RefreshQueueTableConnection.fetchItem(boardName, threadId)
                    .onErrorReturn { if (it is EmptyResultSetException) QueueItem() else throw it }
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                    .flatMap {
                        if (it.queueId >= 0) {
//                            RefreshQueueTableConnection.addItem(it.historyId, it.oldThreadSize, it.oldUnread, it.lastRefresh, it.queueId)
                            Single.just(it)
                        } else {
                            HistoryTableConnection.fetchPost(boardName, threadId).flatMap {
                                Single.defer {
                                    val historyId = it.id
                                    if (historyId == null) {
                                        Single.just(QueueItem)
                                    } else {
                                        Single.defer {
                                            try {
                                                RefreshQueueTableConnection.addItem(historyId, it.threadSize, 0, it.lastAccess)
                                            } catch (e: Exception) {
                                                Log.e(LOG_TAG, "Error adding item to refresh queue for /$boardName/$threadId in the refresh scheduler\nhistory id = $historyId, thread size = ${it.threadSize}, last access = ${it.lastAccess}", e)
                                            }
                                            Single.just(0)
                                        }
                                                .flatMap {
                                                    RefreshQueueTableConnection.fetchItem(boardName, threadId)
                                                            .onErrorReturn { err ->
                                                                Log.e(LOG_TAG, "Error getting refresh queue for /$boardName/$threadId after adding it", err)
                                                                QueueItem()
                                                            }
                                                }
                                    }
                                }

                            }
                        }
                    }

            if (isActive()) {
                obs.subscribe()
            } else {
                val sub = obs.subscribe({
                    val item = it as QueueItem
                    if (item.queueId >= 0) {
                        val scheduler = RefreshScheduler2()
                        scheduler.foreground()
                    }
                }, {
                    Log.e(LOG_TAG, "Error getting refresh queue for /$boardName/$threadId", it)
                })
            }
        }

        @JvmStatic
        fun removeThread(boardName: String, threadId: Long) {
            val obs = RefreshQueueTableConnection.removeItem(boardName, threadId)
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
            if (isActive()) {
                obs.subscribe()
            } else {
                val sub = obs.subscribe({ result ->
                    if (result > 0) {
                        val scheduler = RefreshScheduler2()
                        scheduler.foreground()
                    }
                }, {
                    Log.e(LOG_TAG, "Error removing thread from the refresh scheduler", it)
                    Log.e(LOG_TAG, "Caught exception", it)
                })
            }
        }

    }
}

fun <T> LiveData<T>.observeOnce(observer: Observer<T>) {
    observeForever(object : Observer<T> {
        override fun onChanged(t: T) {
            observer.onChanged(t)
            removeObserver(this)
        }
    })
}
