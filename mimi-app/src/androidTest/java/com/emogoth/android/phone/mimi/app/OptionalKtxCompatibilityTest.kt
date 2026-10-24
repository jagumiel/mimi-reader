package com.emogoth.android.phone.mimi.app

import androidx.appcompat.view.ContextThemeWrapper
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.RxWorker
import androidx.work.workDataOf
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.autorefresh.RefreshWorker
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Build requests and detached preferences only: never enqueue work or edit user data. */
@RunWith(AndroidJUnit4::class)
class OptionalKtxCompatibilityTest {
    @Test fun runtimeStillOwnsKotlinWorkBuildersAndRoomExtensions() {
        val data = workDataOf("board" to "g", "thread" to 42L, "background" to true)
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setInputData(data)
            .addTag("ktx-compat-fixture")
            .build()
        assertEquals("g", data.getString("board"))
        assertEquals(42L, data.getLong("thread", -1L))
        assertTrue(data.getBoolean("background", false))
        assertTrue(request.tags.contains("ktx-compat-fixture"))
        assertTrue(request.tags.contains(RefreshWorker::class.java.name))
        assertTrue(RxWorker::class.java.isAssignableFrom(RefreshWorker::class.java))
        assertNotNull(Class.forName("androidx.room.RoomDatabaseKt"))
        // No WorkManager enqueue or worker execution: this is an offline API-owner check.
    }

    @Test fun corePreferencesWorkInAllThemesWithoutTheUnusedGroupExtensions() {
        assertThrows(ClassNotFoundException::class.java) {
            Class.forName("androidx.preference.PreferenceGroupKt")
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (theme in listOf(R.style.Theme_Mimi_Light_Toolbar_Default,
                R.style.Theme_Mimi_Dark_Toolbar_Default, R.style.Theme_Mimi_Black_Toolbar_Default)) {
                val context = ContextThemeWrapper(instrumentation.targetContext, theme)
                val manager = PreferenceManager(context)
                // Default put methods throw: an unexpected write cannot reach real preferences.
                manager.preferenceDataStore = object : PreferenceDataStore() {}
                val screen = manager.createPreferenceScreen(context)
                val preference = Preference(context).apply {
                    key = "ktx-compat-fixture"
                    isPersistent = false
                }
                assertTrue(screen.addPreference(preference))
                assertEquals(1, screen.preferenceCount)
                assertSame(preference, screen.getPreference(0))
                assertSame(preference, screen.findPreference<Preference>("ktx-compat-fixture"))
                assertTrue(screen.removePreference(preference))
                assertEquals(0, screen.preferenceCount)
            }
        }
    }
}
