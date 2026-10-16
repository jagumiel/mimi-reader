package com.emogoth.android.phone.mimi.util

import android.os.Environment
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppCacheCleanerTest {
    @Test
    fun clearRemovesCachesAndPreservesPersistentUserData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val preferenceKey = "cache_cleaner_test_preference"
        val internalCacheMarker = File(context.cacheDir, "cache-cleaner-test/media/video.mp4")
        val externalCacheMarker = context.externalCacheDir?.let {
            File(it, "cache-cleaner-test/image.jpg")
        }
        val filesMarker = File(context.filesDir, "cache-cleaner-test/preserved-file.txt")
        val databaseMarker = context.getDatabasePath("cache-cleaner-preserved.db")
        val downloadMarker = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let {
            File(it, "cache-cleaner-preserved-download.bin")
        }

        try {
            createMarker(internalCacheMarker, 4_096)
            externalCacheMarker?.let { createMarker(it, 2_048) }
            createMarker(filesMarker, 128)
            createMarker(databaseMarker, 128)
            downloadMarker?.let { createMarker(it, 128) }
            preferences.edit().putString(preferenceKey, "preserved").commit()

            val bytesFreed = Single.fromCallable { AppCacheCleaner.clear(context) }
                    .subscribeOn(Schedulers.io())
                    .blockingGet()

            assertTrue(bytesFreed >= 4_096L)
            assertFalse(internalCacheMarker.exists())
            assertFalse(externalCacheMarker?.exists() == true)
            assertTrue(filesMarker.exists())
            assertTrue(databaseMarker.exists())
            assertTrue(downloadMarker?.exists() != false)
            assertTrue(preferences.getString(preferenceKey, null) == "preserved")
            assertTrue(File(context.cacheDir, HttpClientFactory.HTTP_CACHE_DIRECTORY).exists())
        } finally {
            preferences.edit().remove(preferenceKey).commit()
            filesMarker.parentFile?.deleteRecursively()
            databaseMarker.delete()
            downloadMarker?.delete()
        }
    }

    private fun createMarker(file: File, byteCount: Int) {
        check(file.parentFile?.mkdirs() != false || file.parentFile?.isDirectory == true)
        file.outputStream().use { output ->
            output.write(ByteArray(byteCount) { 1 })
        }
    }
}
