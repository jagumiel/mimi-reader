package com.emogoth.android.phone.mimi.view.gallery

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Base64
import android.view.TextureView
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.activity.TabsActivity
import com.emogoth.android.phone.mimi.util.MediaPlayerHelper
import com.emogoth.android.phone.mimi.view.MimiWebView
import com.emogoth.android.phone.mimi.viewmodel.GalleryItem
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import pl.droidsonroids.gif.GifDrawable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class GalleryPlaybackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val preferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val autoplayKey = context.getString(R.string.gallery_autoplay_pref)
    private var hadAutoplayPreference = false
    private var originalAutoplayPreference = false

    @Before
    fun disablePersistentAutoplay() {
        hadAutoplayPreference = preferences.contains(autoplayKey)
        originalAutoplayPreference = preferences.getBoolean(autoplayKey, false)
        preferences.edit().putBoolean(autoplayKey, false).commit()
    }

    @After
    fun restorePersistentAutoplay() {
        val editor = preferences.edit()
        if (hadAutoplayPreference) {
            editor.putBoolean(autoplayKey, originalAutoplayPreference)
        } else {
            editor.remove(autoplayKey)
        }
        editor.commit()
    }

    @Test
    fun autoplayAdvancesImageGifAndVideoWithoutWrapping() {
        val scenario = launchHostActivity()
        lateinit var galleryPager: GalleryPager

        try {
            scenario.onActivity { activity ->
                prepareHostWindow(activity)
                galleryPager = GalleryPager(activity)
                galleryPager.setViewModel(GalleryViewModel.empty())
                galleryPager.setItems(galleryItems())
                activity.addContentView(
                        galleryPager,
                        FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                        )
                )
                galleryPager.setAutoplayEnabled(true)
                galleryPager.resume()

                val adapter = galleryPager.findViewById<RecyclerView>(R.id.pager)
                        .adapter as GalleryPagerAdapter
                assertEquals(GalleryPagerAdapter.ItemType.IMAGE.value, adapter.getItemViewType(0))
                assertEquals(GalleryPagerAdapter.ItemType.GIF.value, adapter.getItemViewType(1))
                assertEquals(GalleryPagerAdapter.ItemType.VIDEO.value, adapter.getItemViewType(2))

                val flashAdapter = GalleryPagerAdapter(
                        listOf(galleryItem(4L, ".swf")),
                        GalleryViewModel.empty(),
                        null
                ) { _, _ -> }
                assertEquals(GalleryPagerAdapter.ItemType.FLASH.value, flashAdapter.getItemViewType(0))
            }

            waitUntil(LAYOUT_TIMEOUT_MS) {
                var laidOut = false
                scenario.onActivity {
                    val recycler = galleryPager.findViewById<RecyclerView>(R.id.pager)
                    laidOut = recycler.width > 0 &&
                            recycler.findViewHolderForAdapterPosition(0) != null
                }
                laidOut
            }
            instrumentation.waitForIdleSync()
            SystemClock.sleep(SNAP_SETTLE_DELAY_MS)
            scenario.onActivity {
                galleryPager.setAutoplayEnabled(true)
                galleryPager.resume()
                assertTrue(galleryPager.autoplayEnabled)
                galleryPager.onPageEvent(0, GalleryPageEvent.CONTENT_READY)
            }

            waitForPosition(scenario, galleryPager, 1, IMAGE_TIMEOUT_MS)

            scenario.onActivity {
                galleryPager.onPageEvent(1, GalleryPageEvent.PLAYBACK_COMPLETED)
            }
            waitForPosition(scenario, galleryPager, 2, TRANSITION_TIMEOUT_MS)

            scenario.onActivity {
                galleryPager.onPageEvent(2, GalleryPageEvent.PLAYBACK_COMPLETED)
            }
            SystemClock.sleep(300L)
            scenario.onActivity { assertEquals(2, galleryPager.position) }
        } finally {
            scenario.onActivity {
                galleryPager.release()
                (galleryPager.parent as? ViewGroup)?.removeView(galleryPager)
            }
            scenario.close()
        }
    }

    @Test
    fun localMp4PlaysToCompletionWithMedia3() {
        val mediaFile = writeFixture("gallery-playback", ".mp4", MP4_BASE64)
        val scenario = launchHostActivity()
        val completed = CountDownLatch(1)
        val playbackError = AtomicReference<PlaybackException?>()
        lateinit var textureView: TextureView
        lateinit var player: MediaPlayerHelper

        try {
            scenario.onActivity { activity ->
                prepareHostWindow(activity)
                textureView = TextureView(activity)
                activity.addContentView(textureView, FrameLayout.LayoutParams(64, 64))
                player = MediaPlayerHelper(activity)
                player.setRepeatEnabled(false)
                player.addListener(object : MediaPlayerHelper.Listener {
                    override fun onStateChanged(playWhenReady: Boolean, playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            completed.countDown()
                        }
                    }

                    override fun onPlaybackError(error: PlaybackException) {
                        playbackError.set(error)
                        completed.countDown()
                    }

                    override fun onVideoSizeChanged(
                            width: Int,
                            height: Int,
                            unappliedRotationDegrees: Int,
                            pixelWidthHeightRatio: Float
                    ) = Unit

                    override fun onRenderedFirstFrame() = Unit
                })
            }

            waitUntil(SURFACE_TIMEOUT_MS) { textureView.isAvailable }
            scenario.onActivity {
                player.playVideo(android.net.Uri.fromFile(mediaFile), textureView, true)
            }

            assertTrue("The MP4 did not reach STATE_ENDED", completed.await(PLAYBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS))
            assertNull("Media3 reported an MP4 playback error", playbackError.get())
        } finally {
            scenario.onActivity {
                player.release()
                (textureView.parent as? ViewGroup)?.removeView(textureView)
            }
            scenario.close()
            mediaFile.delete()
        }
    }

    @Test
    fun animatedGifFixtureDecodesMultipleFrames() {
        val gifFile = writeFixture("gallery-animation", ".gif", GIF_BASE64)
        val drawable = GifDrawable(gifFile)

        try {
            assertTrue("The GIF fixture is not animated", drawable.numberOfFrames > 1)
            assertTrue("The GIF fixture has no playback duration", drawable.duration > 0)
        } finally {
            drawable.recycle()
            gifFile.delete()
        }
    }

    @Test
    fun flashViewerServesOnlyItsBundledRuntimeAndCurrentMovie() {
        val swfFile = File.createTempFile("gallery-flash", ".swf", context.cacheDir).apply {
            writeBytes(MINIMAL_SWF)
        }
        val loader = FlashContentLoader(context).apply { this.swfFile = swfFile }

        try {
            val shell = loader.shouldInterceptRequest(Uri.parse(FlashContentLoader.PLAYER_URL))
            assertEquals(200, shell.statusCode)
            assertEquals("text/html", shell.mimeType)
            assertTrue(shell.data.bufferedReader().use { it.readText() }.contains("/ruffle/ruffle.js"))

            val wasm = loader.shouldInterceptRequest(
                    Uri.parse("https://appassets.androidplatform.net/ruffle/172de42e6619eac8f371.wasm")
            )
            assertEquals(200, wasm.statusCode)
            assertEquals("application/wasm", wasm.mimeType)
            wasm.data.close()

            val movie = loader.shouldInterceptRequest(
                    Uri.parse("https://appassets.androidplatform.net/movie/current.swf")
            )
            assertEquals(200, movie.statusCode)
            assertEquals("application/x-shockwave-flash", movie.mimeType)
            assertTrue(movie.data.use { it.readBytes() }.contentEquals(MINIMAL_SWF))

            assertEquals(
                    403,
                    loader.shouldInterceptRequest(Uri.parse("https://example.org/tracker.js")).statusCode
            )
            assertEquals(
                    404,
                    loader.shouldInterceptRequest(
                            Uri.parse("https://appassets.androidplatform.net/ruffle/../captcha/post.html")
                    ).statusCode
            )
            assertFalse(loader.isAllowedNavigation(Uri.parse("https://boards.4chan.org/f/")))
        } finally {
            swfFile.delete()
        }
    }

    @Test
    fun bundledRuffleLoadsAFlashMovieInAndroidWebView() {
        val swfFile = File.createTempFile("gallery-ruffle", ".swf", context.cacheDir).apply {
            writeBytes(MINIMAL_SWF)
        }
        val scenario = launchHostActivity()
        val loader = FlashContentLoader(context).apply { this.swfFile = swfFile }
        val loadError = AtomicReference<String?>()
        lateinit var webView: WebView

        try {
            scenario.onActivity { activity ->
                prepareHostWindow(activity)
                webView = MimiWebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = FlashWebViewClient(
                            loader,
                            onPageReady = {},
                            onLoadError = { loadError.set("page load failed") },
                            onRendererGone = { loadError.set("WebView renderer stopped") }
                    )
                }
                activity.addContentView(webView, FrameLayout.LayoutParams(320, 240))
                webView.loadUrl(FlashContentLoader.PLAYER_URL)
            }

            waitUntil(RUFFLE_TIMEOUT_MS) {
                loadError.get() == null && evaluateBoolean(scenario, webView, "Boolean(window.mimiRuffleLoaded)")
            }
            assertNull(loadError.get())

            assertTrue(evaluateBoolean(scenario, webView, "window.mimiSetRuffleActive(true); true"))
            waitUntil(JS_EVALUATION_TIMEOUT_MS) {
                evaluateBoolean(
                        scenario,
                        webView,
                        "Boolean(document.getElementById('ruffle-player')) && " +
                                "!document.getElementById('ruffle-player').ruffle().suspended"
                )
            }

            assertTrue(evaluateBoolean(scenario, webView, "window.mimiSetRuffleActive(false); true"))
            waitUntil(JS_EVALUATION_TIMEOUT_MS) {
                evaluateBoolean(
                        scenario,
                        webView,
                        "document.getElementById('ruffle-player').ruffle().suspended"
                )
            }
        } finally {
            scenario.onActivity {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.destroy()
            }
            scenario.close()
            swfFile.delete()
        }
    }

    private fun launchHostActivity(): ActivityScenario<TabsActivity> {
        val intent = Intent(context, TabsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return ActivityScenario.launch(intent)
    }

    @Suppress("DEPRECATION")
    private fun prepareHostWindow(activity: TabsActivity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            activity.setShowWhenLocked(true)
            activity.setTurnScreenOn(true)
        } else {
            activity.window.addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    private fun galleryItems(): List<GalleryItem> = listOf(
            galleryItem(1L, ".jpg"),
            galleryItem(2L, ".gif"),
            galleryItem(3L, ".mp4")
    )

    private fun galleryItem(id: Long, extension: String) = GalleryItem(
            id,
            "https://example.invalid/${id}s.jpg",
            "https://example.invalid/$id$extension",
            1,
            16,
            16,
            "test",
            extension,
            "$id$extension",
            "$id$extension"
    )

    private fun waitForPosition(
            scenario: ActivityScenario<TabsActivity>,
            pager: GalleryPager,
            expected: Int,
            timeoutMs: Long
    ) {
        waitUntil(timeoutMs) {
            var matches = false
            scenario.onActivity {
                val recycler = pager.findViewById<RecyclerView>(R.id.pager)
                val layoutManager = recycler.layoutManager as LinearLayoutManager
                matches = pager.position == expected &&
                        recycler.scrollState == RecyclerView.SCROLL_STATE_IDLE &&
                        layoutManager.findFirstCompletelyVisibleItemPosition() == expected
            }
            matches
        }
        scenario.onActivity { assertEquals(expected, pager.position) }
    }

    private fun waitUntil(timeoutMs: Long, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(50L)
        }
        assertTrue("Condition was not met within $timeoutMs ms", condition())
    }

    private fun evaluateBoolean(
            scenario: ActivityScenario<TabsActivity>,
            webView: WebView,
            expression: String
    ): Boolean {
        val result = AtomicReference<String?>()
        val evaluated = CountDownLatch(1)
        scenario.onActivity {
            webView.evaluateJavascript(expression) {
                result.set(it)
                evaluated.countDown()
            }
        }
        return evaluated.await(JS_EVALUATION_TIMEOUT_MS, TimeUnit.MILLISECONDS) && result.get() == "true"
    }

    private fun writeFixture(prefix: String, suffix: String, base64: String): File {
        return File.createTempFile(prefix, suffix, context.cacheDir).apply {
            writeBytes(Base64.decode(base64, Base64.DEFAULT))
        }
    }

    private companion object {
        const val IMAGE_TIMEOUT_MS = 10_000L
        const val LAYOUT_TIMEOUT_MS = 5_000L
        const val SNAP_SETTLE_DELAY_MS = 300L
        const val TRANSITION_TIMEOUT_MS = 5_000L
        const val SURFACE_TIMEOUT_MS = 5_000L
        const val PLAYBACK_TIMEOUT_MS = 8_000L
        const val RUFFLE_TIMEOUT_MS = 20_000L
        const val JS_EVALUATION_TIMEOUT_MS = 2_000L
        val MINIMAL_SWF = byteArrayOf(
                0x46, 0x57, 0x53, 0x06, 0x10, 0x00, 0x00, 0x00,
                0x08, 0x00, 0x00, 0x0C, 0x01, 0x00, 0x00, 0x00
        )
        const val MP4_BASE64 = "AAAAIGZ0eXBpc29tAAACAGlzb21pc28yYXZjMW1wNDEAAAM3bW9vdgAAAGxtdmhkAAAAAAAAAAAAAAAAAAAD6AAAAfQAAQAAAQAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAgAAAmJ0cmFrAAAAXHRraGQAAAADAAAAAAAAAAAAAAABAAAAAAAAAfQAAAAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAABAAAAAAAAAAAAAAAAAABAAAAAAKAAAAB4AAAAAAAkZWR0cwAAABxlbHN0AAAAAAAAAAEAAAH0AAAAAAABAAAAAAHabWRpYQAAACBtZGhkAAAAAAAAAAAAAAAAAAAoAAAAFABVxAAAAAAALWhkbHIAAAAAAAAAAHZpZGUAAAAAAAAAAAAAAABWaWRlb0hhbmRsZXIAAAABhW1pbmYAAAAUdm1oZAAAAAEAAAAAAAAAAAAAACRkaW5mAAAAHGRyZWYAAAAAAAAAAQAAAAx1cmwgAAAAAQAAAUVzdGJsAAAAuXN0c2QAAAAAAAAAAQAAAKlhdmMxAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAKAAeABIAAAASAAAAAAAAAABFUxhdmM2Mi4xNS4xMDAgbGlieDI2NAAAAAAAAAAAAAAAGP//AAAAL2F2Y0MBQsAe/+EAF2dCwB7ZAoR+XARAAAADAEAAAAUDxYuSAQAFaMuDyyAAAAAQcGFzcAAAAAEAAAABAAAAFGJ0cnQAAAAAAAAusAAAAAAAAAAYc3R0cwAAAAAAAAABAAAABQAABAAAAAAUc3RzcwAAAAAAAAABAAAAAQAAABxzdHNjAAAAAAAAAAEAAAABAAAABQAAAAEAAAAoc3RzegAAAAAAAAAAAAAABQAAAsEAAAALAAAACwAAAAoAAAAKAAAAFHN0Y28AAAAAAAAAAQAAA2cAAABhdWR0YQAAAFltZXRhAAAAAAAAACFoZGxyAAAAAAAAAABtZGlyYXBwbAAAAAAAAAAAAAAAACxpbHN0AAAAJKl0b28AAAAcZGF0YQAAAAEAAAAATGF2ZjYyLjUuMTAwAAAACGZyZWUAAALzbWRhdAAAAnEGBf//bdxF6b3m2Ui3lizYINkj7u94MjY0IC0gY29yZSAxNjUgcjMyMjIgYjM1NjA1YSAtIEguMjY0L01QRUctNCBBVkMgY29kZWMgLSBDb3B5bGVmdCAyMDAzLTIwMjUgLSBodHRwOi8vd3d3LnZpZGVvbGFuLm9yZy94MjY0Lmh0bWwgLSBvcHRpb25zOiBjYWJhYz0wIHJlZj0zIGRlYmxvY2s9MTowOjAgYW5hbHlzZT0weDE6MHgxMTEgbWU9aGV4IHN1Ym1lPTcgcHN5PTEgcHN5X3JkPTEuMDA6MC4wMCBtaXhlZF9yZWY9MSBtZV9yYW5nZT0xNiBjaHJvbWFfbWU9MSB0cmVsbGlzPTEgOHg4ZGN0PTAgY3FtPTAgZGVhZHpvbmU9MjEsMTEgZmFzdF9wc2tpcD0xIGNocm9tYV9xcF9vZmZzZXQ9LTIgdGhyZWFkcz00IGxvb2thaGVhZF90aHJlYWRzPTEgc2xpY2VkX3RocmVhZHM9MCBucj0wIGRlY2ltYXRlPTEgaW50ZXJsYWNlZD0wIGJsdXJheV9jb21wYXQ9MCBjb25zdHJhaW5lZF9pbnRyYT0wIGJmcmFtZXM9MCB3ZWlnaHRwPTAga2V5aW50PTI1MCBrZXlpbnRfbWluPTEwIHNjZW5lY3V0PTQwIGludHJhX3JlZnJlc2g9MCByY19sb29rYWhlYWQ9NDAgcmM9Y3JmIG1idHJlZT0xIGNyZj0yMy4wIHFjb21wPTAuNjAgcXBtaW49MCBxcG1heD02OSBxcHN0ZXA9NCBpcF9yYXRpbz0xLjQwIGFxPTE6MS4wMACAAAAASGWIhA/yYoAAw+ycnJycnJycnJ11111111111111111111111111111111111111111111111111111111111111111111114AAAAAdBmjgf4FGAAAAAB0GaVAf4FGAAAAAGQZpgO8CjAAAABkGagDfAow=="
        const val GIF_BASE64 = "R0lGODlhBAAEAPcfMQAAACQAAEgAAGwAAJAAALQAANgAAPwAAAAkACQkAEgkAGwkAJAkALQkANgkAPwkAABIACRIAEhIAGxIAJBIALRIANhIAPxIAABsACRsAEhsAGxsAJBsALRsANhsAPxsAACQACSQAEiQAGyQAJCQALSQANiQAPyQAAC0ACS0AEi0AGy0AJC0ALS0ANi0APy0AADYACTYAEjYAGzYAJDYALTYANjYAPzYAAD8ACT8AEj8AGz8AJD8ALT8ANj8APz8AAAAVSQAVUgAVWwAVZAAVbQAVdgAVfwAVQAkVSQkVUgkVWwkVZAkVbQkVdgkVfwkVQBIVSRIVUhIVWxIVZBIVbRIVdhIVfxIVQBsVSRsVUhsVWxsVZBsVbRsVdhsVfxsVQCQVSSQVUiQVWyQVZCQVbSQVdiQVfyQVQC0VSS0VUi0VWy0VZC0VbS0Vdi0Vfy0VQDYVSTYVUjYVWzYVZDYVbTYVdjYVfzYVQD8VST8VUj8VWz8VZD8VbT8Vdj8Vfz8VQAAqiQAqkgAqmwAqpAAqrQAqtgAqvwAqgAkqiQkqkgkqmwkqpAkqrQkqtgkqvwkqgBIqiRIqkhIqmxIqpBIqrRIqthIqvxIqgBsqiRsqkhsqmxsqpBsqrRsqthsqvxsqgCQqiSQqkiQqmyQqpCQqrSQqtiQqvyQqgC0qiS0qki0qmy0qpC0qrS0qti0qvy0qgDYqiTYqkjYqmzYqpDYqrTYqtjYqvzYqgD8qiT8qkj8qmz8qpD8qrT8qtj8qvz8qgAA/yQA/0gA/2wA/5AA/7QA/9gA//wA/wAk/yQk/0gk/2wk/5Ak/7Qk/9gk//wk/wBI/yRI/0hI/2xI/5BI/7RI/9hI//xI/wBs/yRs/0hs/2xs/5Bs/7Rs/9hs//xs/wCQ/ySQ/0iQ/2yQ/5CQ/7SQ/9iQ//yQ/wC0/yS0/0i0/2y0/5C0/7S0/9i0//y0/wDY/yTY/0jY/2zY/5DY/7TY/9jY//zY/wD8/yT8/0j8/2z8/5D8/7T8/9j8//z8/yH/C05FVFNDQVBFMi4wAwEBAAAh+QQEFAAfACwAAAAABAAEAAAIEQAPHAAGTCBBgQCAGDgABEhAACH5BAUUAAAALAMAAwABAAEAAAgEAAEEBAA7"
    }
}
