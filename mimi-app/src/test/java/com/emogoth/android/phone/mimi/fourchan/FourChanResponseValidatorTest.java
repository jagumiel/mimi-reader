package com.emogoth.android.phone.mimi.fourchan;

import com.emogoth.android.phone.mimi.fourchan.models.FourChanPost;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThread;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThreadPage;
import com.emogoth.android.phone.mimi.util.InvalidApiResponseException;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.lang.reflect.Type;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FourChanResponseValidatorTest {
    private static final Gson GSON = new Gson();
    private static final Type CATALOG_TYPE =
            new TypeToken<List<FourChanThreadPage>>() { }.getType();

    @Test
    public void keepsValidThreadPostsFromAPartialResponse() throws Exception {
        FourChanThread thread = GSON.fromJson(
                "{\"posts\":[{\"no\":123},null,{\"com\":\"missing id\"},{\"no\":124}]}",
                FourChanThread.class);

        List<FourChanPost> posts = FourChanResponseValidator.requireThreadPosts(thread, 123L);

        assertEquals(2, posts.size());
        assertEquals(123, posts.get(0).getNo());
        assertEquals(124, posts.get(1).getNo());
    }

    @Test
    public void keepsValidCatalogEntriesAcrossIncompletePages() throws Exception {
        List<FourChanThreadPage> pages = GSON.fromJson(
                "[null,{\"page\":1,\"threads\":null},"
                        + "{\"page\":2,\"threads\":[null,{\"sub\":\"missing id\"},{\"no\":321}]}]",
                CATALOG_TYPE);

        List<FourChanPost> posts = FourChanResponseValidator.requireCatalogPosts(pages);

        assertEquals(1, posts.size());
        assertEquals(321, posts.get(0).getNo());
    }

    @Test
    public void rejectsThreadWithoutUsablePosts() {
        FourChanThread thread = GSON.fromJson("{\"posts\":null}", FourChanThread.class);

        assertInvalidThread(thread);
    }

    @Test
    public void rejectsCatalogWithoutUsableThreads() {
        List<FourChanThreadPage> pages = GSON.fromJson(
                "[{\"page\":1,\"threads\":null}]", CATALOG_TYPE);

        try {
            FourChanResponseValidator.requireCatalogPosts(pages);
            fail("Expected an invalid catalog response");
        } catch (InvalidApiResponseException expected) {
            assertEquals("Catalog response contains no usable threads", expected.getMessage());
        }
    }

    private static void assertInvalidThread(FourChanThread thread) {
        try {
            FourChanResponseValidator.requireThreadPosts(thread, 123L);
            fail("Expected an invalid thread response");
        } catch (InvalidApiResponseException expected) {
            assertEquals("Thread response does not contain the expected opening post", expected.getMessage());
        }
    }
}
