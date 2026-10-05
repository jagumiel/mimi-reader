package com.emogoth.android.phone.mimi.util

import android.content.Context
import android.util.Log
import com.emogoth.android.phone.mimi.BuildConfig
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.FlowableSubscriber
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import okhttp3.*
import okio.buffer
import okio.sink
import org.reactivestreams.Subscription
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs


/**
 * A queued download manager that keeps a strict concurrency limit and prioritizes files near the
 * currently visible gallery item.
 *
 *
 * @param concurrentDownloads the number of downloads to start
 * @property client the okhttp client to use for the downloads
 * @property downloadItems the list of items to download
 * @property app (optional) app context to determine if wifi is connected. If the context is non-null, wifi connectivity is used
 * @constructor Creates an empty group.
 */
class DownloadManager(
    private val client: OkHttpClient,
    private val downloadItems: List<DownloadItem>,
    concurrentDownloads: Int,
    private val app: Context? = null,
    initialPosition: Int = 0
) {
    companion object {
        val TAG = DownloadManager::class.java.simpleName
        const val BUFFER_SIZE = 1024L
    }

    private val stateLock = Any()
    private val itemById = downloadItems.associateBy { it.id }
    private val queue = GalleryDownloadQueue(downloadItems, concurrentDownloads, initialPosition)
    private val subscriberMap = ConcurrentHashMap<Long, Subscription>()
    private val callbackMap = ConcurrentHashMap<Long, DownloadListener>()
    @Volatile
    private var destroyed = false

    fun start() {
        drainQueue()
    }

    fun prioritize(position: Int) {
        queue.prioritize(position)
        drainQueue()
    }

    fun cancel(id: Long) {
        val subscription = synchronized(stateLock) {
            queue.remove(id)
            subscriberMap.remove(id)
        }
        subscription?.cancel()
        drainQueue()
    }

    fun addListener(id: Long, listener: DownloadListener): DownloadItem {
        val item = itemById[id] ?: return DownloadItem.empty()
        synchronized(stateLock) {
            if (destroyed) {
                return DownloadItem.empty()
            }
            callbackMap[item.id] = listener
            if (item.file.exists() && item.file.length() == 0L) {
                item.file.delete()
            }
            queue.ensurePending(item)
        }

        // Existing files still pass through the scheduled downloader so the completion callback
        // cannot run before GalleryPage.bind() has stored the returned DownloadItem.
        drainQueue()
        return item
    }

    fun removeListener(id: Long) {
        callbackMap.remove(id)
    }

    fun retry(item: DownloadItem) {
        val subscription = synchronized(stateLock) {
            if (destroyed) {
                return
            }
            queue.requeue(item)
            subscriberMap.remove(item.id)
        }
        subscription?.cancel()
        drainQueue()
    }

    private fun preloadEnabled(): Boolean {
        return app == null || MimiPrefs.preloadEnabled(app)
    }

    private fun drainQueue() {
        val nextItems = synchronized(stateLock) {
            if (destroyed) {
                return
            }
            queue.takeAvailable(preloadEnabled(), callbackMap.keys)
        }
        for (item in nextItems) {
            createSubscriber(item)
        }
    }

    private fun createSubscriber(item: DownloadItem) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Starting prioritized download for ${item.url}")
        }

        try {
            downloadToFile(client, item.url, item.file)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(object : FlowableSubscriber<Int> {
                        private var subscription: Subscription? = null

                        override fun onComplete() {
                            finishDownload(item, subscription, null)
                        }

                        override fun onSubscribe(s: Subscription) {
                            subscription = s
                            val cancel = synchronized(stateLock) {
                                if (destroyed || !queue.isActive(item.id)) {
                                    true
                                } else {
                                    subscriberMap[item.id] = s
                                    false
                                }
                            }
                            if (cancel) {
                                s.cancel()
                            } else {
                                s.request(Long.MAX_VALUE)
                            }
                        }

                        override fun onNext(progress: Int?) {
                            callbackMap[item.id]?.onBytesReceived(progress ?: 0)
                        }

                        override fun onError(error: Throwable?) {
                            finishDownload(
                                    item,
                                    subscription,
                                    error ?: Exception("Unknown download error")
                            )
                        }
                    })
        } catch (error: Throwable) {
            finishDownload(item, null, error)
        }
    }

    private fun finishDownload(item: DownloadItem, subscription: Subscription?, error: Throwable?) {
        val listener = synchronized(stateLock) {
            val currentSubscription = subscriberMap[item.id]
            if (subscription != null && currentSubscription !== subscription) {
                return
            }
            subscriberMap.remove(item.id)
            queue.finished(item.id)
            callbackMap[item.id]
        }

        if (error == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Finished download for ${item.url}")
            }
            listener?.onComplete()
        } else {
            if (item.file.exists()) {
                item.file.delete()
            }
            listener?.onError(error)
        }
        drainQueue()
    }

    fun clear() {
        val subscriptions = synchronized(stateLock) {
            val current = subscriberMap.toMap()
            subscriberMap.clear()
            for (id in current.keys) {
                itemById[id]?.let { queue.requeue(it) }
            }
            current.values
        }
        subscriptions.forEach { it.cancel() }
    }

    fun destroy() {
        val subscriptions = synchronized(stateLock) {
            destroyed = true
            queue.clear()
            callbackMap.clear()
            val current = subscriberMap.values.toList()
            subscriberMap.clear()
            current
        }
        subscriptions.forEach { it.cancel() }
    }
}

