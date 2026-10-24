package com.emogoth.android.phone.mimi.util

import android.text.Spanned
import android.text.TextPaint
import android.widget.Filter
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.emogoth.android.phone.mimi.R
import com.emogoth.android.phone.mimi.adapter.PostOptionAdapter
import com.emogoth.android.phone.mimi.db.models.PostOption
import com.emogoth.android.phone.mimi.fourchan.FourChanCommentParser
import com.emogoth.android.phone.mimi.span.ReplySpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual consumers with synthetic data: no Room writes, navigation or public posting. */
@RunWith(AndroidJUnit4::class)
class CommonsLangCompatibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun realPostOptionFilterPublishesUnicodeMatchesAndRestoresAllRows() {
        val names = listOf("sage", "Álvaro", "İSTANBUL", "Straße")
        val originals = names.map { PostOption(option = it, lastUsed = 0L) }
        lateinit var adapter: PostOptionAdapter
        lateinit var filter: Filter
        lateinit var parent: FrameLayout
        instrumentation.runOnMainSync {
            val themed = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Mimi_Light_Toolbar_Default)
            parent = FrameLayout(themed)
            adapter = PostOptionAdapter(themed, R.layout.post_option_item, originals)
            filter = adapter.filter
        }
        for ((query, expected) in listOf(
            null to names, "SAGE" to listOf("sage"), "álV" to listOf("Álvaro"),
            "alv" to emptyList(), "istanbul" to listOf("İSTANBUL"),
            "STRASSE" to emptyList(), "not present" to emptyList(), "" to names, null to names)) {
            val published = CountDownLatch(1)
            var count = -1
            instrumentation.runOnMainSync { filter.filter(query) { value -> count = value; published.countDown() } }
            assertTrue("Real filter must publish within 10 seconds", published.await(10, TimeUnit.SECONDS))
            assertEquals(expected.size, count)
            instrumentation.runOnMainSync {
                assertEquals(expected.size, adapter.count)
                // The adapter owns a separate filtered list; getItem() would read ArrayAdapter's original list.
                val displayed = (0 until adapter.count).map { index ->
                    adapter.getView(index, null, parent).findViewById<TextView>(R.id.option_text).text.toString()
                }
                assertEquals(expected, displayed)
                assertEquals(names, originals.map { it.option })
                assertTrue(originals.all { it.id == null && it.lastUsed == 0L && it.usedCount == 0 })
            }
        }
    }

    @Test fun realCommentParserPreservesNumericUnicodeCrossBoardAndHighlightedReplies() {
        val normal = 0xff3366aa.toInt()
        val highlighted = 0xffaa3355.toInt()
        val quote = 0xff228822.toInt()
        val html = "<span class=\"quote\">&gt;green text</span><br>" +
            listOf("100", "101", "102", "١٢٣", ">/g/101", "\uD835\uDFD9").joinToString("<br>") {
                "<a href=\"#p$it\" class=\"quotelink\">&gt;&gt;$it</a>"
            }
        fun parse(input: String?): Spanned = FourChanCommentParser.Builder()
            .setContext(instrumentation.targetContext).setBoardName("g").setThreadId(100L)
            .setReplies(mutableListOf("101", "102")).setUserPostIds(listOf(101L))
            .setHighlightedPosts(listOf(102L, 123L)).setReplyColor(normal)
            .setHighlightColor(highlighted).setQuoteColor(quote).setLinkColor(normal)
            .setComment(input).build().parse() as Spanned
        val parsed = parse(html)
        assertTrue(parsed.toString().contains(">green text"))
        assertTrue(parsed.toString().contains(">>100 (OP)"))
        assertTrue(parsed.toString().contains(">>101 (You)"))
        val actual = parsed.getSpans(0, parsed.length, ReplySpan::class.java).associate { span ->
            val paint = TextPaint()
            span.updateDrawState(paint)
            assertTrue(paint.isUnderlineText)
            parsed.subSequence(parsed.getSpanStart(span), parsed.getSpanEnd(span)).toString() to paint.color
        }
        assertEquals(mapOf(">>100 (OP)" to normal, ">>101 (You)" to highlighted, ">>102" to highlighted,
            ">>١٢٣" to highlighted, ">>>/g/101" to normal, ">>\uD835\uDFD9" to normal), actual)
        assertEquals("", parse(null).toString())
        assertEquals("", parse("").toString())
    }
}
