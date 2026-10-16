package com.emogoth.android.phone.mimi.view.gallery

import android.annotation.SuppressLint
import android.content.Context
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import com.google.android.material.snackbar.Snackbar

import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

@SuppressLint("ViewConstructor")
class GifPage(context: Context, viewModel: GalleryViewModel) : GalleryPage(context, viewModel) {
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

        if (!isAttachedToWindow) {
            return
        }

        try {
            (view.drawable as? GifDrawable)?.recycle()
            val drawable = GifDrawable(downloadItem.file)
            drawable.addAnimationListener {
                if (pageSelected) {
                    notifyPlaybackCompleted()
                }
            }
            view.setImageDrawable(drawable)

            if (pageSelected) drawable.start() else drawable.stop()
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
            if (selected) drawable.start() else drawable.stop()
        }
    }

    override fun onHostPause() {
        (view.drawable as? GifDrawable)?.stop()
    }

    override fun onHostResume() {
        if (pageSelected) {
            (view.drawable as? GifDrawable)?.start()
        }
    }
}
