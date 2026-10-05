package com.emogoth.android.phone.mimi.fourchan;

import com.emogoth.android.phone.mimi.fourchan.models.FourChanPost;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThread;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThreadPage;
import com.emogoth.android.phone.mimi.util.InvalidApiResponseException;

import java.util.ArrayList;
import java.util.List;

/** Keeps usable entries from partial responses and rejects responses with no usable content. */
final class FourChanResponseValidator {
    private FourChanResponseValidator() {
    }

    static List<FourChanPost> requireThreadPosts(FourChanThread thread, long expectedThreadId)
            throws InvalidApiResponseException {
        List<FourChanPost> posts = new ArrayList<>();
        List<FourChanPost> candidates = thread == null ? null : thread.getPosts();
        if (candidates == null || candidates.isEmpty()
                || candidates.get(0) == null
                || candidates.get(0).getNo() != expectedThreadId) {
            throw new InvalidApiResponseException("Thread response does not contain the expected opening post");
        }
        addValidPosts(posts, candidates);
        return posts;
    }

    static List<FourChanPost> requireCatalogPosts(List<FourChanThreadPage> pages)
            throws InvalidApiResponseException {
        List<FourChanPost> posts = new ArrayList<>();
        if (pages != null) {
            for (FourChanThreadPage page : pages) {
                if (page != null) {
                    addValidPosts(posts, page.getThreads());
                }
            }
        }
        if (posts.isEmpty()) {
            throw new InvalidApiResponseException("Catalog response contains no usable threads");
        }
        return posts;
    }

    private static void addValidPosts(List<FourChanPost> destination,
                                      List<FourChanPost> candidates) {
        if (candidates == null) {
            return;
        }
        for (FourChanPost post : candidates) {
            if (post != null && post.getNo() > 0) {
                destination.add(post);
            }
        }
    }
}
