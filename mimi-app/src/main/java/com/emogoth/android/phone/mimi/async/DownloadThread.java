package com.emogoth.android.phone.mimi.async;


import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.emogoth.android.phone.mimi.util.HttpClientFactory;
;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.internal.Util;
import okio.Buffer;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public class DownloadThread extends Thread {
    public static final int IO_BUFFER_SIZE = 4 * 1024;
    private static final String LOG_TAG = DownloadThread.class.getSimpleName();

    private final File saveDir;
    private final String url;
    private final long bufferSize;

    private ProgressListener progressListener;
    private ErrorListener errorListener;
    private boolean cancelled;

    public DownloadThread(final File saveDir, final String url, final long bufferSize) {
        this.saveDir = saveDir;
        this.url = url;
        this.cancelled = false;
        this.bufferSize = bufferSize;
    }

    @Override
    public void run() {
        super.run();

        try {
            if (saveDir.exists() && saveDir.length() > 0) {
                downloadComplete(saveDir.getAbsolutePath());
            } else {
                final boolean isError = !download(saveDir, url, bufferSize);
                if (!isError) {
                    downloadComplete(saveDir.getAbsolutePath());
                } else {
                    downloadError();
                }
            }
        } catch (final Exception e) {
            Log.e(LOG_TAG, "Error downloading: url=" + url, e);
        }

    }

    public void cancel() {
        cancelled = true;
    }

    private boolean download(final File saveDir, final String url, long bufferSize) {
        BufferedSink sink = null;
        BufferedSource source = null;

        boolean useGzip = !url.endsWith("pdf");

        long downloaded = 0;
        long target = 0;

        try {
            OkHttpClient httpClient = HttpClientFactory.getInstance().getDownloadClient();
            Request.Builder requestBuilder = new Request.Builder();
            requestBuilder.url(url)
                    .tag(url)
                    .get();

            if (useGzip) {
                requestBuilder.addHeader("Accept-Encoding", "gzip");
            }

            Request request = requestBuilder.build();
            Response response = httpClient.newCall(request).execute();
            ResponseBody body = response.body();
            if (response.code() == 200) {

                source = body.source();
                sink = Okio.buffer(Okio.sink(saveDir));
                Buffer sinkBuffer = sink.buffer();

                target = body.contentLength();
                long read;

                downloadProgress(0);
                while ((read = source.read(sinkBuffer, bufferSize)) != -1) {
                    sink.emit();
                    downloaded += read;
                    downloadProgress(Math.round(downloaded / (float) target * 100));
                    if (cancelled) {
                        read = -1;
                        if (saveDir.exists()) {
                            saveDir.delete();
                        }
                    }
                }

            }
        } catch (IOException e) {
            Log.e(LOG_TAG, "Download error!", e);
            return false;
        } finally {
            Util.closeQuietly(source);
            Util.closeQuietly(sink);
        }

        return downloaded > 0 && ((target > 0 && downloaded == target) || !useGzip);
    }

    private void downloadProgress(final int progress) {
        if (progressListener != null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> progressListener.onProgressUpdate(progress));
        }
    }

    private void downloadComplete(final String downloadFile) {
        if (progressListener != null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> progressListener.onComplete(downloadFile));
        }
    }

    private void downloadError() {
        if (errorListener != null) {
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> errorListener.onError());
        }
    }

    public void setProgressListener(final ProgressListener listener) {
        this.progressListener = listener;
    }

    public void setErrorListener(final ErrorListener listener) {
        this.errorListener = listener;
    }

    public interface ProgressListener {
        void onProgressUpdate(final int progress);

        void onComplete(final String filePath);
    }

    public interface ErrorListener {
        void onError();
    }
}
