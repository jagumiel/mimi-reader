package com.emogoth.android.phone.mimi.fourchan;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.regex.Pattern;

public final class FourChanHtmlParser {
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final String LINE_BREAK_MARKER = "\uE000MIMI_LINE_BREAK\uE001";

    private FourChanHtmlParser() {
    }

    public static String parseCommentText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        final String markedHtml = LINE_BREAK.matcher(html).replaceAll(LINE_BREAK_MARKER);
        return Jsoup.parse(markedHtml).text().replace(LINE_BREAK_MARKER, "\n");
    }

    public static String normalizeLineBreaks(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }

        return LINE_BREAK.matcher(html).replaceAll("\n");
    }

    public static LoginResult parseLoginResponse(String html) {
        final Document document = Jsoup.parse(html == null ? "" : html);
        final Element error = document.selectFirst(".msg-error");
        if (error != null && !error.text().isEmpty()) {
            return new LoginResult(error.html(), false);
        }

        final Element success = document.selectFirst(".msg-success");
        return new LoginResult(null, success != null && !success.text().isEmpty());
    }

    public static String parsePostError(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }

        final Element error = Jsoup.parse(html).selectFirst("#errmsg, .errmsg, [name=errmsg]");
        if (error == null || error.text().isEmpty()) {
            return null;
        }

        return error.text();
    }

    public static final class LoginResult {
        private final String errorHtml;
        private final boolean success;

        private LoginResult(String errorHtml, boolean success) {
            this.errorHtml = errorHtml;
            this.success = success;
        }

        public String getErrorHtml() {
            return errorHtml;
        }

        public boolean hasError() {
            return errorHtml != null && !errorHtml.isEmpty();
        }

        public boolean isSuccess() {
            return success;
        }
    }
}
