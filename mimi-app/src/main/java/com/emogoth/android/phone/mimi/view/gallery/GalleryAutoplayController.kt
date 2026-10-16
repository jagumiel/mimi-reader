package com.emogoth.android.phone.mimi.view.gallery

internal class GalleryAutoplayController(enabled: Boolean) {
    var enabled = enabled
        private set

    private var currentPosition = -1
    private var itemCount = 0
    private var hostResumed = false
    private var advancePending = false

    fun onPageSelected(position: Int, count: Int) {
        currentPosition = position
        itemCount = count
        advancePending = false
    }

    fun onHostResume() {
        hostResumed = true
    }

    fun onHostPause() {
        hostResumed = false
        advancePending = false
    }

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        advancePending = false
    }

    fun shouldScheduleImage(position: Int, contentReady: Boolean): Boolean {
        return enabled && hostResumed && !advancePending && contentReady && position == currentPosition
    }

    fun requestAdvance(position: Int): Int? {
        if (!enabled || !hostResumed || advancePending || position != currentPosition) {
            return null
        }

        val nextPosition = position + 1
        if (nextPosition >= itemCount) {
            return null
        }

        advancePending = true
        return nextPosition
    }
}
