package com.emogoth.android.phone.mimi.view.gallery

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.net.toUri
import androidx.media3.common.PlaybackException
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.databinding.ViewGalleryVideoErrorBinding
import com.emogoth.android.phone.mimi.db.DatabaseUtils
import com.emogoth.android.phone.mimi.util.MediaPlayerHelper
import com.emogoth.android.phone.mimi.util.VideoPlaybackError
import com.emogoth.android.phone.mimi.util.GlideApp
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import io.reactivex.Flowable
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit

class VideoPage(context: Context, private val viewModel: GalleryViewModel, private val player: MediaPlayerHelper?) : GalleryPage(context, viewModel), MediaPlayerHelper.Listener {
    private val videoView = TextureView(context)
    private var controlView: VideoControls
    private var preview: AppCompatImageView
    private val errorBinding = ViewGalleryVideoErrorBinding.inflate(LayoutInflater.from(context), this, false)
    private var retryRequiresDownload = false
    private var forceReloadOnNextPlayback = false
    private var hostPaused = false

    init {
        addMainChildView(videoView)

        videoView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(LOG_TAG, "Surface texture changed (width: $width, height: $height)")
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                Log.d(LOG_TAG, "Surface texture updated")
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.d(LOG_TAG, "Surface texture destroyed")
                return true
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                Log.d(LOG_TAG, "Surface texture available")
            }
        }

        controlView = VideoControls(context)
        controlView.setAudioLock(!viewModel.audioState.muted, false)
        controlView.muteListener = { muted ->
            viewModel.audioState.setMuted(muted)
            controlView.setAudioLock(lock = !muted, fromUser = false)
            player?.mute(muted)
        }
        controlView.playListener = { paused ->
            if (paused) {
                player?.pause()
            } else {
                player?.start()
            }
        }
        controlView.scrubberListener = { value ->
            player?.seekTo(value)
        }
        addView(controlView)

        videoView.setOnClickListener {
            controlView.visibility = if (controlView.visibility == View.VISIBLE) View.INVISIBLE else View.VISIBLE
        }

        preview = AppCompatImageView(context)
        addView(preview)

        val errorParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        val errorMargin = (24 * resources.displayMetrics.density).toInt()
        errorParams.setMargins(errorMargin, 0, errorMargin, 0)
        addView(errorBinding.root, errorParams)
        errorBinding.videoRetryButton.setOnClickListener { retryPlayback() }
        errorBinding.videoExternalButton.setOnClickListener {
            if (!controlView.openExternally()) {
                Toast.makeText(context, R.string.video_no_external_player, Toast.LENGTH_SHORT).show()
            }
        }

    }

    override fun onComplete() {
        super.onComplete()
        if (!isAttachedToWindow) {
            return
        }

        controlView.videoLocation = downloadItem.file.absolutePath
        hidePlaybackError()
        updateVideoPlaybackState()
        loaded = true
    }

    override fun onPageSelectedChange(selected: Boolean) {
        super.onPageSelectedChange(selected)
        val muted = viewModel.audioState.muted
        controlView.setAudioLock(lock = !muted, fromUser = false)
        controlView.setMuted(muted = muted, fromUser = false)
        updateVideoPlaybackState()
    }

    private fun updateVideoPlaybackState() {
        if (downloadComplete && pageSelected && !hostPaused) {
            controlView.setAudioLock(!viewModel.audioState.muted, false)
            controlView.setMuted(viewModel.audioState.muted, false)

            showVideoView()
            scaleView(videoView, downloadItem.width, downloadItem.height)

            player?.addListener(this)
            player?.mute(controlView.isMuted())
            player?.playVideo(downloadItem.file.toUri(), videoView, forceReloadOnNextPlayback)
            forceReloadOnNextPlayback = false
        } else if (downloadComplete && !pageSelected) {
            GlideApp.with(preview).clear(preview)

            player?.removeListener(this)
            player?.detachVideo(videoView)
            videoView.keepScreenOn = false
            hidePlaybackError()
            showPreviewView()
        }
    }

    private fun retryPlayback() {
        hidePlaybackError()
        if (retryRequiresDownload) {
            forceReloadOnNextPlayback = true
            if (downloadItem.file.exists() && !downloadItem.file.delete()) {
                Log.w(LOG_TAG, "Could not remove invalid cached video: ${downloadItem.file.absolutePath}")
            }
            if (!retryDownload()) {
                showPlaybackError(R.string.video_download_error, false, true)
            }
        } else if (downloadItem.file.exists() && downloadItem.file.length() > 0L) {
            updateVideoPlaybackState()
        } else if (!retryDownload()) {
            showPlaybackError(R.string.video_download_error, false, true)
        }
    }

    private fun showPlaybackError(messageRes: Int, canOpenExternally: Boolean, requiresDownload: Boolean = false) {
        player?.pause()
        videoView.keepScreenOn = false
        showPreviewView()
        retryRequiresDownload = requiresDownload
        errorBinding.videoErrorMessage.setText(messageRes)
        errorBinding.videoExternalButton.visibility = if (canOpenExternally) View.VISIBLE else View.GONE
        errorBinding.root.visibility = View.VISIBLE
    }

    private fun hidePlaybackError() {
        errorBinding.root.visibility = View.GONE
    }

    private fun scaleView(view: View, videoWidth: Int, videoHeight: Int) {
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) {
            return
        }
        val ratio = scaleToFit(width, height, videoWidth, videoHeight)

        val params = view.layoutParams
        params.width = (videoWidth * ratio).toInt()
        params.height = (videoHeight * ratio).toInt()
        if (params is LayoutParams) {
            params.gravity = Gravity.CENTER
        }
        view.layoutParams = params
    }

    private fun scaleToFit(displayWidth: Int, displayHeight: Int, videoWidth: Int, videoHeight: Int): Float {
        val widthRatio = displayWidth.toFloat() / videoWidth.toFloat()
        val heightRatio = displayHeight.toFloat() / videoHeight.toFloat()
        return if (widthRatio < heightRatio) widthRatio else heightRatio
    }

    private fun showPreviewView(loadImage: Boolean = true) {
        videoView.visibility = View.INVISIBLE
        preview.visibility = View.VISIBLE

        if (loadImage) {
            try {
                preview.setImageDrawable(null)
                GlideApp.with(preview)
                        .load(downloadItem.thumbUrl)
                        .error(R.drawable.ic_content_picture)
                        .into(preview)
            } catch (e: IllegalStateException) {
                // no op
            }
        }
    }

    private fun showVideoView() {
        videoView.visibility = View.VISIBLE
        preview.visibility = View.GONE
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        controlView.setAudioLock(!viewModel.audioState.muted, false)
        startTimer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        videoTimerSubscription?.dispose()
        player?.removeListener(this)
        player?.detachVideo(videoView)
        videoView.keepScreenOn = false
        showPreviewView(loadImage = false)
    }

    private var videoTimerSubscription: Disposable? = null

    private fun startTimer() {
        videoTimerSubscription?.dispose()
        videoTimerSubscription = Flowable.interval(250, TimeUnit.MILLISECONDS).timeInterval()
                .compose(DatabaseUtils.applySchedulers())
                .subscribe({
                    if (pageSelected && player != null
                            && player.ownsVideo(downloadItem.file.toUri(), videoView)
                            && player.isPlaying) {
                        controlView.progress = player.currentPosition
                    }
                }, { throwable -> Log.e(LOG_TAG, "Timer error", throwable) })
    }

    override fun fullScreen(enabled: Boolean) {
        super.fullScreen(enabled)
        scaleView(videoView, downloadItem.width, downloadItem.height)
    }

    override fun onViewBind() {
        hidePlaybackError()
        Log.d(LOG_TAG, "width=${downloadItem.width}, height=${downloadItem.height}")
    }

    override fun onStateChanged(playWhenReady: Boolean, playbackState: Int) {
        Log.d(LOG_TAG, "State changed (playWhenReady: $playWhenReady, state: $playbackState)")
        videoView.keepScreenOn = pageSelected && playWhenReady
    }

    override fun onHostPause() {
        hostPaused = true
        videoView.keepScreenOn = false
        if (player?.ownsVideo(downloadItem.file.toUri(), videoView) == true) {
            player.pause()
        }
    }

    override fun onHostResume() {
        hostPaused = false
        if (pageSelected) {
            updateVideoPlaybackState()
        }
    }

    override fun onPlaybackError(error: PlaybackException) {
        Log.e(LOG_TAG, "Error playing file (${error.errorCodeName})", error)
        val kind = VideoPlaybackError.classify(error.errorCodeName)
        val message = when (kind) {
            VideoPlaybackError.Kind.UNSUPPORTED_FORMAT -> R.string.video_error_unsupported
            VideoPlaybackError.Kind.CORRUPT_OR_INCOMPLETE -> R.string.video_error_incomplete
            VideoPlaybackError.Kind.GENERIC -> R.string.video_error_generic
        }
        val requiresDownload = kind == VideoPlaybackError.Kind.CORRUPT_OR_INCOMPLETE
        videoView.post { showPlaybackError(message, downloadItem.file.exists(), requiresDownload) }
    }

    override fun onError(t: Throwable) {
        super.onError(t)
        videoView.post { showPlaybackError(R.string.video_download_error, false, true) }
    }

    override fun onVideoSizeChanged(width: Int, height: Int, unappliedRotationDegrees: Int, pixelWidthHeightRatio: Float) {
        Log.d(LOG_TAG, "Video size changed (width: $width, height: $height)")
        scaleView(videoView, width, height)
    }

    override fun onRenderedFirstFrame() {
        Log.d(LOG_TAG, "First frame of video rendered")
        videoView.post {
            hidePlaybackError()
            showVideoView()
        }
    }
}
