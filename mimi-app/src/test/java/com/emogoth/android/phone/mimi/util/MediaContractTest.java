package com.emogoth.android.phone.mimi.util;

import com.mimireader.chanlib.models.ChanPost;
import com.mimireader.chanlib.models.ArchivedChanPost;

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
        assertEquals("..jpg", MediaContract.normalizedExtension("..JPG"));
        assertEquals("", MediaContract.normalizedExtension("."));
        assertEquals("", MediaContract.normalizedExtension("  "));
    }

    @Test
    public void trimsOriginalNamesAndFallsBackToTheRemoteIdentifier() {
        ChanPost named = post(123L, "1786436977617243", ".jpg");
        named.setFilename(" holiday photo ");
        assertEquals("holiday photo", MediaContract.originalFilename(named));

        ChanPost blank = post(123L, "1786436977617243", ".jpg");
        blank.setFilename("  ");
        assertEquals("1786436977617243", MediaContract.originalFilename(blank));
        assertEquals("media", MediaContract.originalFilename(null));
    }

    private static ChanPost post(long id, String tim, String extension) {
        ChanPost post = new ChanPost();
        post.setNo(id);
        post.setTim(tim);
        post.setExt(extension);
        return post;
    }

    @Test
    public void flashBoardUsesOriginalNameEncodedAsOnePathSegment() {
        ChanPost flash = post(3524315L, "1744563833311673", ".SWF");
        flash.setFilename("Æ &amp; a/b? #100%");
        assertEquals("https://i.4cdn.org/f/%C3%86%20&%20a%2Fb%3F%20%23100%25.swf",
                MediaContract.mediaUrl("https://i.4cdn.org", "f", flash));
        assertEquals("", MediaContract.thumbnailUrl("https://i.4cdn.org", "f", flash));
    }

    @Test
    public void normalMediaStillUsesTimAndArchivesKeepTheirOwnLinks() {
        ChanPost video = post(123L, "1786436977617243", ".mp4");
        video.setFilename("not the remote filename");
        assertEquals("https://i.4cdn.org/g/1786436977617243.mp4",
                MediaContract.mediaUrl("https://i.4cdn.org/", "g", video));
        assertEquals("https://t.4cdn.org/g/1786436977617243s.jpg",
                MediaContract.thumbnailUrl("https://t.4cdn.org", "g", video));
        ArchivedChanPost archived = new ArchivedChanPost();
        archived.setExt(".swf");
        archived.mediaLink = "https://archive.example/movie.swf";
        archived.thumbLink = "https://archive.example/preview.jpg";
        assertEquals(archived.mediaLink, MediaContract.mediaUrl("https://i.4cdn.org", "f", archived));
        assertEquals(archived.thumbLink, MediaContract.thumbnailUrl("https://i.4cdn.org", "f", archived));
    }
}
