package com.emogoth.android.phone.mimi.util

import androidx.annotation.StringRes
import com.emogoth.android.phone.mimi.R

/** The 4chan board catalog, using home-page themes plus API-only board assignments. */
object BoardCatalog {
    const val UNKNOWN = 0
    const val JAPANESE_CULTURE = 1
    const val VIDEO_GAMES = 2
    const val INTERESTS = 3
    const val CREATIVE = 4
    const val OTHER = 5
    const val MISC = 6
    const val ADULT = 7

    private val categories = mapOf(
        // Japanese Culture
        "a" to JAPANESE_CULTURE, "c" to JAPANESE_CULTURE,
        "w" to JAPANESE_CULTURE, "m" to JAPANESE_CULTURE,
        "cgl" to JAPANESE_CULTURE, "cm" to JAPANESE_CULTURE,
        "f" to JAPANESE_CULTURE, "n" to JAPANESE_CULTURE,
        "jp" to JAPANESE_CULTURE, "vt" to JAPANESE_CULTURE,

        // Video Games
        "v" to VIDEO_GAMES, "vg" to VIDEO_GAMES, "vm" to VIDEO_GAMES,
        "vmg" to VIDEO_GAMES, "vp" to VIDEO_GAMES, "vr" to VIDEO_GAMES,
        "vrpg" to VIDEO_GAMES, "vst" to VIDEO_GAMES,

        // Interests
        "co" to INTERESTS, "g" to INTERESTS, "tv" to INTERESTS,
        "k" to INTERESTS, "o" to INTERESTS, "an" to INTERESTS,
        "tg" to INTERESTS, "sp" to INTERESTS, "xs" to INTERESTS,
        "pw" to INTERESTS, "sci" to INTERESTS, "his" to INTERESTS,
        "int" to INTERESTS, "out" to INTERESTS, "toy" to INTERESTS,

        // Creative
        "i" to CREATIVE, "po" to CREATIVE, "p" to CREATIVE,
        "ck" to CREATIVE, "ic" to CREATIVE, "wg" to CREATIVE,
        "lit" to CREATIVE, "mu" to CREATIVE, "fa" to CREATIVE,
        "3" to CREATIVE, "gd" to CREATIVE, "diy" to CREATIVE,
        "wsg" to CREATIVE, "qst" to CREATIVE,

        // Other
        "biz" to OTHER, "trv" to OTHER, "fit" to OTHER, "x" to OTHER,
        "adv" to OTHER, "lgbt" to OTHER, "mlp" to OTHER,
        "news" to OTHER, "wsr" to OTHER, "vip" to OTHER,

        // Miscellaneous (NSFW)
        "b" to MISC, "r9k" to MISC, "pol" to MISC,
        "bant" to MISC, "soc" to MISC, "s4s" to MISC,
        "trash" to MISC,

        // Adult (NSFW)
        "s" to ADULT, "hc" to ADULT, "hm" to ADULT, "h" to ADULT,
        "e" to ADULT, "u" to ADULT, "d" to ADULT, "y" to ADULT,
        "t" to ADULT, "hr" to ADULT, "gif" to ADULT, "aco" to ADULT,
        "r" to ADULT
    )

    @JvmStatic
    fun categoryFor(boardName: String?): Int = categories[boardName] ?: OTHER

    @JvmStatic
    fun isPublicBoard(boardName: String?): Boolean =
        !boardName.isNullOrBlank()

    @JvmStatic
    fun listedBoardNames(): Set<String> = categories.keys

    @StringRes
    @JvmStatic
    fun categoryLabel(category: Int): Int = when (category) {
        JAPANESE_CULTURE -> R.string.board_category_japanese_culture
        VIDEO_GAMES -> R.string.board_category_video_games
        INTERESTS -> R.string.board_category_interests
        CREATIVE -> R.string.board_category_creative
        MISC -> R.string.board_category_misc
        ADULT -> R.string.board_category_adult
        else -> R.string.board_category_other
    }
}
