package com.emogoth.android.phone.mimi.util

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import io.reactivex.Single
import java.lang.ref.WeakReference

class MediaUtil {
    companion object {
        fun videoInfo(videoPath: String): Single<MediaInfo> {
            return Single.defer {
                val metadataRetriever = MediaMetadataRetriever()
                metadataRetriever.setDataSource(videoPath)
                val hasAudio = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) ?: "no"
                val duration = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) ?: ""
                val mimeType = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE) ?: ""
                metadataRetriever.release()

                Single.just(MediaInfo(hasAudio.equals("yes", ignoreCase = true), duration.toLong(), mimeType))
            }
        }

        fun previewImage(videoPath: String): Single<Bitmap> {
            return Single.defer {
                val metadataRetriever = MediaMetadataRetriever()
                metadataRetriever.setDataSource(videoPath)
                val weakBmp = WeakReference(metadataRetriever.frameAtTime)
                metadataRetriever.release()
                Single.just(weakBmp.get())
            }
        }
    }
}

data class MediaInfo(val hasAudio: Boolean, val duration: Long, val mimeType: String) {
    companion object {
        @JvmStatic
        fun empty(): MediaInfo {
            return MediaInfo(false, -1, "")
        }
    }
}