internal class GalleryDownloadQueue(
    downloadItems: List<DownloadItem>,
    concurrentDownloads: Int,
    initialPosition: Int = 0
) {
    private val maxConcurrent = concurrentDownloads.coerceAtLeast(0)
    private val positions = downloadItems.mapIndexed { index, item -> item.id to index }.toMap()
    private val pending = ArrayList(downloadItems.distinctBy { it.id })
    private val active = HashSet<Long>()
    private var focusPosition = normalizedPosition(initialPosition)

    init {
        reorderPending()
    }

    @Synchronized
    fun prioritize(position: Int) {
        focusPosition = normalizedPosition(position)
        reorderPending()
    }

    @Synchronized
    fun takeAvailable(preloadEnabled: Boolean, requestedIds: Set<Long>): List<DownloadItem> {
        val availableSlots = (maxConcurrent - active.size).coerceAtLeast(0)
        if (availableSlots == 0) {
            return emptyList()
        }

        val selected = pending
                .asSequence()
                .filter { preloadEnabled || requestedIds.contains(it.id) }
                .take(availableSlots)
                .toList()
        if (selected.isEmpty()) {
            return emptyList()
        }

        val selectedIds = selected.mapTo(HashSet()) { it.id }
        pending.removeAll { selectedIds.contains(it.id) }
        active.addAll(selectedIds)
        return selected
    }

    @Synchronized
    fun ensurePending(item: DownloadItem) {
        if (!active.contains(item.id) && pending.none { it.id == item.id }) {
            pending.add(item)
            reorderPending()
        }
    }

    @Synchronized
    fun requeue(item: DownloadItem) {
        active.remove(item.id)
        pending.removeAll { it.id == item.id }
        pending.add(item)
        reorderPending()
    }

    @Synchronized
    fun finished(id: Long) {
        active.remove(id)
    }

    @Synchronized
    fun remove(id: Long) {
        active.remove(id)
        pending.removeAll { it.id == id }
    }

    @Synchronized
    fun isActive(id: Long): Boolean = active.contains(id)

    @Synchronized
    fun clear() {
        active.clear()
        pending.clear()
    }

    private fun normalizedPosition(position: Int): Int {
        if (positions.isEmpty()) {
            return 0
        }
        return position.coerceIn(0, positions.size - 1)
    }

    private fun reorderPending() {
        pending.sortWith(
                compareBy<DownloadItem> {
                    abs((positions[it.id] ?: Int.MAX_VALUE) - focusPosition)
                }.thenBy {
                    val position = positions[it.id] ?: Int.MAX_VALUE
                    if (position >= focusPosition) 0 else 1
                }.thenBy { positions[it.id] ?: Int.MAX_VALUE }
        )
    }
}

fun downloadToFile(client: OkHttpClient, url: String, file: File?): Flowable<Int> {
    if (BuildConfig.DEBUG) {
        Log.d(DownloadManager.TAG, "Downloading file: ${file?.absolutePath}")
    }
    return Flowable.create({ emitter ->
        if (file != null && file.exists() && file.length() > 0L) {
            if (BuildConfig.DEBUG) {
                Log.d(DownloadManager.TAG, "File exists: ${file.absolutePath}; manually calling onComplete()")
            }
            emitter.onComplete()
            return@create
        }
        if (file != null && file.exists()) {
            file.delete()
        }

        if (BuildConfig.DEBUG) {
            Log.d(DownloadManager.TAG, "Starting file download: ${file?.absolutePath}")
        }

        val req = Request.Builder().url(url).get().build()
        val call = client.newCall(req)
        emitter.setCancellable { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                emitter.tryOnError(e)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body
                    if (body == null) {
                        emitter.tryOnError(IllegalStateException("Response from $url returned an empty body"))
                        return
                    }

                    val contentLength = body.contentLength()
                    val source = body.source()
                    val sink = if (file != null) {
                        file.createNewFile()
                        file.sink().buffer()
                    } else null
                    val sinkBuffer = sink?.buffer

                    if (sinkBuffer == null) {
                        emitter.tryOnError(IllegalStateException("Could not write to file; File object is null"))
                        return
                    }

                    var totalBytes = 0L
                    while (!emitter.isCancelled) {
                        val count = source.read(sinkBuffer, DownloadManager.BUFFER_SIZE)
                        if (count == -1L) break
                        sink.emit()

                        totalBytes += count
                        val progress = ((totalBytes.toFloat() / contentLength) * 100).toInt()
                        emitter.onNext(progress)
                    }

                    sink.flush()
                    sink.close()

                    source.close()

                    if (emitter.isCancelled) {
                        file?.delete()
                    }

                    if (totalBytes <= 0L) {
                        val fileException = if (file == null) Exception("Could not write to null file") else NoSuchFileException(file, null, "Wrote a 0-byte file")
                        Log.e(DownloadManager.TAG, "Error writing file", fileException)

                        emitter.tryOnError(fileException)
                    } else {
                        emitter.onComplete()
                    }
                } catch (e: Exception) {
                    emitter.tryOnError(e)
                } finally {
                    response.close()
                }
            }
        })
    }, BackpressureStrategy.BUFFER)
}

data class DownloadItem(val id: Long, val url: String, val thumbUrl: String, val width: Int, val height: Int, val file: File, val saveFileName: String) {
    companion object {
        fun empty(): DownloadItem {
            return DownloadItem(-1, "", "", 0, 0, File(""), "")
        }
    }
}

interface DownloadListener {
    fun onBytesReceived(progress: Int)
    fun onComplete()
    fun onError(t: Throwable)
}
