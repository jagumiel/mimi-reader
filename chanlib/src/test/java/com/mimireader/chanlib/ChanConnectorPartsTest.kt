package com.mimireader.chanlib

import com.mimireader.chanlib.models.*
import io.reactivex.Single
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/** Exercises the protected multipart helper; never constructs or calls a remote service. */
class ChanConnectorPartsTest {
    private class Connector : ChanConnector() {
        fun parts(values: Map<String, Any>): Map<String, RequestBody> = getPartsFromMap(values)

        override fun fetchBoards(): Single<List<ChanBoard>> = unused()
        override fun fetchCatalog(boardName: String): Single<ChanCatalog> = unused()
        override fun fetchThread(boardName: String, threadId: Long, cacheControl: String): Single<ChanThread> = unused()
        override fun fetchArchives(): Single<List<ChanArchive>> = unused()
        override fun fetchArchivedThread(board: String, threadId: Long, name: String, domain: String, url: String): Single<ArchivedChanThread> = unused()
        override fun post(boardName: String, params: Map<String, Any>): Single<Response<ResponseBody>> = unused()
        override fun login(token: String, pin: String): Single<Response<ResponseBody>> = unused()
        override fun getThumbUrl(boardName: String, id: String): String = unused()
        override val imageBaseUrl: String get() = unused()
        override fun getImageCountText(imageCount: Int): String = unused()
        override fun getRepliesCountText(repliesCount: Int): String = unused()

        private fun unused(): Nothing = error("No service operation is allowed in the parts fixture")
    }

    private fun bodyText(body: RequestBody): String {
        val buffer = Buffer()
        body.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Test
    fun stringIntegerAndLongFieldsKeepTheirTextAndMimeType() {
        val parts = Connector().parts(mapOf("comment" to "test", "mode" to 1, "resto" to 4_000_000_000L))
        assertEquals(setOf("comment", "mode", "resto"), parts.keys)
        assertEquals("test", bodyText(parts.getValue("comment")))
        assertEquals("1", bodyText(parts.getValue("mode")))
        assertEquals("4000000000", bodyText(parts.getValue("resto")))
        parts.values.forEach { assertEquals("text/plain; charset=utf-8", it.contentType().toString()) }
    }

    @Test
    fun emptyAndUnicodeCommentsAreNotLostOrRecoded() {
        val parts = Connector().parts(mapOf("empty" to "", "comment" to "café 日本語 😀"))
        assertEquals("", bodyText(parts.getValue("empty")))
        assertEquals("café 日本語 😀", bodyText(parts.getValue("comment")))
    }

    @Test
    fun unsupportedValuesStillDoNotBecomeMultipartFields() {
        assertTrue(Connector().parts(mapOf("boolean" to true, "decimal" to 1.5, "list" to listOf("a"))).isEmpty())
    }

    @Test
    fun emptyInputStillProducesNoParts() {
        assertTrue(Connector().parts(emptyMap()).isEmpty())
    }
}
