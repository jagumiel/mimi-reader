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

package com.emogoth.android.phone.mimi.util;


import android.content.SharedPreferences;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.emogoth.android.phone.mimi.BuildConfig;
import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.app.MimiApplication;
import com.franmontiel.persistentcookiejar.PersistentCookieJar;
import com.franmontiel.persistentcookiejar.cache.SetCookieCache;
import com.franmontiel.persistentcookiejar.persistence.CookiePersistor;
import com.franmontiel.persistentcookiejar.persistence.SharedPrefsCookiePersistor;

import java.io.IOException;
import java.io.File;
import java.net.Socket;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.net.SocketFactory;

import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;

public class HttpClientFactory {
    private static final String LOG_TAG = HttpClientFactory.class.getSimpleName();
    private static final int MAX_CACHE_SIZE = 50 * 1024 * 1024;
    static final String HTTP_CACHE_DIRECTORY = "http_cache";

    private static final int API_CONNECT_TIMEOUT_SECONDS = 20;
    private static final int API_READ_TIMEOUT_SECONDS = 30;
    private static final int API_CALL_TIMEOUT_SECONDS = 60;
    private static final int DOWNLOAD_CONNECT_TIMEOUT_SECONDS = 30;
    private static final int DOWNLOAD_IO_TIMEOUT_SECONDS = 90;

    private static final HttpClientFactory ourInstance = new HttpClientFactory();
    private OkHttpClient apiClient;
    private OkHttpClient downloadClient;
    private SharedPrefsCookiePersistor cookiePersistor;
    private final ApiRequestLimiter apiRequestLimiter = new ApiRequestLimiter();

    private String defaultUserAgent;
    private String archiveUserAgent;

    public enum ClientType {
        API, DOWNLOAD
    }

    public static HttpClientFactory getInstance() {
        return ourInstance;
    }

    private HttpClientFactory() {
        defaultUserAgent = System.getProperty("http.agent");
        Random r = new Random(System.nanoTime());
        switch(r.nextInt(3)) {
            case 0:
                archiveUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36";
                break;
            case 1:
                archiveUserAgent = "Mozilla/5.0 (X11; Ubuntu; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/55.0.2919.83 Safari/537.36";
                break;
            case 2:
            default:
                archiveUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/79.0.3945.117 Safari/537.36";
        }

    }

