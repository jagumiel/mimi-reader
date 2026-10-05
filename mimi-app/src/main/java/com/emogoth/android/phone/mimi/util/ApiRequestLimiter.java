package com.emogoth.android.phone.mimi.util;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.Response;

/**
 * Serializes network requests to the read-only 4chan JSON API.
 * Static content and media use other hosts and are not throttled.
 */
public final class ApiRequestLimiter implements Interceptor {
    static final String API_HOST = "a.4cdn.org";
    static final long MIN_REQUEST_INTERVAL_MILLIS = 1_000L;

    private long nextRequestAtMillis;
    private boolean hasReservation;

    @Override
    public Response intercept(Chain chain) throws IOException {
        final long delayMillis = reserveDelayMillis(
                chain.request().url().host(),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime()));

        if (delayMillis > 0L) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                InterruptedIOException interrupted =
                        new InterruptedIOException("Interrupted while waiting for the API rate limit");
                interrupted.initCause(error);
                throw interrupted;
            }
        }

        return chain.proceed(chain.request());
    }

    synchronized long reserveDelayMillis(String host, long nowMillis) {
        if (!API_HOST.equalsIgnoreCase(host)) {
            return 0L;
        }

        if (!hasReservation) {
            hasReservation = true;
            nextRequestAtMillis = nowMillis + MIN_REQUEST_INTERVAL_MILLIS;
            return 0L;
        }

        final long scheduledAt = Math.max(nowMillis, nextRequestAtMillis);
        nextRequestAtMillis = scheduledAt + MIN_REQUEST_INTERVAL_MILLIS;
        return scheduledAt - nowMillis;
    }
}
