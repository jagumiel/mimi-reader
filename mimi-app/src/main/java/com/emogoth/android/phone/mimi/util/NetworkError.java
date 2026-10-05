package com.emogoth.android.phone.mimi.util;

import com.google.gson.JsonParseException;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import javax.net.ssl.SSLException;

import retrofit2.HttpException;

/** A stable, UI-independent classification of network failures. */
public final class NetworkError {
    public enum Kind {
        NOT_FOUND,
        RATE_LIMITED,
        TIMEOUT,
        CONNECTION,
        TLS,
        CLIENT,
        SERVER,
        INVALID_RESPONSE,
        UNKNOWN
    }

    private final Kind kind;
    private final int httpStatus;

    private NetworkError(Kind kind, int httpStatus) {
        this.kind = kind;
        this.httpStatus = httpStatus;
    }

    public static NetworkError from(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof HttpException) {
                return fromHttpStatus(((HttpException) current).code());
            }
        }

        if (hasCause(throwable, SocketTimeoutException.class)) {
            return new NetworkError(Kind.TIMEOUT, 0);
        }
        if (hasCause(throwable, UnknownHostException.class)
                || hasCause(throwable, ConnectException.class)
                || hasCause(throwable, NoRouteToHostException.class)) {
            return new NetworkError(Kind.CONNECTION, 0);
        }
        if (hasCause(throwable, SSLException.class)) {
            return new NetworkError(Kind.TLS, 0);
        }
        if (hasCause(throwable, JsonParseException.class)
                || hasCause(throwable, EOFException.class)) {
            return new NetworkError(Kind.INVALID_RESPONSE, 0);
        }
        if (hasCause(throwable, IOException.class)) {
            return new NetworkError(Kind.CONNECTION, 0);
        }
        return new NetworkError(Kind.UNKNOWN, 0);
    }

    public static NetworkError fromHttpStatus(int status) {
        if (status == 404) {
            return new NetworkError(Kind.NOT_FOUND, status);
        }
        if (status == 408) {
            return new NetworkError(Kind.TIMEOUT, status);
        }
        if (status == 429) {
            return new NetworkError(Kind.RATE_LIMITED, status);
        }
        if (status >= 500 && status <= 599) {
            return new NetworkError(Kind.SERVER, status);
        }
        if (status >= 400 && status <= 499) {
            return new NetworkError(Kind.CLIENT, status);
        }
        return new NetworkError(Kind.UNKNOWN, status);
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
        }
        return false;
    }

    public Kind getKind() {
        return kind;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return kind == Kind.RATE_LIMITED
                || kind == Kind.TIMEOUT
                || kind == Kind.CONNECTION
                || kind == Kind.SERVER
                || kind == Kind.INVALID_RESPONSE;
    }
}
