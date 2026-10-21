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
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Original local fixtures only. The same real native renderers run on 4 KB and 16 KB devices. */
@RunWith(AndroidJUnit4::class)
class NativeMediaCompatibilityTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    // TabsActivity may clear cache on first install/upgrade, including while it is launching.
    // Give each case its own JUnit-owned directory outside all disposable app caches.
    @get:Rule val fixtures = TemporaryFolder(context.filesDir)

    @Before fun verifyRequestedRuntimePageSize() {
        assertEquals("Fixture storage must be outside disposable caches", context.filesDir.canonicalFile,
                fixtures.root.canonicalFile.parentFile)
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
            waitForPdf(scenario, page, "Initial red PDF page")
            scenario.onActivity {
                assertEquals(2, page.view.pageCount)
                selectPdfPage(page, 1, 1.5f)
            }
            waitForPdf(scenario, page, "Blue PDF page after zoom", 1, 1.5f, Color.BLUE)
            scenario.onActivity {
                page.onHostPause()
                assertTrue(page.view.isRecycled)
                assertFalse(page.contentReady)
                page.onHostResume()
            }
            waitForPdf(scenario, page, "Blue PDF page after host resume", 1, 1.5f, Color.BLUE)
            scenario.onActivity {
                assertEquals(1, page.view.currentPage)
                assertEquals(1.5f, page.view.zoom, 0.01f)
                val parent = page.parent as ViewGroup
                parent.removeView(page)
                assertTrue(page.view.isRecycled)
                assertFalse(page.contentReady)
                parent.addView(page)
            }
            waitForPdf(scenario, page, "Blue PDF page after reattach", 1, 1.5f, Color.BLUE)
            scenario.onActivity {
                assertEquals(1, page.view.currentPage)
                page.onPageSelectedChange(false)
                assertTrue(page.view.isRecycled)
                page.onPageSelectedChange(true)
            }
            waitForPdf(scenario, page, "Blue PDF page after reselection", 1, 1.5f, Color.BLUE)
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
            waitForPdf(scenario, page, "PDF before recreation")
            scenario.onActivity {
                selectPdfPage(page, 1, 2f)
            }
            waitForPdf(scenario, page, "Blue PDF page before recreation", 1, 2f, Color.BLUE)
            scenario.onActivity {
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
            waitForPdf(scenario, page, "Blue PDF page after recreation", 1, 2f, Color.BLUE)
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
        val bad = fixtureFile("mimi-bad", ".pdf").apply { writeText("not a PDF") }
        val valid = pdf()
        val encrypted = fixtureFile("mimi-password", ".pdf").apply {
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
            waitFor(scenario, "Damaged PDF error", { pdfState(page) }) {
                page.findViewWithTag<TextView>("pdf-error-text").isShown
            }
            scenario.onActivity {
                assertFalse(page.contentReady)
                assertTrue(page.findViewWithTag<Button>("pdf-retry").isShown)
                valid.copyTo(bad, overwrite = true)
                page.findViewWithTag<Button>("pdf-retry").performClick()
            }
            waitForPdf(scenario, page, "PDF after explicit retry")
            scenario.onActivity { page.load(encrypted) }
            waitFor(scenario, "Password-protected PDF error", { pdfState(page) }) {
                page.findViewWithTag<TextView>("pdf-error-text").isShown
            }
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
        val file = fixtureFile("mimi-native", ".gif").apply {
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

    private fun waitFor(
        scenario: ActivityScenario<TabsActivity>,
        description: String = "Native media fixture",
        diagnostics: () -> String = { "" },
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var done = false
            scenario.onActivity { done = condition() }
            if (done) return
            SystemClock.sleep(50)
        }
        var state = ""
        scenario.onActivity { state = diagnostics() }
        fail("$description did not reach its expected state within 15 seconds. $state")
    }

    private fun selectPdfPage(page: FixturePdf, index: Int, zoom: Float) {
        // zoomTo changes only the scale field. jumpTo must calculate offsets and load tiles
        // AFTER that change; reversing them leaves the viewport on a page boundary.
        page.view.zoomTo(zoom)
        page.view.jumpTo(index)
    }

    private fun waitForPdf(
        scenario: ActivityScenario<TabsActivity>, page: FixturePdf, stage: String,
        expectedPage: Int = 0, expectedZoom: Float = 1f, expectedColor: Int = Color.RED
    ) = waitFor(scenario, stage, { pdfState(page) }) {
        page.contentReady && page.view.currentPage == expectedPage &&
                kotlin.math.abs(page.view.zoom - expectedZoom) < 0.01f &&
                colors(page).all { it == expectedColor }
    }

    private fun pdfState(page: FixturePdf): String =
        "fileExists=${page.localFile.isFile}, ready=${page.contentReady}, " +
                "attached=${page.isAttachedToWindow}, shown=${page.view.isShown}, " +
                "recycled=${page.view.isRecycled}, page=${page.view.currentPage}/${page.view.pageCount}, " +
                "zoom=${page.view.zoom}, offset=${page.view.currentXOffset},${page.view.currentYOffset}, " +
                "size=${page.view.width}x${page.view.height}, " +
                "pixels=${colors(page).map { Integer.toHexString(it) }}, " +
                "errorShown=${page.findViewWithTag<TextView>("pdf-error-text").isShown}"

    private fun fixtureFile(prefix: String, suffix: String): File =
        File.createTempFile(prefix, suffix, fixtures.root)

    private fun colors(page: FixturePdf): List<Int> {
        if (!page.contentReady || page.view.width == 0 || page.view.height == 0) return listOf(Color.TRANSPARENT)
        val bitmap = Bitmap.createBitmap(page.view.width, page.view.height, Bitmap.Config.ARGB_8888)
        return try {
            page.view.draw(Canvas(bitmap))
            listOf(0.25f, 0.5f, 0.75f).map {
                bitmap.getPixel(bitmap.width / 2, (bitmap.height * it).toInt())
            }
        } finally { bitmap.recycle() }
    }

    private fun pdf(): File {
        val file = fixtureFile("mimi-native", ".pdf")
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
