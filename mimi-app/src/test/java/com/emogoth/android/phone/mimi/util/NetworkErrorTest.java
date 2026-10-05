package com.emogoth.android.phone.mimi.util;

import com.google.gson.JsonSyntaxException;

import org.junit.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLHandshakeException;

import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.HttpException;
import retrofit2.Response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NetworkErrorTest {
    @Test
    public void classifiesHttpFailuresAndRetryability() {
        assertError(httpError(404), NetworkError.Kind.NOT_FOUND, 404, false);
        assertError(httpError(408), NetworkError.Kind.TIMEOUT, 408, true);
        assertError(httpError(429), NetworkError.Kind.RATE_LIMITED, 429, true);
        assertError(httpError(400), NetworkError.Kind.CLIENT, 400, false);
        assertError(httpError(503), NetworkError.Kind.SERVER, 503, true);
    }

    @Test
    public void classifiesTransportAndParsingFailures() {
        assertError(new SocketTimeoutException(), NetworkError.Kind.TIMEOUT, 0, true);
        assertError(new UnknownHostException(), NetworkError.Kind.CONNECTION, 0, true);
        assertError(new IOException(), NetworkError.Kind.CONNECTION, 0, true);
        assertError(new SSLHandshakeException("certificate"), NetworkError.Kind.TLS, 0, false);
        assertError(new JsonSyntaxException("truncated"), NetworkError.Kind.INVALID_RESPONSE, 0, true);
        assertError(new InvalidApiResponseException("missing posts"),
                NetworkError.Kind.INVALID_RESPONSE, 0, true);
    }

    @Test
    public void findsARecognizedFailureInsideAWrappedException() {
        NetworkError error = NetworkError.from(
                new IllegalStateException("wrapped", new SocketTimeoutException("slow")));

        assertEquals(NetworkError.Kind.TIMEOUT, error.getKind());
        assertTrue(error.isRetryable());
    }

    private static HttpException httpError(int status) {
        ResponseBody body = ResponseBody.create(MediaType.parse("text/plain"), "error");
        return new HttpException(Response.error(status, body));
    }

    private static void assertError(Throwable throwable,
                                    NetworkError.Kind kind,
                                    int status,
                                    boolean retryable) {
        NetworkError error = NetworkError.from(throwable);
        assertEquals(kind, error.getKind());
        assertEquals(status, error.getHttpStatus());
        if (retryable) {
            assertTrue(error.isRetryable());
        } else {
            assertFalse(error.isRetryable());
        }
    }
}
