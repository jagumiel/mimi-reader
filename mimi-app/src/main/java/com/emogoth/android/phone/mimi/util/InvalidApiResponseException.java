package com.emogoth.android.phone.mimi.util;

import java.io.IOException;

/** Indicates that a successful HTTP response did not contain usable API data. */
public final class InvalidApiResponseException extends IOException {
    public InvalidApiResponseException(String message) {
        super(message);
    }
}
