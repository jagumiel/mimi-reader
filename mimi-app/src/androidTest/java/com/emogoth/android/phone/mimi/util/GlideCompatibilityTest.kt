package com.emogoth.android.phone.mimi.util

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.data.DataFetcher
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.TabsActivity
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Local files and an intercepted HTTP client only; no live CDN or registry/singleton replacement. */
@RunWith(AndroidJUnit4::class)
class GlideCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule val files = TemporaryFolder(context.filesDir)
    private fun host() = ActivityScenario.launch<TabsActivity>(Intent(context, TabsActivity::class.java))
    private fun png(color: Int): ByteArray = ByteArrayOutputStream().use { output ->
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        try { bitmap.eraseColor(color); bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        finally { bitmap.recycle() }
        output.toByteArray()
    }
    private fun image(name: String, color: Int): File = files.newFile(name).apply { writeBytes(png(color)) }
    private fun waitFor(scenario: ActivityScenario<TabsActivity>, message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario.onActivity { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail(message)
    }
    private fun pixel(view: ImageView): Int? {
        val bitmap = (view.drawable as? BitmapDrawable)?.bitmap ?: return null
        // Read hardware/software decoders alike without disabling the production decode path.
        val sample = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return null
        return try { sample.getPixel(sample.width / 2, sample.height / 2) } finally { sample.recycle() }
    }

    @Test fun generatedModuleRegistersOkHttpAndItsFetcherDeliversImagesAndHttpErrors() {
        // Do not replace Glide's registry to make this assertion pass.
        val model = GlideUrl("https://glide-fixture.invalid/image.png")
        assertTrue(Glide.get(context).registry.getModelLoaders(model).any { it is OkHttpUrlLoader })
        val requests = AtomicInteger()
        val bytes = png(Color.RED)
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            val success = chain.request().url.encodedPath == "/image.png"
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if (success) 200 else 404).message("Local fixture")
                .body((if (success) bytes else byteArrayOf()).toResponseBody()).build()
        }.build()
        try {
            for (success in listOf(true, false)) {
                val fetcher = OkHttpUrlLoader(client).buildLoadData(
                    GlideUrl("https://glide-fixture.invalid/${if (success) "image.png" else "missing.png"}"),
                    16, 16, Options())!!.fetcher
                val done = CountDownLatch(1)
                val color = AtomicReference<Int?>()
                val error = AtomicReference<Exception?>()
                try {
                    fetcher.loadData(Priority.NORMAL, object : DataFetcher.DataCallback<InputStream> {
                        override fun onDataReady(data: InputStream?) {
                            try {
                                val decoded = BitmapFactory.decodeStream(data)
                                color.set(decoded?.getPixel(8, 8))
                                decoded?.recycle()
                            } finally { done.countDown() }
                        }
                        override fun onLoadFailed(e: Exception) { error.set(e); done.countDown() }
                    })
                    assertTrue(done.await(5, TimeUnit.SECONDS))
                    if (success) { assertNull(error.get()); assertEquals(Color.RED, color.get()) }
                    else { assertNotNull(error.get()); assertNull(color.get()) }
                } finally { fetcher.cleanup() }
            }
            assertEquals(2, requests.get())
        } finally { client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun thumbnailsReplaceRecycledImagesAndKeepFlashAndMissingPlaceholders() {
        val red = image("red.png", Color.RED)
        val blue = image("blue.png", Color.BLUE)
        val scenario = host()
        lateinit var view: ImageView
        try {
            scenario.onActivity { activity ->
                view = ImageView(activity).apply { minimumWidth = 16; minimumHeight = 16 }
                activity.addContentView(view, android.widget.FrameLayout.LayoutParams(16, 16))
                MediaThumbnail.load(view, red.toURI().toString(), ".png")
            }
            waitFor(scenario, "First thumbnail must decode") { pixel(view) == Color.RED }
            scenario.onActivity {
                Glide.with(view).clear(view)
                // clear() restores the request's placeholder, not necessarily a null drawable.
                assertNotEquals(Color.RED, pixel(view))
                MediaThumbnail.load(view, red.toURI().toString(), ".png")
                MediaThumbnail.load(view, blue.toURI().toString(), ".png")
            }
            waitFor(scenario, "Recycled image must show the new request, not the old image") { pixel(view) == Color.BLUE }
            scenario.onActivity {
                MediaThumbnail.load(view, null, ".swf")
                assertNotNull(view.drawable)
                assertEquals(context.getDrawable(R.drawable.thumbnail_flash)!!.intrinsicWidth, view.drawable.intrinsicWidth)
                MediaThumbnail.load(view, null, ".png")
                assertNotNull(view.drawable)
                Glide.with(view).clear(view)
            }
        } finally { scenario.close() }
    }

    @Test fun fallbackAndPrimarySuccessNotifyTheCallerWithoutReentrantLoads() {
        val blue = image("fallback.png", Color.BLUE).toURI().toString()
        val missing = File(files.root, "missing.png").toURI().toString()
        val scenario = host()
        lateinit var view: ImageView
        try {
            scenario.onActivity { activity ->
                view = ImageView(activity)
                activity.addContentView(view, android.widget.FrameLayout.LayoutParams(16, 16))
            }
            for ((primary, fallback, success) in listOf(Triple(missing, blue, true),
                Triple(blue, missing, true), Triple(missing, missing, false),
                Triple(missing, null, false), Triple(blue, null, true))) {
                val completed = AtomicInteger()
                val failed = AtomicInteger()
                scenario.onActivity {
                    MimiUtil.loadImageWithFallback(view, primary, fallback, null, object : RequestListener<Drawable> {
                        override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>, first: Boolean): Boolean {
                            failed.incrementAndGet(); return false
                        }
                        override fun onResourceReady(resource: Drawable, model: Any, target: Target<Drawable>?, source: DataSource, first: Boolean): Boolean {
                            completed.incrementAndGet(); return false
                        }
                    })
                }
                waitFor(scenario, "Primary/fallback must notify its terminal result") {
                    completed.get() + failed.get() > 0 && (!success || pixel(view) == Color.BLUE)
                }
                scenario.onActivity {
                    assertEquals(if (success) 1 else 0, completed.get())
                    assertEquals(if (success) 0 else 1, failed.get())
                    Glide.with(view).clear(view)
                }
            }
        } finally { scenario.close() }
    }

    @Test fun transformedImageCanBeReadFromDiskAfterMemoryIsCleared() {
        val file = image("cached.png", Color.RED)
        val scenario = host()
        try {
            val manager = Glide.with(context)
            val first = manager.asBitmap().load(file).override(16, 16)
                .diskCacheStrategy(DiskCacheStrategy.ALL).disallowHardwareConfig().submit()
            try { assertEquals(Color.RED, first.get(10, TimeUnit.SECONDS).getPixel(8, 8)) }
            finally { manager.clear(first) }
            scenario.onActivity { Glide.get(context).clearMemory() }
            val source = AtomicReference<DataSource>()
            val request = manager.asBitmap().load(file).override(16, 16)
                .diskCacheStrategy(DiskCacheStrategy.ALL).disallowHardwareConfig().onlyRetrieveFromCache(true)
                .listener(object : RequestListener<Bitmap> {
                    override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Bitmap>, first: Boolean) = false
                    override fun onResourceReady(resource: Bitmap, model: Any, target: Target<Bitmap>?, dataSource: DataSource, first: Boolean): Boolean {
                        source.set(dataSource); return false
                    }
                })
            // DecodeJob notifies the target BEFORE deferred resource encoding completes.
            // Wait for that write, never permit a source/network reload to satisfy the check.
            val deadline = SystemClock.uptimeMillis() + 10000
            var lastMiss: ExecutionException? = null
            while (SystemClock.uptimeMillis() < deadline) {
                val cached = request.clone().submit()
                try {
                    val remaining = (deadline - SystemClock.uptimeMillis()).coerceAtLeast(1)
                    assertEquals(Color.RED, cached.get(remaining, TimeUnit.MILLISECONDS).getPixel(8, 8))
                    assertEquals(DataSource.RESOURCE_DISK_CACHE, source.get())
                    return
                } catch (miss: ExecutionException) { lastMiss = miss }
                finally { manager.clear(cached) }
                SystemClock.sleep(50)
            }
            throw AssertionError("Deferred disk encoding must finish within 10 seconds", lastMiss)
        } finally { scenario.close() }
    }
}
