package com.emogoth.android.phone.mimi.util

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

class DownloadToFileTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        directory = Files.createTempDirectory("mimi-download-").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun writesSuccessfulResponsesOnlyAfterTheExpectedSizeIsReceived() {
        val body = byteArrayOf(0x46, 0x57, 0x53, 0x09, 0x01, 0x02)
        server.enqueue(MockResponse().setResponseCode(200).setBody(okio.Buffer().write(body)))
        val target = File(directory, "movie.swf")

        val observer = downloadToFile(
                OkHttpClient(),
                server.url("/movie.swf").toString(),
                target,
                body.size.toLong()
        ).test()
        observer.awaitTerminalEvent()
        observer.assertComplete()
        observer.assertNoErrors()

        assertArrayEquals(body, target.readBytes())
        assertFalse(File(directory, "movie.swf.part").exists())
    }

    @Test
    fun rejectsHttpErrorsWithoutSavingTheirHtmlBodyAsMedia() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not found</html>"))
        val target = File(directory, "missing.swf")

        val observer = downloadToFile(
                OkHttpClient(),
                server.url("/missing.swf").toString(),
                target,
                1024L
        ).test()
        observer.awaitTerminalEvent()
        observer.assertError { error: Throwable ->
            error is MediaDownloadHttpException && error.statusCode == 404
        }

        assertFalse(target.exists())
        assertFalse(File(directory, "missing.swf.part").exists())
    }

    @Test
    fun rejectsSuccessfulResponsesWhoseSizeDoesNotMatchTheCatalog() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("truncated"))
        val target = File(directory, "truncated.swf")

        val observer = downloadToFile(
                OkHttpClient(),
                server.url("/truncated.swf").toString(),
                target,
                4096L
        ).test()
        observer.awaitTerminalEvent()
        observer.assertError { error: Throwable ->
            error is IOException && error.message.orEmpty().contains("Unexpected download size")
        }

        assertFalse(target.exists())
        assertFalse(File(directory, "truncated.swf.part").exists())
        assertTrue(server.requestCount == 1)
    }
}
