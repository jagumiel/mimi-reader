/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.emogoth.android.phone.mimi.fourchan.PostingProtocol;
import com.emogoth.android.phone.mimi.util.HttpClientFactory;
import com.emogoth.android.phone.mimi.util.MimiUtil;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

/** Dedicated unprivileged renderer; never exposes an Android JS bridge. */
public class CaptchaWebView extends MimiWebView {
    private static final String DOCUMENT_URL = PostingProtocol.CAPTCHA_ORIGIN + "__mimi_captcha__";
    private volatile byte[] document = new byte[0];
    public CaptchaWebView(Context context) { super(context); setup(); }
    public CaptchaWebView(Context context, AttributeSet attrs) { super(context, attrs); setup(); }
    public CaptchaWebView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); setup(); }
    @SuppressLint("SetJavaScriptEnabled") private void setup() {
        getSettings().setJavaScriptEnabled(true); getSettings().setDomStorageEnabled(true);
        getSettings().setAllowFileAccess(false); getSettings().setAllowContentAccess(false);
        getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true);
        setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return request.isForMainFrame() || !PostingProtocol.allowedCaptchaResource(request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return !PostingProtocol.allowedCaptchaResource(url); }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return interceptCaptchaRequest(request);
            }
        });
    }
    /** Serve only our own document; the synthetic board URL never reaches the network. */
    protected WebResourceResponse interceptCaptchaRequest(WebResourceRequest request) {
        if (request.isForMainFrame() && DOCUMENT_URL.equals(request.getUrl().toString())) {
            return new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(document));
        }
        if (!PostingProtocol.allowedCaptchaResource(request.getUrl().toString())) {
            return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", null, new ByteArrayInputStream(new byte[0]));
        }
        return null;
    }
    public void loadCaptchaPage(String board, long thread) {
        PostingProtocol.board(board);
        String existing = CookieManager.getInstance().getCookie(PostingProtocol.SYSTEM_ORIGIN);
        for (Cookie cookie : HttpClientFactory.getInstance().getClient().cookieJar().loadForRequest(HttpUrl.get(PostingProtocol.SYSTEM_ORIGIN))) {
            boolean present = false;
            if (existing != null) for (String pair : existing.split(";")) {
                if (pair.trim().startsWith(cookie.name() + "=")) { present = true; break; }
            }
            if (!present) CookieManager.getInstance().setCookie(PostingProtocol.SYSTEM_ORIGIN, cookie.toString());
        }
        try (InputStream input = getContext().getAssets().open("captcha/index.html"); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) != -1) bytes.write(buffer, 0, count);
            String html = bytes.toString("UTF-8").replace("__BOARD__", "\"" + board + "\"").replace("__THREAD__", Long.toString(Math.max(0, thread)))
                    .replace("__DARK__", Boolean.toString(MimiUtil.getInstance().getTheme() != MimiUtil.THEME_LIGHT));
            document = html.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception error) {
            document = "<p>Could not load CAPTCHA. Please retry.</p>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        }
        loadUrl(DOCUMENT_URL);
    }
    public void readAnswer(ValueCallback<String> callback) {
        evaluateBounded("JSON.stringify(window.mimiCaptchaSnapshot ? window.mimiCaptchaSnapshot() : null)", callback);
    }

    /** Read readiness only; do not copy challenge/answer data until explicit send. */
    public void readStatus(ValueCallback<String> callback) {
        evaluateBounded("window.mimiCaptchaSnapshot ? window.mimiCaptchaSnapshot().state : 'pending'", callback);
    }

    private void evaluateBounded(String script, ValueCallback<String> callback) {
        java.util.concurrent.atomic.AtomicBoolean delivered = new java.util.concurrent.atomic.AtomicBoolean();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (delivered.compareAndSet(false, true)) callback.onReceiveValue("null");
        }, 3000);
        evaluateJavascript(script, result -> {
            if (delivered.compareAndSet(false, true)) callback.onReceiveValue(result);
        });
    }
}
