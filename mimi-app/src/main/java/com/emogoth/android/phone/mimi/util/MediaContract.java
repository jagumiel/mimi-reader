package com.emogoth.android.phone.mimi.util;

import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ArchivedChanPost;
import okhttp3.HttpUrl;
import org.jsoup.parser.Parser;

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

    public static boolean isFlash(ChanPost post) {
        return post != null && ".swf".equals(normalizedExtension(post.getExt()));
    }

    /** /f/ stores SWF files under their original name, unlike other board attachments. */
    public static String mediaUrl(String baseUrl, String board, ChanPost post) {
        if (post instanceof ArchivedChanPost) {
            String link = ((ArchivedChanPost) post).mediaLink;
            if (!isBlank(link)) return link;
        }
        String name = "f".equals(board) && isFlash(post)
                ? Parser.unescapeEntities(originalFilename(post), false)
                : post.getTim();
        return HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment(board)
                .addPathSegment(name + normalizedExtension(post.getExt()))
                .build().toString();
    }

    /** An empty URL means the board supplies no thumbnail; callers show a local media icon. */
    public static String thumbnailUrl(String baseUrl, String board, ChanPost post) {
        if (post instanceof ArchivedChanPost) {
            String link = ((ArchivedChanPost) post).thumbLink;
            if (!isBlank(link)) return link;
        }
        if ("f".equals(board) && isFlash(post)) return "";
        return HttpUrl.get(baseUrl).newBuilder()
                .addPathSegment(board)
                .addPathSegment(post.getTim() + "s.jpg")
                .build().toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
