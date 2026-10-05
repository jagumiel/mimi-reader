package com.emogoth.android.phone.mimi.app

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.emogoth.android.phone.mimi.R

/** Applies explicit, non-destructive migrations to preferences kept across app updates. */
internal object PreferenceMigration {
    internal const val VERSION_KEY = "mimi_preference_schema_version"
    internal const val CURRENT_VERSION = 1

    internal data class Keys(
        val theme: String,
        val themeColor: String,
        val boardOrder: String,
        val boardFilter: String,
        val backgroundNotification: String,
        val historyPruneTime: String
    )

    fun migrate(context: Context) {
        migrate(
            PreferenceManager.getDefaultSharedPreferences(context),
            Keys(
                theme = context.getString(R.string.theme_pref),
                themeColor = context.getString(R.string.theme_color_pref),
                boardOrder = context.getString(R.string.board_order_pref),
                boardFilter = context.getString(R.string.board_filter_pref),
                backgroundNotification = context.getString(R.string.background_notification_pref),
                historyPruneTime = context.getString(R.string.history_prune_time_pref)
            )
        )
    }

    internal fun migrate(preferences: SharedPreferences, keys: Keys) {
        val storedVersion = preferences.getInt(VERSION_KEY, 0)
        if (storedVersion >= CURRENT_VERSION) {
            return
        }

        val values = preferences.all
        val editor = preferences.edit()

        // Board sorting predates the current preference contract. Preserve numeric values
        // written as strings by old or modified builds while standardizing the stored type.
        migrateStringToInt(values[keys.boardOrder], keys.boardOrder, 0, editor)
        migrateStringToInt(values[keys.boardFilter], keys.boardFilter, 0, editor)

        // ListPreference values are strings. Some old builds/defaults used numbers directly,
        // which otherwise cause ClassCastException during application startup.
        migrateNumberToString(values[keys.theme], keys.theme, "0", editor)
        migrateNumberToString(values[keys.themeColor], keys.themeColor, "0", editor)
        migrateNumberToString(values[keys.historyPruneTime], keys.historyPruneTime, "0", editor)

        val notificationValue = values[keys.backgroundNotification]
        when (notificationValue) {
            null, "0", 0 -> editor.putString(keys.backgroundNotification, "3")
            is Number -> editor.putString(keys.backgroundNotification, notificationValue.toInt().toString())
            is String -> if (notificationValue.toIntOrNull() == null) {
                editor.putString(keys.backgroundNotification, "3")
            }
            else -> editor.putString(keys.backgroundNotification, "3")
        }

        editor.putInt(VERSION_KEY, CURRENT_VERSION)
        editor.apply()
    }

    private fun migrateStringToInt(
        value: Any?,
        key: String,
        fallback: Int,
        editor: SharedPreferences.Editor
    ) {
        when (value) {
            is String -> editor.putInt(key, value.toIntOrNull() ?: fallback)
            null, is Number -> Unit
            else -> editor.putInt(key, fallback)
        }
    }

    private fun migrateNumberToString(
        value: Any?,
        key: String,
        fallback: String,
        editor: SharedPreferences.Editor
    ) {
        when (value) {
            is Number -> editor.putString(key, value.toInt().toString())
            is String -> if (value.toIntOrNull() == null) editor.putString(key, fallback)
            null -> Unit
            else -> editor.putString(key, fallback)
        }
    }
}
