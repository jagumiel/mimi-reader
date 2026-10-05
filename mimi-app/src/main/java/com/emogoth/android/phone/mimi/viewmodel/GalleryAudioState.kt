package com.emogoth.android.phone.mimi.viewmodel

/** Audio preference shared by every video in the current gallery session. */
class GalleryAudioState(audioEnabledByDefault: Boolean = false) {
    var muted: Boolean = !audioEnabledByDefault
        private set

    fun setMuted(muted: Boolean) {
        this.muted = muted
    }
}
