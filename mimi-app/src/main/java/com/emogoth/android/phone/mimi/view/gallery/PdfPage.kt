package com.emogoth.android.phone.mimi.view.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import com.github.barteksc.pdfviewer.PDFView
import com.github.barteksc.pdfviewer.util.FitPolicy

@SuppressLint("ViewConstructor")
open class PdfPage(context: Context, private val model: GalleryViewModel) : GalleryPage(context, model) {
    val view = object : PDFView(context, null) {
        override fun onDetachedFromWindow() {
            rememberViewport() // Capture before PDFView's own detach recycles its document.
            super.onDetachedFromWindow()
        }
    }
    private var generation = 0
    private var loading = false
    private var failed = false
    private var released = false
    private var hostResumed = (context as? LifecycleOwner)?.lifecycle?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) ?: true
    private val retryButton = Button(context).apply { tag = "pdf-retry"; setText(R.string.retry) }
    private val errorView = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        visibility = View.GONE
        addView(TextView(context).apply {
            tag = "pdf-error-text"
            setText(R.string.pdf_load_failed)
            gravity = Gravity.CENTER
        })
        addView(retryButton)
    }

    init {
        addMainChildView(view, closeGalleryOnClick = false)
        addView(errorView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        retryButton.setOnClickListener {
            failed = false
            errorView.visibility = View.GONE
            if (!localFile.isFile && retryDownload()) return@setOnClickListener
            onComplete()
        }
    }

    override fun onViewBind() {
        stopRenderer()
        failed = false
        errorView.visibility = View.GONE
    }

    override fun onComplete() {
        super.onComplete()

        if (!isAttachedToWindow || !pageSelected || !hostResumed || released || loading || loaded || failed) return
        val attempt = ++generation
        val viewport = model.pdfViewport(postId)
        loading = true
        view.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        view.fromFile(downloadItem.file)
                .pageFitPolicy(FitPolicy.WIDTH)
                .enableDoubletap(true)
                .defaultPage(viewport.first)
                .onLoad { if (attempt == generation) view.zoomTo(viewport.second) }
                .onRender {
                    if (attempt == generation && isAttachedToWindow && !released) {
                        loading = false
                        loaded = true
                        notifyContentReady()
                    }
                }
                .onError { if (attempt == generation) showError(it) }
                .onPageError { _, error -> if (attempt == generation) showError(error) }
                .load()
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        showError(t)
    }

    override fun onPageSelectedChange(selected: Boolean) {
        super.onPageSelectedChange(selected)
        if (selected && downloadComplete) onComplete() else if (!selected) stopRenderer()
    }

    override fun onHostPause() {
        hostResumed = false
        stopRenderer()
    }

    override fun onHostResume() {
        hostResumed = true
        if (downloadComplete && pageSelected) onComplete()
    }

    override fun onDetachedFromWindow() {
        stopRenderer()
        super.onDetachedFromWindow()
    }

    fun release() {
        stopRenderer()
        released = true
    }

    private fun stopRenderer() {
        rememberViewport()
        ++generation // Ignore late callbacks after rebinding, pausing or detaching.
        loaded = false
        loading = false
        view.recycle()
    }

    private fun rememberViewport() {
        if (loaded && !view.isRecycled) model.savePdfViewport(postId, view.currentPage, view.zoom)
    }

    private fun showError(t: Throwable) {
        stopRenderer()
        failed = true
        view.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        Log.w(LOG_TAG, "PDF could not be rendered", t)
    }
}
