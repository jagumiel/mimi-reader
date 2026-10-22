package com.emogoth.android.phone.mimi.widget

import kotlin.math.roundToInt

/** Adapter-index progress: post heights are not uniform or stable while images load. */
object ThreadScrollPosition {
    fun range(count: Int, height: Int, scrollable: Boolean): Int =
        if (!scrollable || count < 2 || height <= 0) 0
        else minOf((count - 1L) * 1000, Int.MAX_VALUE.toLong() - height).toInt()

    fun position(offset: Int, range: Int, count: Int): Int =
        if (range <= 0 || count <= 1) 0
        else (offset.coerceIn(0, range).toDouble() / range * (count - 1)).roundToInt()

    fun offset(position: Int, fraction: Double, range: Int, count: Int): Int =
        if (range <= 0 || count <= 1) 0
        else (((position + fraction.coerceIn(0.0, 0.999)) / (count - 1))
            .coerceIn(0.0, 1.0) * range).roundToInt()

    fun label(position: Int, count: Int): String? =
        if (count <= 0) null else "${position.coerceIn(0, count - 1) + 1} / $count"
}
