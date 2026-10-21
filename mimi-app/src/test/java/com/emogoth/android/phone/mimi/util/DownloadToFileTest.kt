package com.emogoth.android.phone.mimi.util

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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
        assertTrue(observer.awaitTerminalEvent(10, TimeUnit.SECONDS))
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
        assertTrue(observer.awaitTerminalEvent(10, TimeUnit.SECONDS))
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
        val partialExistsAtError = AtomicBoolean(true)

        val observer = downloadToFile(
                OkHttpClient(),
                server.url("/truncated.swf").toString(),
                target,
                4096L
        ).doOnError {
            // Capture state inside notification: waiting for termination alone races cleanup.
            partialExistsAtError.set(File(directory, "truncated.swf.part").exists())
        }.test()
        assertTrue(observer.awaitTerminalEvent(10, TimeUnit.SECONDS))
        observer.assertError { error: Throwable ->
            error is IOException && error.message.orEmpty().contains("Unexpected download size")
        }

        assertFalse(target.exists())
        assertFalse("Temporary file must be removed before notifying the subscriber", partialExistsAtError.get())
        assertFalse(File(directory, "truncated.swf.part").exists())
        assertTrue(server.requestCount == 1)
    }

    @Test
    fun closesAndRemovesPartialDownloadsBeforeReportingReadFailures() {
        val body = ByteArray(8192) { 0x46 }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(body))
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        val target = File(directory, "interrupted.swf")
        val partialExistsAtError = AtomicBoolean(true)

        val observer = downloadToFile(
                OkHttpClient.Builder().retryOnConnectionFailure(false).readTimeout(5, TimeUnit.SECONDS).build(),
                server.url("/interrupted.swf").toString(), target, body.size.toLong()
        ).doOnError {
            partialExistsAtError.set(File(directory, "interrupted.swf.part").exists())
        }.test()
        assertTrue(observer.awaitTerminalEvent(10, TimeUnit.SECONDS))
        observer.assertError(IOException::class.java)

        assertFalse(target.exists())
        assertFalse("Read failures must close and delete the partial file before notification", partialExistsAtError.get())
        assertFalse(File(directory, "interrupted.swf.part").exists())
    }

    @Test
    fun aSubscriberCanRetryImmediatelyAfterAnError() {
        val body = byteArrayOf(0x46, 0x57, 0x53, 0x09, 0x01, 0x02)
        server.enqueue(MockResponse().setBody("short"))
        server.enqueue(MockResponse().setBody(okio.Buffer().write(body)))
        val target = File(directory, "retry.swf")
        val partialExistsAtError = AtomicBoolean(true)
        val download = downloadToFile(OkHttpClient(), server.url("/retry.swf").toString(),
                target, body.size.toLong())

        val observer = download.doOnError {
            partialExistsAtError.set(File(directory, "retry.swf.part").exists())
        }.onErrorResumeNext(download).test()
        assertTrue(observer.awaitTerminalEvent(10, TimeUnit.SECONDS))
        observer.assertComplete()
        observer.assertNoErrors()

        assertFalse("A retry must not race the previous response's cleanup", partialExistsAtError.get())
        assertArrayEquals(body, target.readBytes())
        assertFalse(File(directory, "retry.swf.part").exists())
        assertTrue(server.requestCount == 2)
    }
}