    private void init() {
        final File cacheDirectory = new File(
                MimiUtil.getInstance().getCacheDir(),
                HTTP_CACHE_DIRECTORY
        );
        final Cache cache = new Cache(cacheDirectory, MAX_CACHE_SIZE);

        cookiePersistor = new SharedPrefsCookiePersistor(MimiApplication.getInstance());
        PersistentCookieJar jar = new PersistentCookieJar(new SetCookieCache(), cookiePersistor);

        final OkHttpClient.Builder apiBuilder = commonBuilder(jar)
                .cache(cache)
                .connectTimeout(API_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(API_READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(API_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .addNetworkInterceptor(apiRequestLimiter);

        final OkHttpClient.Builder downloadBuilder = commonBuilder(jar)
                .connectTimeout(DOWNLOAD_CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(DOWNLOAD_IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(DOWNLOAD_IO_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .callTimeout(0, TimeUnit.SECONDS);

//        try {
//            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
//            builder.sslSocketFactory(sslSocketFactory, new MyX509TrustManager());
//        } catch (NoSuchAlgorithmException | KeyStoreException e) {
//            Log.e(LOG_TAG, "Could not enable SSL", e);
//            Log.e(LOG_TAG, "Caught exception", e);
//        }

        final int bufferSize = configuredBufferSize();
        if (bufferSize > 0) {
            apiBuilder.socketFactory(bufferedSocketFactory(bufferSize));
            downloadBuilder.socketFactory(bufferedSocketFactory(bufferSize));
        }

        apiClient = apiBuilder.build();
        downloadClient = downloadBuilder.build();
    }

    private OkHttpClient.Builder commonBuilder(PersistentCookieJar jar) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .cookieJar(jar)
                .retryOnConnectionFailure(false)
                .addNetworkInterceptor(headerInterceptor());

        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(loggingInterceptor());
        }
        return builder;
    }

    private int configuredBufferSize() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(MimiApplication.getInstance());
        String prefsKey = MimiApplication.getInstance().getString(R.string.http_buffer_size_pref);
        String defaultValue = MimiApplication.getInstance().getString(R.string.http_buffer_size_default);
        return Integer.parseInt(prefs.getString(prefsKey, defaultValue));
    }

    private SocketFactory bufferedSocketFactory(final int bufferSize) {
        return new DelegatingSocketFactory(SocketFactory.getDefault()) {
            @Override
            protected Socket configureSocket(Socket socket) throws IOException {
                socket.setSendBufferSize(bufferSize);
                socket.setReceiveBufferSize(bufferSize);
                return socket;
            }
        };
    }

    private Interceptor headerInterceptor() {
        return chain -> {
            Request request = chain.request();
            Request.Builder newRequestBuilder = request.newBuilder();

            final String userAgent;
            if (!chain.request().url().host().endsWith("thebarchive.com") && !chain.request().url().host().endsWith("archived.moe")) {
                userAgent = defaultUserAgent;
            } else {
                userAgent = archiveUserAgent;
            }

            newRequestBuilder.header("User-Agent", userAgent);

            return chain.proceed(newRequestBuilder.build());
        };
    }

    private Interceptor loggingInterceptor() {
        return chain -> {
            Request request = chain.request();
            Log.d(LOG_TAG, "[Secure] " + (chain.connection().socket() instanceof javax.net.ssl.SSLSocket));
            Log.d(LOG_TAG, "[Request] " + request.method() + " "
                    + request.url().host() + request.url().encodedPath());
            return chain.proceed(request);
        };
    }

    synchronized CookiePersistor getCookieStore() {
        if (cookiePersistor == null) {
            init();
        }
        return cookiePersistor;
    }

    public synchronized OkHttpClient getClient() {
        return getClient(ClientType.API);
    }

    public synchronized OkHttpClient getDownloadClient() {
        return getClient(ClientType.DOWNLOAD);
    }

    /** CAPTCHA cookies apply only to the exact posting host, with no automatic redirects. */
    public OkHttpClient getPostingClient(String userAgent, String webCookies, String referer) {
        OkHttpClient base = getClient();
        okhttp3.CookieJar jar = base.cookieJar();
        java.util.List<okhttp3.Cookie> browserCookies = new java.util.ArrayList<>();
        if (webCookies != null) for (String pair : webCookies.split(";")) {
            int equal = pair.indexOf('=');
            if (equal <= 0) continue;
            String name = pair.substring(0, equal).trim();
            // Pass authentication belongs to the native login flow. Never restore a
            // stale browser login or override the app's current Pass session.
            if (name.startsWith("pass_")) continue;
            try {
                browserCookies.add(new okhttp3.Cookie.Builder().name(name)
                        .value(pair.substring(equal + 1).trim()).hostOnlyDomain("sys.4chan.org").path("/").secure().build());
            } catch (IllegalArgumentException ignored) { }
        }
        return base.newBuilder().followRedirects(false).followSslRedirects(false)
                .cookieJar(new okhttp3.CookieJar() {
                    @Override public void saveFromResponse(okhttp3.HttpUrl url, java.util.List<okhttp3.Cookie> cookies) { jar.saveFromResponse(url, cookies); }
                    @Override public java.util.List<okhttp3.Cookie> loadForRequest(okhttp3.HttpUrl url) {
                        java.util.Map<String, okhttp3.Cookie> cookies = new java.util.LinkedHashMap<>();
                        for (okhttp3.Cookie cookie : jar.loadForRequest(url)) cookies.put(cookie.name(), cookie);
                        if (url.isHttps() && "sys.4chan.org".equals(url.host())) {
                            for (okhttp3.Cookie cookie : browserCookies) cookies.put(cookie.name(), cookie);
                        }
                        return new java.util.ArrayList<>(cookies.values());
                    }
                }).addInterceptor(chain -> {
                    Request request = chain.request();
                    if (!request.url().isHttps() || request.url().port() != 443 || !"sys.4chan.org".equals(request.url().host())
                            || !request.url().encodedPath().matches("/[a-z0-9]{1,16}/post")) throw new IOException("Invalid posting destination");
                    return chain.proceed(request.newBuilder().header("User-Agent", userAgent)
                            .header("Origin", "https://boards.4chan.org").header("Referer", referer).build());
                }).addNetworkInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", userAgent).build())).build();
    }

    public synchronized OkHttpClient getClient(ClientType type) {
        if (apiClient == null || downloadClient == null) {
            init();
        }
        return type == ClientType.DOWNLOAD ? downloadClient : apiClient;
    }

    public synchronized void reset() {
        apiClient = null;
        downloadClient = null;
        cookiePersistor = null;
    }

    public synchronized void clearCache() throws IOException {
        final Cache cache = getClient().cache();
        if (cache != null) {
            cache.evictAll();
        }
    }
}
