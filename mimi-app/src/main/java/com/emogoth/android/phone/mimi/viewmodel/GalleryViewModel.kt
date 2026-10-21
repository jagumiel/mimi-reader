package com.emogoth.android.phone.mimi.viewmodel

import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.emogoth.android.phone.mimi.activity.GalleryActivity2
import com.emogoth.android.phone.mimi.app.MimiApplication
import com.emogoth.android.phone.mimi.util.*
import com.mimireader.chanlib.models.ChanPost
import com.mimireader.chanlib.models.ChanThread
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import java.io.File

class GalleryViewModel(private val imageBaseUrl: String = "empty", audioEnabledByDefault: Boolean = false) : ViewModel() {
    companion object {
        val TAG: String = GalleryViewModel::class.java.simpleName
        const val DOWNLOAD_DIR = "full_images"
        private const val STATE_VIDEO_MUTED = "gallery_video_muted"
        private const val STATE_PDF_VIEWPORTS = "gallery_pdf_viewports"

        fun get(context: FragmentActivity, imageBaseUrl: String, audioLock: Boolean): GalleryViewModel {
            val factory = object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GalleryViewModel(imageBaseUrl, audioLock) as T
                }
            }

            return ViewModelProvider(context, factory)
                    .get(GalleryViewModel::class.java)
        }

        fun empty(): GalleryViewModel {
            return GalleryViewModel()
        }
    }

    var boardName: String = ""
    val audioState = GalleryAudioState(audioEnabledByDefault)
    private val pdfViewports = LinkedHashMap<Long, Pair<Int, Float>>()

    fun pdfViewport(id: Long): Pair<Int, Float> = pdfViewports[id] ?: (0 to 1f)

    fun savePdfViewport(id: Long, page: Int, zoom: Float) {
        if (id < 0) return
        pdfViewports[id] = page.coerceAtLeast(0) to if (zoom.isFinite()) zoom.coerceIn(1f, 3f) else 1f
        if (pdfViewports.size > 64) pdfViewports.remove(pdfViewports.keys.first())
    }
    var threadId: Long = -1
    var keepFiles = false
    var fullScreen: Boolean = false
    var position: Int = -1
    var postId: Long = -1
    var galleryType: Int = GalleryActivity2.GALLERY_TYPE_PAGER
    var postIds: LongArray = LongArray(0)
    var selectedItems: ArrayList<GalleryItem> = ArrayList()
        set(value) {
            field = value
            selectedItemIds = LongArray(value.size)

            for (i in 0 until value.size) {
                selectedItemIds[i] = value[i].id
            }
        }
    var saveLocation: String = ""

    var selectedItemIds: LongArray = LongArray(0)
    private var id: String = ""
    private val galleryItems: List<GalleryItem> = ArrayList()

    private val dataSource = GalleryDataSource()
    private val baseFilePath = File(MimiUtil.getInstance().cacheDir, "/$DOWNLOAD_DIR")
    private var downloadManager: DownloadManager? = null

    fun update(chanThread: ChanThread) {
        val posts = getPostsWithImages(chanThread.posts)
        update(posts)
    }

    fun update(items: List<GalleryItem>) {
        if (imageBaseUrl == "empty") {
            throw IllegalStateException("View model must be initialized before using the instance")
        }

        val newId = "$boardName$threadId"
        if (id == newId) {
            return
        }

        val downloadItems = ArrayList<DownloadItem>(items.size)
        if (!baseFilePath.exists()) {
            baseFilePath.mkdirs()
        }
        for (post in items) {
            downloadItems.add(
                    DownloadItem(
                            post.id,
                            post.downloadUrl,
                            post.thumbnailUrl,
                            post.width,
                            post.height,
                            File(MimiUtil.getInstance().cacheDir, "/$DOWNLOAD_DIR/${post.remoteFileName}"),
                            post.originalFileName,
                            post.size.toLong()
                    ))
        }

        if (galleryItems is ArrayList) {
            galleryItems.clear()
            galleryItems.addAll(items)
        }

        Log.d(TAG, "Starting gallery image download manager")
        val initialPosition = when {
            position in items.indices -> position
            else -> items.indexOfFirst { it.id == postId }.coerceAtLeast(0)
        }
        downloadManager = DownloadManager(
                HttpClientFactory.getInstance().downloadClient,
                downloadItems,
                2,
                MimiApplication.instance.applicationContext,
                initialPosition
        )
        id = newId
        downloadManager?.start()
    }

    fun addDownloadListener(id: Long, listener: DownloadListener): DownloadItem {
        return downloadManager?.addListener(id, listener) ?: DownloadItem.empty()
    }

    fun retryDownload(item: DownloadItem) {
        downloadManager?.retry(item)
    }

    fun prioritizeDownloads(position: Int) {
        downloadManager?.prioritize(position)
    }

    fun removeDownloadListener(id: Long) {
        downloadManager?.removeListener(id)
    }

    fun isEmpty(): Boolean {
        return imageBaseUrl == "empty"
    }

    // set state from bundle
    fun initState(state: Bundle) {
        boardName = state.getString(Extras.EXTRAS_BOARD_NAME, "") ?: ""
        threadId = state.getLong(Extras.EXTRAS_THREAD_ID, -1)

        selectedItemIds = state.getLongArray(GalleryActivity2.EXTRA_SELECTED_ITEMS)
                ?: LongArray(0)
        fullScreen = state.getBoolean(Extras.EXTRAS_FULL_SCREEN, false)
        postId = state.getLong(Extras.EXTRAS_POST_ID)
        galleryType = state.getInt(Extras.EXTRAS_GALLERY_TYPE)
        postIds = state.getLongArray(Extras.EXTRAS_POST_LIST) ?: LongArray(0)
        position = state.getInt(Extras.EXTRAS_POSITION, -1)
        saveLocation = state.getString(Extras.EXTRAS_FILE_PATH, "")
        if (state.containsKey(STATE_VIDEO_MUTED)) {
            audioState.setMuted(state.getBoolean(STATE_VIDEO_MUTED))
        }
        state.getBundle(STATE_PDF_VIEWPORTS)?.let { saved ->
            saved.keySet().take(64).forEach { key ->
                val value = saved.getFloatArray(key)
                val id = key.toLongOrNull()
                if (id != null && value?.size == 2) savePdfViewport(id, value[0].toInt(), value[1])
            }
        }
    }

    // put current state into a bundle
    fun saveState(state: Bundle) {
        state.putString(Extras.EXTRAS_BOARD_NAME, boardName)
        state.putLong(Extras.EXTRAS_THREAD_ID, threadId)
        state.putLong(Extras.EXTRAS_POST_ID, postId)
        state.putInt(Extras.EXTRAS_GALLERY_TYPE, galleryType)
        state.putLongArray(Extras.EXTRAS_POST_LIST, postIds)
        state.putBoolean(Extras.EXTRAS_FULL_SCREEN, fullScreen)
        state.putLongArray(GalleryActivity2.EXTRA_SELECTED_ITEMS, selectedItemIds)
        state.putInt(Extras.EXTRAS_POSITION, position)
        state.putString(Extras.EXTRAS_FILE_PATH, saveLocation)
        state.putBoolean(STATE_VIDEO_MUTED, audioState.muted)
        state.putBundle(STATE_PDF_VIEWPORTS, Bundle().apply {
            pdfViewports.forEach { (id, viewport) ->
                putFloatArray(id.toString(), floatArrayOf(viewport.first.toFloat(), viewport.second))
            }
        })
    }


    override fun onCleared() {
        super.onCleared()
        downloadManager?.destroy()

        if (galleryItems is ArrayList) {
            galleryItems.clear()
        }

        if (!keepFiles) {
            MimiUtil.deleteRecursive(baseFilePath, true)
        }
    }

    private fun getPostsWithImages(postList: List<ChanPost>): List<GalleryItem> {
        val posts = ArrayList<GalleryItem>()
        for (post in postList) {
            val item = convertPostToGalleryItem(post)
            if (item != GalleryItem.empty()) {
                posts.add(item)
            }
        }

        return posts
    }

    fun convertPostToGalleryItem(post: ChanPost): GalleryItem {
        return if (MediaContract.hasRemoteMedia(post)) {
            val tim: String = post.tim ?: ""
            val ext = MediaContract.normalizedExtension(post.ext)
            val thumb = MediaContract.thumbnailUrl(imageBaseUrl, boardName, post)
            val localFileName = "${tim}${ext}"
            val originalFileName = "${MediaContract.originalFilename(post)}${ext}"
            val img = MediaContract.mediaUrl(imageBaseUrl, boardName, post)
            GalleryItem(post.no, thumb, img, post.fsize.coerceAtLeast(0), post.width, post.height, boardName, ext, localFileName, originalFileName)
        } else {
            GalleryItem.empty()
        }
    }

    fun fetchThread(): Single<List<GalleryItem>> {
        return when (galleryItems.size) {
            0 -> dataSource.fetchThread(boardName, threadId, 0)
                    .doOnSuccess { update(it) }
                    .map { galleryItems }
            else -> Single.just(galleryItems).observeOn(Schedulers.io())
        }
                .doAfterSuccess {
                    for (selectedItemId in selectedItemIds) {
                        for (item in it) {
                            if (item.id == selectedItemId) {
                                selectedItems.add(item)
                            }
                        }
                    }
                }
    }

    fun fetchPosts(): Single<List<ChanPost>> {
        return dataSource.fetchThread(boardName, threadId)
                .observeOn(Schedulers.io())
                .map {
                    it.posts
                }
    }

    fun getGalleryItemsFromList(postIds: LongArray): Single<List<GalleryItem>> {
        return dataSource.postsWithImages(boardName, threadId, postIds)
                .observeOn(Schedulers.io())
                .flatMap {
                    val items = ArrayList<GalleryItem>(postIds.size)
                    for (chanPost in it) {
                        val item = convertPostToGalleryItem(chanPost)
                        if (item != GalleryItem.empty()) {
                            items.add(item)
                        }
                    }

                    Single.just(items as List<GalleryItem>)
                }
                .doAfterSuccess {
                    update(it)
                }
    }

    fun getGalleryItems(): List<GalleryItem> {
        return galleryItems
    }
}

data class GalleryItem(val id: Long, val thumbnailUrl: String, val downloadUrl: String, val size: Int, val width: Int, val height: Int, val boardName: String, val ext: String, val remoteFileName: String, val originalFileName: String) {
    companion object {
        fun empty(): GalleryItem {
            return GalleryItem(-1, "", "", 0, 0, 0, "", "", "", "")
        }
    }
}
