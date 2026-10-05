package com.emogoth.android.phone.mimi.viewmodel;

import com.emogoth.android.phone.mimi.db.models.CatalogPost;
import com.mimireader.chanlib.models.ChanPost;

import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class CachedCatalogTest {
    @Test
    public void keepsBoardPostsAndRefreshFailureTogether() {
        final ChanPost post = new ChanPost();
        post.setNo(123L);
        final IOException error = new IOException("offline");

        final CachedCatalog catalog = new CachedCatalog("wsg", Collections.singletonList(post), error);

        assertEquals("wsg", catalog.getBoardName());
        assertEquals(123L, catalog.getPosts().get(0).getNo());
        assertSame(error, catalog.getError());
    }

    @Test
    public void catalogRowsRetainTheirBoardCacheKey() {
        final ChanPost post = new ChanPost();
        post.setNo(456L);

        final CatalogPost cachedPost = new CatalogPost("gif", post);

        assertEquals("gif", cachedPost.getBoardName());
        assertEquals(456L, cachedPost.toPost().getNo());
    }
}
