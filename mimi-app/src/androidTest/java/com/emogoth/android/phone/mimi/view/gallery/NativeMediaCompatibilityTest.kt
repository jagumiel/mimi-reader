package com.emogoth.android.phone.mimi.view.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Base64
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.activity.TabsActivity
import com.emogoth.android.phone.mimi.util.DownloadItem
import com.emogoth.android.phone.mimi.viewmodel.GalleryItem
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import com.shockwave.pdfium.PdfiumCore
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Original local fixtures only. The same real native renderers run on 4 KB and 16 KB devices. */
@RunWith(AndroidJUnit4::class)
class NativeMediaCompatibilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun verifyRequestedRuntimePageSize() {
        InstrumentationRegistry.getArguments().getString("expectedPageSize")?.let {
            assertEquals("Wrong runtime page size", it.toLong(), Os.sysconf(OsConstants._SC_PAGESIZE))
        }
    }

    class FixturePdf(context: Context, model: GalleryViewModel) : PdfPage(context, model) {
        fun load(file: File, events: (GalleryPageEvent) -> Unit = {}) {
            bind(GalleryItem.empty(), events)
            postId = 901
            downloadItem = DownloadItem(901, "", "", 300, 400, file, file.name)
            onPageSelectedChange(true)
        }
    }

    class FixtureGif(context: Context) : GifPage(context, GalleryViewModel.empty()) {
        fun load(file: File, events: (GalleryPageEvent) -> Unit) {
            bind(GalleryItem.empty(), events)
            postId = 902
            downloadItem = DownloadItem(902, "", "", 2, 2, file, file.name)
            onPageSelectedChange(true)
            onComplete()
        }
    }

    @Test fun pdfRendersPagesZoomsAndReleasesOnPauseAndDetach() {
        val file = pdf()
        val scenario = host()
        lateinit var page: FixturePdf
        try {
            scenario.onActivity { activity ->
                page = FixturePdf(activity, GalleryViewModel.empty())
                activity.addContentView(page, FrameLayout.LayoutParams(600, 800))
                page.load(file)
                assertFalse("Ready must wait for native rendering", page.contentReady)
            }
            waitFor(scenario) { page.contentReady && color(page) == Color.RED }
            scenario.onActivity {
                assertEquals(2, page.view.pageCount)
                page.view.jumpTo(1)
                page.view.zoomTo(1.5f)
                page.view.loadPages()
            }
            waitFor(scenario) { color(page) == Color.BLUE }
            scenario.onActivity {
                page.onHostPause()
                assertTrue(page.view.isRecycled)
                assertFalse(page.contentReady)
                page.onHostResume()
            }
            waitFor(scenario) { page.contentReady }
            scenario.onActivity {
                assertEquals(1, page.view.currentPage)
                assertEquals(1.5f, page.view.zoom, 0.01f)
                val parent = page.parent as ViewGroup
                parent.removeView(page)
                assertTrue(page.view.isRecycled)
                assertFalse(page.contentReady)
                parent.addView(page)
            }
            waitFor(scenario) { page.contentReady }
            scenario.onActivity {
                assertEquals(1, page.view.currentPage)
                page.onPageSelectedChange(false)
                assertTrue(page.view.isRecycled)
                page.onPageSelectedChange(true)
            }
            waitFor(scenario) { page.contentReady }
        } finally {
            scenario.onActivity { page.release() }
            scenario.close()
            file.delete()
        }
    }

    @Test fun pdfViewportSurvivesActivityAndModelRecreation() {
        val file = pdf()
        val scenario = host()
        val model = GalleryViewModel.empty()
        lateinit var page: FixturePdf
        val saved = Bundle()
        try {
            scenario.onActivity { activity ->
                page = FixturePdf(activity, model)
                activity.addContentView(page, FrameLayout.LayoutParams(600, 800))
                page.load(file)
            }
            waitFor(scenario) { page.contentReady }
            scenario.onActivity {
                page.view.jumpTo(1)
                page.view.zoomTo(2f)
                page.onHostPause()
                model.saveState(saved)
                page.release()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = GalleryViewModel.empty().apply { initState(saved) }
                assertEquals(1 to 2f, restored.pdfViewport(901))
                page = FixturePdf(activity, restored)
                activity.addContentView(page, FrameLayout.LayoutParams(600, 800))
                page.load(file)
            }
            waitFor(scenario) { page.contentReady }
            scenario.onActivity {
                assertEquals(1, page.view.currentPage)
                assertEquals(2f, page.view.zoom, 0.01f)
            }
        } finally {
            scenario.onActivity { page.release() }
            scenario.close()
            file.delete()
        }
    }

    @Test fun damagedAndPasswordProtectedPdfsShowErrorsAndRetryWorks() {
        val bad = File.createTempFile("mimi-bad", ".pdf", context.cacheDir).apply { writeText("not a PDF") }
        val valid = pdf()
        val encrypted = File.createTempFile("mimi-password", ".pdf", context.cacheDir).apply {
            writeBytes(Base64.decode(ENCRYPTED_PDF, Base64.DEFAULT))
        }
        val scenario = host()
        lateinit var page: FixturePdf
        try {
            scenario.onActivity { activity ->
                page = FixturePdf(activity, GalleryViewModel.empty())
                activity.addContentView(page, FrameLayout.LayoutParams(600, 800))
                page.load(bad)
            }
            waitFor(scenario) { page.findViewWithTag<TextView>("pdf-error-text").isShown }
            scenario.onActivity {
                assertFalse(page.contentReady)
                assertTrue(page.findViewWithTag<Button>("pdf-retry").isShown)
                valid.copyTo(bad, overwrite = true)
                page.findViewWithTag<Button>("pdf-retry").performClick()
            }
            waitFor(scenario) { page.contentReady }
            scenario.onActivity { page.load(encrypted) }
            waitFor(scenario) { page.findViewWithTag<TextView>("pdf-error-text").isShown }
            scenario.onActivity { assertFalse(page.contentReady) }
            // Prove this is an actual password-protected PDF, not just another malformed file.
            val core = PdfiumCore(context)
            val document = core.newDocument(ParcelFileDescriptor.open(encrypted, ParcelFileDescriptor.MODE_READ_ONLY), "mimi-fixture")
            try { assertEquals(1, core.getPageCount(document)) } finally { core.closeDocument(document) }
        } finally {
            scenario.onActivity { page.release() }
            scenario.close()
            bad.delete(); valid.delete(); encrypted.delete()
        }
    }

    @Test fun rebindingAndReleaseCannotReportAnOldPdfAsReady() {
        val file = pdf()
        val scenario = host()
        val ready = AtomicInteger()
        lateinit var page: FixturePdf
        try {
            scenario.onActivity { activity ->
                page = FixturePdf(activity, GalleryViewModel.empty())
                activity.addContentView(page, FrameLayout.LayoutParams(600, 800))
                page.load(file) { ready.incrementAndGet() }
                page.load(file) { ready.incrementAndGet() }
                page.release()
            }
            SystemClock.sleep(700)
            scenario.onActivity {
                assertTrue(page.view.isRecycled)
                assertFalse(page.contentReady)
                assertEquals(0, ready.get())
            }
        } finally {
            scenario.onActivity { page.release() }
            scenario.close()
            file.delete()
        }
    }

    @Test fun gifDecodesLoopsPausesResumesAndReleases() {
        val file = File.createTempFile("mimi-native", ".gif", context.cacheDir).apply {
            // Original two-frame, two-colour 1x1 GIF, 100 ms per frame, infinite loop.
            writeBytes(Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAQABAAACAkQBACH5BAAKAAAALAAAAAABAAEAAAICRAEAOw==", Base64.DEFAULT))
        }
        val scenario = host()
        val loops = AtomicInteger()
        lateinit var page: FixtureGif
        lateinit var drawable: GifDrawable
        try {
            scenario.onActivity { activity ->
                page = FixtureGif(activity)
                activity.addContentView(page, FrameLayout.LayoutParams(200, 200))
                page.load(file) { if (it == GalleryPageEvent.PLAYBACK_COMPLETED) loops.incrementAndGet() }
                drawable = page.view.drawable as GifDrawable
                assertEquals(2, drawable.numberOfFrames)
                assertTrue(page.contentReady)
            }
            waitFor(scenario) { loops.get() > 0 }
            scenario.onActivity {
                page.onHostPause()
                assertFalse(drawable.isRunning)
                page.onHostResume()
                assertTrue(drawable.isRunning)
                page.onPageSelectedChange(false)
                assertFalse(drawable.isRunning)
                page.release()
                assertTrue(drawable.isRecycled)
                assertFalse(page.contentReady)
            }
        } finally {
            scenario.onActivity { page.release() }
            scenario.close()
            file.delete()
        }
    }

    @Test fun pdfViewportStateIsBoundedAndNormalizesInvalidValues() {
        val model = GalleryViewModel.empty()
        model.savePdfViewport(-1, 5, 2f)
        assertEquals(0 to 1f, model.pdfViewport(-1))
        model.savePdfViewport(1, -10, Float.NaN)
        assertEquals(0 to 1f, model.pdfViewport(1))
        model.savePdfViewport(2, 5, 90f)
        assertEquals(5 to 3f, model.pdfViewport(2))
        for (id in 3L..70L) model.savePdfViewport(id, 1, 2f)
        assertEquals(0 to 1f, model.pdfViewport(2))
        val saved = Bundle()
        model.saveState(saved)
        val restored = GalleryViewModel.empty().apply { initState(saved) }
        assertEquals(1 to 2f, restored.pdfViewport(70))
    }

    private fun host() = ActivityScenario.launch<TabsActivity>(Intent(context, TabsActivity::class.java))

    private fun waitFor(scenario: ActivityScenario<TabsActivity>, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            scenario.onActivity { done = condition() }
            if (done) return
            SystemClock.sleep(50)
        }
        fail("Native media fixture did not reach its expected state")
    }

    private fun color(page: FixturePdf): Int {
        if (!page.contentReady || page.view.width == 0 || page.view.height == 0) return Color.TRANSPARENT
        val bitmap = Bitmap.createBitmap(page.view.width, page.view.height, Bitmap.Config.ARGB_8888)
        return try {
            page.view.draw(Canvas(bitmap))
            bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        } finally { bitmap.recycle() }
    }

    private fun pdf(): File {
        val file = File.createTempFile("mimi-native", ".pdf", context.cacheDir)
        val document = PdfDocument()
        try {
            for ((index, colour) in listOf(Color.RED, Color.BLUE).withIndex()) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(300, 400, index + 1).create())
                page.canvas.drawColor(colour)
                document.finishPage(page)
            }
            file.outputStream().use { document.writeTo(it) }
        } finally { document.close() }
        return file
    }

    companion object {
        // Original one-page Standard Security R2 fixture. Password: mimi-fixture; no personal data.
        private const val ENCRYPTED_PDF = "JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9Db3VudCAxIC9LaWRzIFszIDAgUl0gPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCAyMDAgMjAwXSA+PgplbmRvYmoKNCAwIG9iago8PCAvRmlsdGVyIC9TdGFuZGFyZCAvViAxIC9SIDIgL0xlbmd0aCA0MCAvTyA8YjdmZjc2ZjZjYTg1MmIxY2VhMDUzODM2Nzk1OGM5ZWE5MWFiZDMzMzc5NzU3YTNlNmQ4OTc5ZDRiMmJlYzVjOD4gL1UgPGFlYTQ3NjFmYzMzMjE0YTI3YzMyZDkyZDM3MTkyOTc1NWQ1MmQ0YmUyNjg2ODI3ZTNmODU5YmRiNWYwZmYzMDI+IC9QIC00ID4+CmVuZG9iagp4cmVmCjAgNQowMDAwMDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAwMDkgMDAwMDAgbiAKMDAwMDAwMDA1OCAwMDAwMCBuIAowMDAwMDAwMTE1IDAwMDAwIG4gCjAwMDAwMDAxODYgMDAwMDAgbiAKdHJhaWxlcgo8PCAvU2l6ZSA1IC9Sb290IDEgMCBSIC9FbmNyeXB0IDQgMCBSIC9JRCBbPDY2MzkxYjE5ZGNlNzdjMzY4MWI0MjdhMzc3YzQzZDQ3Pjw2NjM5MWIxOWRjZTc3YzM2ODFiNDI3YTM3N2M0M2Q0Nz5dID4+CnN0YXJ0eHJlZgozOTIKJSVFT0YK"
    }
}
