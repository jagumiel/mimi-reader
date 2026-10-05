package com.emogoth.android.phone.mimi.fourchan;

import com.emogoth.android.phone.mimi.fourchan.models.FourChanPost;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanBoards;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThread;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThreadPage;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FourChanJsonContractTest {
    private static final Gson GSON = new Gson();

    @Test
    public void parsesThreadMediaUsedByGalleryAndDownloads() {
        final String json = "{\"posts\":["
                + "{\"no\":100,\"resto\":0,\"filename\":\"clip\","
                + "\"ext\":\".mp4\",\"tim\":1786436977617243,"
                + "\"fsize\":6210678,\"w\":1920,\"h\":1080},"
                + "{\"no\":101,\"resto\":100,\"filename\":\"animation\","
                + "\"ext\":\".webm\",\"tim\":1787019948084733,"
                + "\"fsize\":5010799,\"unknown_future_field\":true}]}";

        final FourChanThread thread = GSON.fromJson(json, FourChanThread.class);

        assertEquals(2, thread.getPosts().size());
        assertMedia(thread.getPosts().get(0), 100, ".mp4", "1786436977617243", 6210678);
        assertMedia(thread.getPosts().get(1), 101, ".webm", "1787019948084733", 5010799);
    }

    @Test
    public void parsesCatalogPagesAndMissingOptionalMedia() {
        final String json = "[{\"page\":1,\"threads\":["
                + "{\"no\":200,\"sub\":\"Thread title\",\"replies\":12,\"images\":4},"
                + "{\"no\":201,\"replies\":0,\"images\":0}]}]";
        final Type catalogType = new TypeToken<List<FourChanThreadPage>>() { }.getType();

        final List<FourChanThreadPage> pages = GSON.fromJson(json, catalogType);

        assertEquals(1, pages.size());
        assertEquals(1, pages.get(0).getPage());
        assertEquals(2, pages.get(0).getThreads().size());
        assertEquals("Thread title", pages.get(0).getThreads().get(0).getSub());
        assertNull(pages.get(0).getThreads().get(1).getExt());
        assertNull(pages.get(0).getThreads().get(1).getTim());
    }

    @Test
    public void normalizesExplicitlyNullCollections() {
        final FourChanThread thread = GSON.fromJson("{\"posts\":null}", FourChanThread.class);
        final FourChanBoards boards = GSON.fromJson("{\"boards\":null}", FourChanBoards.class);
        final Type catalogType = new TypeToken<List<FourChanThreadPage>>() { }.getType();
        final List<FourChanThreadPage> pages = GSON.fromJson(
                "[{\"page\":1,\"threads\":null}]", catalogType);

        assertTrue(thread.getPosts().isEmpty());
        assertTrue(boards.getBoards().isEmpty());
        assertTrue(boards.toBoardList().isEmpty());
        assertTrue(pages.get(0).getThreads().isEmpty());
    }

    @Test(expected = JsonSyntaxException.class)
    public void rejectsMalformedApiJson() {
        GSON.fromJson("{\"posts\":[", FourChanThread.class);
    }

    private static void assertMedia(FourChanPost post,
                                    int postId,
                                    String extension,
                                    String timestamp,
                                    int fileSize) {
        assertEquals(postId, post.getNo());
        assertEquals(extension, post.getExt());
        assertEquals(timestamp, post.getTim());
        assertEquals(fileSize, post.getFileSize());
    }
}
