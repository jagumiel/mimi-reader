package com.emogoth.android.phone.mimi.util;

import com.mimireader.chanlib.models.ChanPost;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MediaContractTest {
    @Test
    public void acceptsMediaWithoutOptionalSizeDimensionsOrOriginalName() {
        ChanPost post = post(123L, "1786436977617243", "mp4");

        assertTrue(MediaContract.hasRemoteMedia(post));
        assertEquals(".mp4", MediaContract.normalizedExtension(post.getExt()));
        assertEquals("1786436977617243", MediaContract.originalFilename(post));
    }

    @Test
    public void rejectsEntriesThatCannotBuildMediaUrls() {
        assertFalse(MediaContract.hasRemoteMedia(post(0L, "1786436977617243", ".jpg")));
        assertFalse(MediaContract.hasRemoteMedia(post(123L, null, ".jpg")));
        assertFalse(MediaContract.hasRemoteMedia(post(123L, "1786436977617243", null)));
        assertFalse(MediaContract.hasRemoteMedia(post(123L, "1786436977617243", ".")));
    }

    @Test
    public void normalizesChangedExtensionFormatting() {
        assertEquals(".webm", MediaContract.normalizedExtension(" WEBM "));
        assertEquals(".mp4", MediaContract.normalizedExtension(".MP4"));
        assertEquals("", MediaContract.normalizedExtension("  "));
    }

    private static ChanPost post(long id, String tim, String extension) {
        ChanPost post = new ChanPost();
        post.setNo(id);
        post.setTim(tim);
        post.setExt(extension);
        return post;
    }
}
