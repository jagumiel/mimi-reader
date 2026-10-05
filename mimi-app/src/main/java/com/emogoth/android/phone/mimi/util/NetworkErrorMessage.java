package com.emogoth.android.phone.mimi.util;

import androidx.annotation.StringRes;

import com.emogoth.android.phone.mimi.R;

public final class NetworkErrorMessage {
    private NetworkErrorMessage() {
    }

    @StringRes
    public static int resourceFor(Throwable throwable) {
        switch (NetworkError.from(throwable).getKind()) {
            case NOT_FOUND:
                return R.string.error_404;
            case RATE_LIMITED:
                return R.string.network_error_rate_limited;
            case TIMEOUT:
                return R.string.network_error_timeout;
            case CONNECTION:
                return R.string.network_error_connection;
            case TLS:
                return R.string.network_error_tls;
            case SERVER:
                return R.string.network_error_server;
            case INVALID_RESPONSE:
                return R.string.network_error_invalid_response;
            case CLIENT:
                return R.string.network_error_request;
            case UNKNOWN:
            default:
                return R.string.unknown_error;
        }
    }
}
