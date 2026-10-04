package com.emogoth.android.phone.mimi.view.gallery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.util.DownloadItem
import com.emogoth.android.phone.mimi.util.DownloadListener
import com.emogoth.android.phone.mimi.util.MimiPrefs
import com.emogoth.android.phone.mimi.viewmodel.GalleryItem
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import java.io.File

@SuppressLint("ViewConstructor") // This custom view should not be instantiated in xml
abstract class GalleryPage(context: Context, private val viewModel: GalleryViewModel) : FrameLayout(context), DownloadListener {
    companion object {
        val LOG_TAG: String = GalleryPage::class.java.simpleName
        const val MAX_RETRIES = 5
    }

    var postId: Long = -1

    protected var downloadComplete = false
    protected var pageSelected = false
    protected var loaded = false

    private var childView: View? = null
    private var progressView: ProgressBar? = null

    private var progressViewID = View.generateViewId()
    private var progress: Int = 0
        set(value) {
            field = value
            if (value in 0..100) {
                progressView?.progress = field
            }
        }

    private var retryCount = 0

    init {
        id = View.generateViewId()

        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        params.gravity = Gravity.CENTER
        layoutParams = params

        val v = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal)
        val progressParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        progressParams.gravity = Gravity.CENTER

        v.id = progressViewID
        v.layoutParams = progressParams
        v.max = 100
        v.setPadding(10, 0, 10, 0)

        progressView = v
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (findViewById<ProgressBar>(progressViewID) == null) {
            addView(progressView)
        }

        if (downloadComplete && !loaded) {
            onComplete()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        loaded = false
    }



    protected var downloadItem = DownloadItem.empty()

    val localFile: File
        get() {
            return downloadItem.file
        }

    fun bind(item: GalleryItem) {
        onViewBind()
        if (item != GalleryItem.empty()) {
            postId = item.id
            downloadComplete = false
            downloadItem = viewModel.addDownloadListener(item.id, this)
            progressView?.progress = 0
            progressView?.visibility = View.VISIBLE
            childView?.visibility = View.INVISIBLE
        } else {
            downloadComplete = true
            progressView?.visibility = View.GONE
            childView?.visibility = View.VISIBLE
        }
    }

    protected fun addMainChildView(child: View) {
        if (progressView != null) {
            val v = progressView
            v?.visibility = View.GONE
        }

        val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        params.gravity = Gravity.CENTER
        child.layoutParams = params

        child.setOnClickListener {
            if (context is Activity && MimiPrefs.closeGalleryOnClick(context)) {
                val activity: Activity = context as Activity
                activity.finish()
            }
        }

        addView(child)

        childView = child
    }

    protected fun retryDownload(): Boolean {
        if (retryCount < MAX_RETRIES) {
            retryCount++
            viewModel.retryDownload(downloadItem)
            return true
        }

        return false
    }

    override fun onBytesReceived(progress: Int) {
        this.progress = progress
    }

    override fun onComplete() {
        if (BuildConfig.DEBUG) {
            Log.d("GalleryPage", "onComplete called: ${downloadItem.file.absolutePath}")
        }

        if (!downloadItem.file.exists()) {
            Log.w("GalleryPage", "onComplete called, but file does not exist: ${downloadItem.file.absolutePath}")
            Log.w("ImagePage", "onComplete called, but file does not exist: \n" +
                    "${downloadItem.file.absolutePath} \n" +
                    "${downloadItem.url} \n" +
                    "${downloadItem.thumbUrl} \n" +
                    "${downloadItem.id}")

            if (retryCount < MAX_RETRIES) {
                retryCount++
                viewModel.retryDownload(downloadItem)
            }
        }

        downloadComplete = true

        progressView?.visibility = View.GONE
        childView?.visibility = View.VISIBLE
    }

    override fun onError(t: Throwable) {
        downloadComplete = true
        Log.e(LOG_TAG, "Error downloading file", t)
    }

    abstract fun onViewBind()

    open fun onPageSelectedChange(selected: Boolean) {
        pageSelected = selected

        if (downloadComplete) {
            progressView?.visibility = View.GONE
            childView?.visibility = View.VISIBLE
        } else {
            progressView?.visibility = View.VISIBLE
            childView?.visibility = View.INVISIBLE
        }
    }

    open fun onHostPause() {
        // Only media-backed pages need to react.
    }

    open fun onHostResume() {
        // Only media-backed pages need to react.
    }

    open fun fullScreen(enabled: Boolean = true) {

    }
}
