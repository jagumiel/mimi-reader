package com.emogoth.android.phone.mimi.fourchan;

import com.emogoth.android.phone.mimi.fourchan.api.FourChanApi;
import com.emogoth.android.phone.mimi.fourchan.models.FourChanThread;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.HttpException;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FourChanApiContractTest {
    private MockWebServer server;
    private FourChanApi api;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        api = new Retrofit.Builder()
                .baseUrl(server.url("/"))
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .build()
                .create(FourChanApi.class);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void requestsThreadPathAndCacheControlHeader() throws InterruptedException {
        server.enqueue(jsonResponse("{\"posts\":[{\"no\":123,\"ext\":\".mp4\","
                + "\"tim\":1786436977617243,\"fsize\":6210678}]}"));

        final FourChanThread thread = api.fetchThread("wsg", 123L, "no-cache").blockingGet();
        final RecordedRequest request = takeRequest();

        assertEquals(1, thread.getPosts().size());
        assertEquals("/wsg/thread/123.json", request.getPath());
        assertEquals("no-cache", request.getHeader("Cache-Control"));
    }

    @Test
    public void requestsCatalogPath() throws InterruptedException {
        server.enqueue(jsonResponse("[{\"page\":1,\"threads\":[{\"no\":321}]}]"));

        assertEquals(1, api.fetchCatalog("wsg").blockingGet().size());
        assertEquals("/wsg/catalog.json", takeRequest().getPath());
    }

    @Test
    public void propagatesNotFoundResponses() {
        assertHttpError(404);
    }

    @Test
    public void propagatesRateLimitResponses() {
        assertHttpError(429);
    }

    @Test
    public void propagatesServerErrors() {
        assertHttpError(503);
    }

    @Test
    public void rejectsTruncatedSuccessfulResponse() {
        server.enqueue(jsonResponse("{\"posts\":[{\"no\":123}"));

        try {
            api.fetchThread("wsg", 123L, null).blockingGet();
            fail("Expected truncated JSON to fail");
        } catch (RuntimeException error) {
            assertTrue(hasCauseNamed(error, "JsonSyntaxException")
                    || hasCauseNamed(error, "EOFException"));
        }
    }

    private void assertHttpError(int status) {
        server.enqueue(new MockResponse().setResponseCode(status));

        try {
            api.fetchThread("wsg", 123L, null).blockingGet();
            fail("Expected HTTP " + status);
        } catch (HttpException error) {
            assertEquals(status, error.code());
        }
    }

    private RecordedRequest takeRequest() throws InterruptedException {
        final RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertNotNull(request);
        return request;
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }

    private static boolean hasCauseNamed(Throwable error, String simpleName) {
        Throwable current = error;
        while (current != null) {
            if (simpleName.equals(current.getClass().getSimpleName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
