package com.emogoth.android.phone.mimi.view.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.emogoth.android.phone.mimi.BuildConfig
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel


@SuppressLint("ViewConstructor")
class ImagePage(context: Context, viewModel: GalleryViewModel) : GalleryPage(context, viewModel) {
    override fun onViewBind() {
//        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    val view = SubsamplingScaleImageView(context)
    init {
        view.setDebug(BuildConfig.DEBUG)
        addMainChildView(view)
    }

    override fun onComplete() {
        super.onComplete()
        if (!isAttachedToWindow) {
            return
        }

        try {
            view.setImage(ImageSource.uri(downloadItem.file.toUri()))
            loaded = true
            notifyContentReady()
        } catch (e: Exception) {
            if (!retryDownload()) {
                Log.w("ImagePage", "Ran out of retries; download failed for ${downloadItem.file.absolutePath}", e)
            }
        }
    }

    override fun onError(t: Throwable) {
        super.onError(t)

        view.visibility = View.INVISIBLE
    }
}
