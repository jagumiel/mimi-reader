package com.emogoth.android.phone.mimi.app

import android.content.Context
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferenceMigrationTest {
    private lateinit var preferences: SharedPreferences

    private val keys = PreferenceMigration.Keys(
        theme = "theme",
        themeColor = "theme_color",
        boardOrder = "board_order",
        boardFilter = "board_filter",
        backgroundNotification = "background_notification",
        historyPruneTime = "history_prune_time"
    )

    @Before
    fun createIsolatedPreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        preferences = context.getSharedPreferences(TEST_PREFERENCES, Context.MODE_PRIVATE)
        assertTrue(preferences.edit().clear().commit())
    }

    @After
    fun clearIsolatedPreferences() {
        preferences.edit().clear().commit()
    }

    @Test
    fun migrationPreservesUserSettingsAndNormalizesLegacyTypes() {
        val expectedFolder = "content://com.android.externalstorage.documents/tree/primary%3APictures"
        val expectedTags = setOf("saved", "custom")
        assertTrue(
            preferences.edit()
                .putString(keys.theme, "2")
                .putInt(keys.themeColor, 4)
                .putString(keys.boardOrder, "2")
                .putString(keys.boardFilter, "1")
                .putString(keys.backgroundNotification, "1")
                .putInt(keys.historyPruneTime, 3)
                .putBoolean("save_history", false)
                .putString("download_folder", expectedFolder)
                .putStringSet("unrelated_custom_value", expectedTags)
                .commit()
        )

        PreferenceMigration.migrate(preferences, keys)

        assertEquals("2", preferences.getString(keys.theme, null))
        assertEquals("4", preferences.getString(keys.themeColor, null))
        assertEquals(2, preferences.getInt(keys.boardOrder, -1))
        assertEquals(1, preferences.getInt(keys.boardFilter, -1))
        assertEquals("1", preferences.getString(keys.backgroundNotification, null))
        assertEquals("3", preferences.getString(keys.historyPruneTime, null))
        assertEquals(false, preferences.getBoolean("save_history", true))
        assertEquals(expectedFolder, preferences.getString("download_folder", null))
        assertEquals(expectedTags, preferences.getStringSet("unrelated_custom_value", emptySet()))
        assertEquals(
            PreferenceMigration.CURRENT_VERSION,
            preferences.getInt(PreferenceMigration.VERSION_KEY, 0)
        )

        val migratedValues = preferences.all.toMap()
        PreferenceMigration.migrate(preferences, keys)
        assertEquals(migratedValues, preferences.all)
    }

    @Test
    fun migrationReplacesOnlyTheInvalidLegacyNotificationDefault() {
        assertTrue(
            preferences.edit()
                .putString(keys.backgroundNotification, "0")
                .putBoolean("audio_enabled", true)
                .commit()
        )

        PreferenceMigration.migrate(preferences, keys)

        assertEquals("3", preferences.getString(keys.backgroundNotification, null))
        assertTrue(preferences.getBoolean("audio_enabled", false))
    }

    @Test
    fun newerPreferenceSchemaIsNotDowngradedOrModified() {
        assertTrue(
            preferences.edit()
                .putInt(PreferenceMigration.VERSION_KEY, PreferenceMigration.CURRENT_VERSION + 1)
                .putString(keys.boardOrder, "future-format")
                .commit()
        )
        val originalValues = preferences.all.toMap()

        PreferenceMigration.migrate(preferences, keys)

        assertEquals(originalValues, preferences.all)
    }

    companion object {
        private const val TEST_PREFERENCES = "preference-migration-test"
    }
}
