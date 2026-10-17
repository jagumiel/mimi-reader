package com.emogoth.android.phone.mimi.view.gallery

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.view.MimiWebView
import com.emogoth.android.phone.mimi.viewmodel.GalleryViewModel
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

@SuppressLint("ViewConstructor", "SetJavaScriptEnabled")
class FlashPage(context: Context, viewModel: GalleryViewModel) : GalleryPage(context, viewModel) {
    private val contentLoader = FlashContentLoader(context.applicationContext)
    private var hostResumed = true
    private var released = false

    val view = MimiWebView(context)

    init {
        configureWebView()
        addMainChildView(view, closeGalleryOnClick = false)
    }

    override fun onViewBind() {
        contentLoader.swfFile = null
        if (!released) {
            view.stopLoading()
        }
    }

    override fun onComplete() {
        super.onComplete()
        if (!isAttachedToWindow || released) {
            return
        }

        val file = downloadItem.file
        if (!file.isFile || !file.name.endsWith(".swf", ignoreCase = true)) {
            showLoadError()
            return
        }

        contentLoader.swfFile = file
        loaded = false
        view.loadUrl(FlashContentLoader.PLAYER_URL)
    }

    override fun onPageSelectedChange(selected: Boolean) {
        super.onPageSelectedChange(selected)
        syncPlaybackState()
    }

    override fun onHostPause() {
        hostResumed = false
        setPlayerActive(false)
        view.onPause()
    }

    override fun onHostResume() {
        hostResumed = true
        view.onResume()
        syncPlaybackState()
    }

    override fun onDetachedFromWindow() {
        setPlayerActive(false)
        super.onDetachedFromWindow()
    }

    fun release() {
        if (released) return
        released = true
        contentLoader.swfFile = null
        view.stopLoading()
        view.clearHistory()
        removeView(view)
        view.removeAllViews()
        view.destroy()
    }

    private fun configureWebView() {
        view.setBackgroundColor(Color.BLACK)
        view.settings.apply {
            javaScriptEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            domStorageEnabled = false
            mediaPlaybackRequiresUserGesture = false
            loadsImagesAutomatically = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
        view.webViewClient = FlashWebViewClient(
                contentLoader,
                onPageReady = {
                    loaded = true
                    notifyContentReady()
                    syncPlaybackState()
                },
                onLoadError = { showLoadError() },
                onRendererGone = {
                    released = true
                    removeView(view)
                    view.destroy()
                    showLoadError()
                }
        )
    }

    private fun syncPlaybackState() {
        setPlayerActive(pageSelected && hostResumed && loaded)
    }

    private fun setPlayerActive(active: Boolean) {
        if (released) return
        view.evaluateJavascript(
                "window.mimiRuffleShouldRun=${if (active) "true" else "false"};" +
                        "if(window.mimiSetRuffleActive){window.mimiSetRuffleActive(window.mimiRuffleShouldRun);}",
                null
        )
    }

    private fun showLoadError() {
        Log.w(LOG_TAG, "Unable to load Flash content")
        if (isAttachedToWindow) {
            Snackbar.make(this, R.string.could_not_load_flash, Snackbar.LENGTH_LONG).show()
        }
    }
}

internal class FlashWebViewClient(
        private val contentLoader: FlashContentLoader,
        private val onPageReady: () -> Unit,
        private val onLoadError: () -> Unit,
        private val onRendererGone: () -> Unit
) : WebViewClient() {
    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
        return contentLoader.shouldInterceptRequest(request.url)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return !contentLoader.isAllowedNavigation(request.url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (url == FlashContentLoader.PLAYER_URL) {
            onPageReady()
        }
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
        if (request.isForMainFrame) {
            onLoadError()
        }
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone()
        return true
    }
}

internal class FlashContentLoader(context: Context) {
    companion object {
        const val PLAYER_URL = "https://appassets.androidplatform.net/ruffle/player.html"
        private const val HOST = "appassets.androidplatform.net"

        private val RUFFLE_ASSETS = setOf(
                "player.html",
                "player.css",
                "player.js",
                "ruffle.js",
                "core.ruffle.16d61072ac7fdc4d5013.js",
                "core.ruffle.994eeef0a471a88cd0e3.js",
                "172de42e6619eac8f371.wasm",
                "878d548199cddef07035.wasm"
        )
    }

    @Volatile
    var swfFile: File? = null

    private val assetManager = context.assets
    private val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/ruffle/") { path -> serveRuffleAsset(path) }
            .addPathHandler("/movie/") { path -> serveMovie(path) }
            .build()

    fun shouldInterceptRequest(uri: Uri): WebResourceResponse {
        if (uri.scheme != "https" || uri.host != HOST || uri.port != -1 || uri.userInfo != null) {
            return response(403, "Forbidden")
        }

        return assetLoader.shouldInterceptRequest(uri) ?: response(404, "Not Found")
    }

    fun isAllowedNavigation(uri: Uri): Boolean {
        return uri.toString() == PLAYER_URL || uri.toString() == "about:blank"
    }

    private fun serveRuffleAsset(path: String): WebResourceResponse {
        if (path !in RUFFLE_ASSETS) {
            return response(404, "Not Found")
        }

        return try {
            val headers = mutableMapOf(
                    "Cache-Control" to "no-store",
                    "X-Content-Type-Options" to "nosniff"
            )
            if (path == "player.html") {
                headers["Content-Security-Policy"] =
                        "default-src 'none'; script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval'; " +
                                "style-src 'self'; connect-src 'self'; img-src 'self' data:; " +
                                "media-src 'self'; worker-src 'self' blob:"
            }
            WebResourceResponse(
                    mimeType(path),
                    if (path.endsWith(".wasm")) null else "UTF-8",
                    200,
                    "OK",
                    headers,
                    assetManager.open("ruffle/$path")
            )
        } catch (error: Exception) {
            Log.e(GalleryPage.LOG_TAG, "Unable to serve bundled Ruffle asset: $path", error)
            response(404, "Not Found")
        }
    }

    private fun serveMovie(path: String): WebResourceResponse {
        val file = swfFile
        if (path != "current.swf" || file == null || !file.isFile ||
                !file.name.endsWith(".swf", ignoreCase = true)) {
            return response(404, "Not Found")
        }

        return try {
            WebResourceResponse(
                    "application/x-shockwave-flash",
                    null,
                    200,
                    "OK",
                    mapOf(
                            "Cache-Control" to "no-store",
                            "X-Content-Type-Options" to "nosniff"
                    ),
                    FileInputStream(file)
            )
        } catch (error: Exception) {
            Log.e(GalleryPage.LOG_TAG, "Unable to serve cached SWF", error)
            response(404, "Not Found")
        }
    }

    private fun mimeType(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".wasm") -> "application/wasm"
        else -> "application/octet-stream"
    }

    private fun response(status: Int, reason: String) = WebResourceResponse(
            "text/plain",
            "UTF-8",
            status,
            reason,
            mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(ByteArray(0))
    )
}
