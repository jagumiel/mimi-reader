package com.emogoth.android.phone.mimi.util;

import com.mimireader.chanlib.models.ChanPost;

import java.util.Locale;

/** Validates and normalizes the fields required to build remote media URLs. */
public final class MediaContract {
    private MediaContract() {
    }

    public static boolean hasRemoteMedia(ChanPost post) {
        return post != null
                && post.getNo() > 0L
                && !isBlank(post.getTim())
                && !normalizedExtension(post.getExt()).isEmpty();
    }

    public static String normalizedExtension(String extension) {
        if (isBlank(extension)) {
            return "";
        }

        String normalized = extension.trim().toLowerCase(Locale.ROOT);
        if (!normalized.startsWith(".")) {
            normalized = "." + normalized;
        }
        return normalized.length() > 1 ? normalized : "";
    }

    public static String originalFilename(ChanPost post) {
        if (post == null) {
            return "media";
        }
        return isBlank(post.getFilename()) ? post.getTim() : post.getFilename().trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
