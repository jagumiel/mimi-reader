package com.emogoth.android.phone.mimi.fourchan;

import org.jsoup.Jsoup;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class FourChanHtmlParserTest {
    @Test
    public void parsesCommentMarkupAndHtmlEntities() {
        assertEquals("first line\nsecond & third",
                FourChanHtmlParser.parseCommentText("first line<br>second &amp; <b>third</b>"));
    }

    @Test
    public void supportsBreakVariantsAndMalformedMarkup() {
        assertEquals("one\ntwo\nthree",
                FourChanHtmlParser.parseCommentText("one<BR />two<br/>three<i>"));
        assertEquals("one\ntwo", FourChanHtmlParser.normalizeLineBreaks("one<Br >two"));
    }

    @Test
    public void extractsFormattedLoginError() {
        final FourChanHtmlParser.LoginResult result = FourChanHtmlParser.parseLoginResponse(
                "<div class='notice msg-error'>Denied &amp; <b>blocked</b></div>");

        assertTrue(result.hasError());
        assertFalse(result.isSuccess());
        assertEquals("Denied & blocked", Jsoup.parseBodyFragment(result.getErrorHtml()).text());
    }

    @Test
    public void detectsSuccessfulLogin() {
        final FourChanHtmlParser.LoginResult result = FourChanHtmlParser.parseLoginResponse(
                "<div class='msg-success'>Authenticated</div>");

        assertFalse(result.hasError());
        assertTrue(result.isSuccess());
    }

    @Test
    public void loginErrorTakesPrecedenceOverSuccess() {
        final FourChanHtmlParser.LoginResult result = FourChanHtmlParser.parseLoginResponse(
                "<p class='msg-error'>Invalid PIN</p><p class='msg-success'>Ignored</p>");

        assertTrue(result.hasError());
        assertFalse(result.isSuccess());
    }

    @Test
    public void extractsPostErrorByIdOrClass() {
        assertEquals("Rate limited; try later", FourChanHtmlParser.parsePostError(
                "<span id='errmsg'>Rate limited; <b>try later</b></span>"));
        assertEquals("File rejected", FourChanHtmlParser.parsePostError(
                "<div class='notice errmsg'>File rejected</div>"));
    }

    @Test
    public void returnsNullWhenPostResponseHasNoError() {
        assertNull(FourChanHtmlParser.parsePostError("<script>parent.success(\"no:123\")</script>"));
        assertNull(FourChanHtmlParser.parsePostError(null));
    }
}
