package com.emogoth.android.phone.mimi.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

public final class VideoPlaybackError {
    public enum Kind {
        UNSUPPORTED_FORMAT,
        CORRUPT_OR_INCOMPLETE,
        GENERIC
    }

    private VideoPlaybackError() {
    }

    @NonNull
    public static Kind classify(@Nullable String errorCodeName) {
        if (errorCodeName == null) {
            return Kind.GENERIC;
        }

        String code = errorCodeName.toUpperCase(Locale.ROOT);
        if (code.contains("PARSING") || code.contains("MALFORMED")
                || code.contains("READ_POSITION_OUT_OF_RANGE") || code.contains("IO_BAD_HTTP_STATUS")) {
            return Kind.CORRUPT_OR_INCOMPLETE;
        }
        if (code.contains("UNSUPPORTED") || code.contains("DECODER_INIT")) {
            return Kind.UNSUPPORTED_FORMAT;
        }
        return Kind.GENERIC;
    }
}
