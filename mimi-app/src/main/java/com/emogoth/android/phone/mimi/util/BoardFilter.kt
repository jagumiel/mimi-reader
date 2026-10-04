package com.emogoth.android.phone.mimi.util

import com.mimireader.chanlib.models.ChanBoard

object BoardFilter {
    const val ALL = 0
    const val SFW_ONLY = 1
    const val NSFW_ONLY = 2

    @JvmStatic
    fun normalize(filter: Int): Int = when (filter) {
        SFW_ONLY, NSFW_ONLY -> filter
        else -> ALL
    }

    @JvmStatic
    fun apply(boards: List<ChanBoard>, filter: Int): List<ChanBoard> = when (normalize(filter)) {
        SFW_ONLY -> boards.filter { it.wsBoard == 1 }
        NSFW_ONLY -> boards.filter { it.wsBoard == 0 }
        else -> boards.toList()
    }
}
