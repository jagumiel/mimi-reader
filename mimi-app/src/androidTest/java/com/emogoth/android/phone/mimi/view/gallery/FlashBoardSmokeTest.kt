package com.emogoth.android.phone.mimi.view.gallery

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.activity.GalleryActivity2
import com.emogoth.android.phone.mimi.util.Extras
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Opt-in live check. CI remains independent of posts being deleted or CDN outages.
 * Run with -e flashThreadIds 3524315,3524302 (replace with currently available /f/ threads).
 */
@RunWith(AndroidJUnit4::class)
class FlashBoardSmokeTest {
    @Test
    fun realBoardAttachmentsDownloadAndRenderThroughTheGallery() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val threads = InstrumentationRegistry.getArguments().getString("flashThreadIds")
                ?.split(',')?.map { it.trim().toLong() }.orEmpty()
        assumeTrue("Live Flash check requires explicit flashThreadIds", threads.isNotEmpty())

        for (thread in threads) {
            val intent = Intent(context, GalleryActivity2::class.java)
                    .putExtra(Extras.EXTRAS_BOARD_NAME, "f")
                    .putExtra(Extras.EXTRAS_THREAD_ID, thread)
                    .putExtra(Extras.EXTRAS_GALLERY_TYPE, GalleryActivity2.GALLERY_TYPE_PAGER)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val scenario = ActivityScenario.launch<GalleryActivity2>(intent)
            var page: FlashPage? = null
            var host: GalleryActivity2? = null
            try {
                lateinit var root: View
                scenario.onActivity { activity ->
                    host = activity
                    root = activity.window.decorView
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                        activity.setShowWhenLocked(true)
                        activity.setTurnScreenOn(true)
                    }
                }
                val deadline = SystemClock.uptimeMillis() + 60_000L
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    // ActivityScenario.onActivity waits for UI idleness, which a 60 fps SWF
                    // need not provide. Posting directly also works for continuously animated movies.
                    instrumentation.runOnMainSync {
                        page = findFlash(root)
                        ready = page?.contentReady == true
                    }
                    SystemClock.sleep(100L)
                }
                assertTrue("Thread $thread never reached SWF readiness", ready)
                assertTrue("Thread $thread downloaded an empty/invalid file", page!!.localFile.length() > 32L)
                val evaluated = CountDownLatch(1)
                val result = AtomicReference<String>()
                instrumentation.runOnMainSync {
                    page!!.view.evaluateJavascript(
                            "(() => { const p = document.getElementById('ruffle-player'); " +
                                    "return window.mimiRuffleLoaded && window.mimiRuffleShouldRun && " +
                                    "p.ruffle().metadata.width > 0 && !p.ruffle().suspended && " +
                                    "p.shadowRoot.querySelector('canvas').width > 0; })()"
                    ) { value -> result.set(value); evaluated.countDown() }
                }
                assertTrue(evaluated.await(5, TimeUnit.SECONDS))
                assertTrue("Thread $thread has no active rendered SWF: ${result.get()}", result.get() == "true")
                SystemClock.sleep(1500L)
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                if (screenshot != null) {
                    val directory = File(context.getExternalFilesDir(null), "flash-validation").apply { mkdirs() }
                    File(directory, "$thread.png").outputStream().use {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                    screenshot.recycle()
                }
            } finally {
                instrumentation.runOnMainSync {
                    page?.release()
                    host?.finish()
                }
                scenario.close()
            }
        }
    }

    private fun findFlash(view: View): FlashPage? {
        if (view is FlashPage) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findFlash(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
