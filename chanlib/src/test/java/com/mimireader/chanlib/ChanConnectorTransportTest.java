package com.mimireader.chanlib;

import androidx.annotation.NonNull;

import com.mimireader.chanlib.interfaces.ArchiveConverter;
import com.mimireader.chanlib.models.ArchivedChanThread;

import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;

import io.reactivex.Single;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.http.GET;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Standalone library tests: no app classpath, Android schedulers or network requests. */
public class ChanConnectorTransportTest {
    @Rule
    public final TemporaryFolder temporary = new TemporaryFolder();

    private static final class Builder extends ChanConnector.ChanConnectorBuilder {
        Retrofit retrofit(boolean post) {
            return initRetrofit(post);
        }

        @Override
        public <T extends ChanConnector> T build() {
            throw new UnsupportedOperationException("Transport fixture does not build a connector");
        }
    }

    interface Service {
        @GET("thread")
        Single<Response<ResponseBody>> thread();
    }

    static final class Payload {
        String name;
    }

    private Builder builder() {
        Builder builder = new Builder();
        builder.setEndpoint("https://read.example.invalid/api/");
        builder.setPostEndpoint("https://post.example.invalid/api/");
        return builder;
    }

    @Test
    public void defaultClientRetainsTimeoutsRedirectsAndRetryPolicy() {
        OkHttpClient client = (OkHttpClient) builder().retrofit(false).callFactory();
        assertEquals(20_000, client.connectTimeoutMillis());
        assertEquals(20_000, client.callTimeoutMillis());
        assertEquals(20_000, client.writeTimeoutMillis());
        assertEquals(20_000, client.readTimeoutMillis());
        assertTrue(client.followRedirects());
        assertTrue(client.followSslRedirects());
        assertTrue(client.retryOnConnectionFailure());
    }

    @Test
    public void readAndPostEndpointsShareTheDefaultClient() {
        Builder builder = builder();
        Retrofit read = builder.retrofit(false);
        Retrofit post = builder.retrofit(true);
        assertEquals("https://read.example.invalid/api/", read.baseUrl().toString());
        assertEquals("https://post.example.invalid/api/", post.baseUrl().toString());
        assertSame(read.callFactory(), post.callFactory());
    }

    @Test
    public void suppliedClientIsNotReplacedOrReconfigured() {
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();
        Builder builder = builder();
        builder.setClient(client);
        assertSame(client, builder.retrofit(false).callFactory());
        assertSame(client, builder.retrofit(true).callFactory());
        assertFalse(client.followRedirects());
    }

    @Test
    public void existingCacheDirectoryAndConfiguredSizeAreRetained() throws Exception {
        Builder builder = builder();
        java.io.File directory = temporary.newFolder("http-cache");
        builder.setCacheDirectory(directory);
        builder.setMaxCacheSize(4096);
        OkHttpClient client = (OkHttpClient) builder.retrofit(false).callFactory();
        try (okhttp3.Cache cache = client.cache()) {
            assertNotNull(cache);
            assertEquals(directory, cache.directory());
            assertEquals(4096, cache.maxSize());
        }
    }

    @Test
    public void gsonConverterAndRxAdapterRemainAvailableWithoutSendingAnything() throws Exception {
        Retrofit retrofit = builder().retrofit(false);
        Converter<ResponseBody, Payload> converter = retrofit.responseBodyConverter(
                Payload.class, new Annotation[0]);
        try (ResponseBody body = ResponseBody.create(MediaType.parse("application/json"),
                "{\"name\":\"standalone\"}")) {
            assertEquals("standalone", converter.convert(body).name);
        }
        Type returnType = Service.class.getDeclaredMethod("thread").getGenericReturnType();
        assertEquals(ResponseBody.class, retrofit.callAdapter(returnType, new Annotation[0]).responseType());
        // Creating a lazy Single must not make a request; never subscribe in this fixture.
        assertNotNull(retrofit.create(Service.class).thread());
    }

    @Test
    public void publicAnnotationAndConverterContractStillCompile() {
        ArchiveConverter converter = new ArchiveConverter() {
            @Override
            public ArchivedChanThread toArchivedThread(@NonNull String board, long threadId,
                                                       @NonNull String name, @NonNull String domain) {
                return null;
            }
        };
        assertNotNull(converter);
    }

    @Test
    public void standaloneClasspathDoesNotAcquireApplicationOnlyDependencies() {
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("androidx.core.content.ContextCompat"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("androidx.core.content.ContextKt"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("io.reactivex.android.schedulers.AndroidSchedulers"));
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("org.jsoup.Jsoup"));
    }
}
