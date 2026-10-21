package com.emogoth.android.phone.mimi.view.gallery

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import com.google.android.material.snackbar.Snackbar

import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

@SuppressLint("ViewConstructor")
open class GifPage(context: Context, viewModel: GalleryViewModel) : GalleryPage(context, viewModel) {
    private var released = false
    private var hostResumed = (context as? LifecycleOwner)?.lifecycle?.currentState
        ?.isAtLeast(Lifecycle.State.RESUMED) ?: true
    override fun onViewBind() {
        (view.drawable as? GifDrawable)?.recycle()
        view.setImageDrawable(null)
    }

    val view = GifImageView(context)
    init {
        view.setFreezesAnimation(true)
        addMainChildView(view)
    }

    override fun onComplete() {
        super.onComplete()

        if (!isAttachedToWindow || released) {
            return
        }

        try {
            (view.drawable as? GifDrawable)?.recycle()
            val drawable = GifDrawable(downloadItem.file)
            drawable.addAnimationListener {
                if (pageSelected && hostResumed && view.drawable === drawable) {
                    notifyPlaybackCompleted()
                }
            }
            view.setImageDrawable(drawable)

            if (pageSelected && hostResumed) drawable.start() else drawable.stop()
            loaded = true
            notifyContentReady()
        } catch (e: Exception) {
            Snackbar.make(this, R.string.could_not_load_gif, Snackbar.LENGTH_SHORT).setAction(R.string.retry) { onComplete() }.show()
        }
    }

    override fun onPageSelectedChange(selected: Boolean) {
        super.onPageSelectedChange(selected)

        val drawable = view.drawable
        if (drawable != null && drawable is GifDrawable) {
            if (selected && hostResumed) drawable.start() else drawable.stop()
        }
    }

    override fun onHostPause() {
        hostResumed = false
        (view.drawable as? GifDrawable)?.stop()
    }

    override fun onHostResume() {
        hostResumed = true
        if (pageSelected) {
            (view.drawable as? GifDrawable)?.start()
        }
    }

    override fun onDetachedFromWindow() {
        (view.drawable as? GifDrawable)?.stop()
        super.onDetachedFromWindow()
    }

    fun release() {
        released = true
        onViewBind()
        loaded = false
    }
}
