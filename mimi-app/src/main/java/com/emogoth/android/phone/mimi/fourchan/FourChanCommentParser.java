/*
 * Copyright (c) 2016. Eli Connelly
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.emogoth.android.phone.mimi.fourchan;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;

import androidx.core.content.res.ResourcesCompat;

import com.emogoth.android.phone.mimi.R;
import com.emogoth.android.phone.mimi.span.CodeSpan;
import com.emogoth.android.phone.mimi.span.LinkSpan;
import com.emogoth.android.phone.mimi.span.ReplySpan;
import com.emogoth.android.phone.mimi.span.SpoilerSpan;
import com.emogoth.android.phone.mimi.span.YoutubeLinkSpan;
import com.mimireader.chanlib.util.CommentParser;

import org.apache.commons.lang3.StringUtils;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;


public final class FourChanCommentParser extends CommentParser {
    private static final String LOG_TAG = FourChanCommentParser.class.getSimpleName();

    private FourChanCommentParser(List<String> replies, List<Long> userPostIds, List<Long> highlightedPosts, Context context, CharSequence comment, String boardName, String opTag, String youTag, long threadId, int replyColor, int highlightedReplyColor, int quoteColor, int linkColor, boolean demoMode) {
        super(replies,
                userPostIds,
                highlightedPosts,
                context,
                comment,
                boardName,
                opTag,
                youTag,
                threadId,
                replyColor,
                highlightedReplyColor,
                quoteColor,
                linkColor,
                demoMode);
    }

    public static class Builder extends CommentParser.Builder {

        public CommentParser build() {
            if (replyColor == -1) {
                replyColor = ResourcesCompat.getColor(context.getResources(), R.color.reply, context.getTheme());
            }

            if (highlightColor == -1) {
                highlightColor = ResourcesCompat.getColor(context.getResources(), R.color.reply_highlight, context.getTheme());
            }

            if (quoteColor == -1) {
                quoteColor = ResourcesCompat.getColor(context.getResources(), R.color.quote, context.getTheme());
            }

            if (linkColor == -1) {
                linkColor = ResourcesCompat.getColor(context.getResources(), R.color.link, context.getTheme());
            }

            return new FourChanCommentParser(
                    replies,
                    userPostIds,
                    highlightedPosts,
                    context,
                    comment,
                    boardName,
                    opTag,
                    youTag,
                    threadId,
                    replyColor,
                    highlightColor,
                    quoteColor,
                    linkColor,
                    demoMode);
        }
    }

    public CharSequence parse() {
        final String rawComment = comment == null ? "" : comment.toString();
        String postWithoutHtml = FourChanHtmlParser.parseCommentText(rawComment);
        String rawPost = FourChanHtmlParser.normalizeLineBreaks(rawComment);
        rawPost = rawPost.replaceAll("&quot;", "\"");
        rawPost = rawPost.replaceAll("&#039;", "\'");
        rawPost = rawPost.replaceAll("&amp;", "&");
        rawPost = rawPost.replaceAll("&gt;", ">");
        rawPost = rawPost.replaceAll("&lt;", "<");

        postWithoutHtml = postWithoutHtml.replace(">>" + threadId, ">>" + threadId + opTag);

        if (userPostIds != null) {
            for (Long userPostId : userPostIds) {
                final String id = userPostId.toString();
                postWithoutHtml = postWithoutHtml.replace(">>" + id, ">>" + id + youTag);
            }
        }

        SpannableStringBuilder span = new SpannableStringBuilder(postWithoutHtml);

        // These can be exact matches
        String replyToken = "class=\"quotelink\">";
        String quoteToken = "<span class=\"quote\">>";
        String codeToken = "<pre class=\"prettyprint\">";
        String spoilerToken = "<s>";
        String replyEnd = "</a>"; // replies end with anchorEnd
        String quoteEnd = "</span>"; // quotes end with spanEnd
        String codeEnd = "<br></pre>";
        String codeEnd2 = "</pre>";
        String spoilerEnd = "</s>";

        // Use this string to search in the spannable variable and set the span to whatever is appropriate
        String stringToSpan;

        // These are compared to determine what the cursor is currently on
        int quotePos;
        int replyPos;
        int codePos;
        int spoilerPos;

        int start;
        int end;
        int strlen;

        int quoteCursor = 0;
        int replyCursor = 0;
        int codeCursor = 0;
        int spoilerCursor = 0;

        final ArrayList<String> urlList = new ArrayList<>();
        final String[] parts = postWithoutHtml.split("\\s+");

        // Attempt to convert each item into an URL.
        for (String item : parts)
            try {

                if (item != null) {
                    if (item.contains("http://") || item.contains("https://")) {
                        if (item.startsWith(">")) {
                            item = item.replace(">", "");
                        }

                        final URL url = new URL(item);
                        urlList.add(item);

                        final int urlStart = postWithoutHtml.contains(item) ? postWithoutHtml.indexOf(item) : 0;
                        final int urlEnd;
                        final int endPos = urlStart + item.length();

                        if (endPos > span.length()) {
                            urlEnd = span.length() - 1;
                        } else {
                            urlEnd = endPos;
                        }

                        if (item.contains("youtube.com") || item.contains("youtu.be")) {
                            final String youtubeId = getYouTubeIdFromUrl(item);
                            if (!TextUtils.isEmpty(youtubeId)) {
                                span.setSpan(new YoutubeLinkSpan(youtubeId, linkColor), urlStart, urlEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            } else {
                                span.setSpan(new LinkSpan(item, linkColor), urlStart, urlEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                            }
                        } else {
                            span.setSpan(new LinkSpan(item, linkColor), urlStart, urlEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    } else if (item.startsWith(">>>")) {
                        if (replies != null && item.length() > 3) {
                            final String reply = item.substring(3);
                            if (replies.indexOf(reply) < 0) {
                                replies.add(reply);
                            }
                        }
                    } else if (item.startsWith(">>")) {
                        if (replies != null && item.length() > 2) {
                            final String reply = item.substring(2);
                            if (replies.indexOf(reply) < 0) {
                                replies.add(reply);
                            }
                        }
                    }
                }

            } catch (MalformedURLException e) {
                // If there was an URL that was not it!...
            }

        rawPost = rawPost.replace(">>" + threadId, ">>" + threadId + opTag);

        if (userPostIds != null) {
            for (final Long userPostId : userPostIds) {
                rawPost = rawPost.replace(">>" + userPostId, ">>" + userPostId + youTag);
            }
        }

        boolean done = false;
        while (!done) {

            start = -1;

            quotePos = rawPost.indexOf(quoteToken, quoteCursor);
            replyPos = rawPost.indexOf(replyToken, replyCursor);
            codePos = rawPost.indexOf(codeToken, codeCursor);
            spoilerPos = rawPost.indexOf(spoilerToken, spoilerCursor);

            if (quotePos >= 0) {
                quoteCursor = quotePos + quoteToken.length();

                // move the starting position up one place so we don't match with quoteEnd again
                start = quoteCursor - 1;

                // set end to be the start of quoteEnd
                end = rawPost.indexOf(quoteEnd, start);

                // reset the cursor for the next loop
                quoteCursor = end;

                // this needs to be set because start and end aren't the same in the spannable
                strlen = end - start;

                // get the string we want to set as a custom spannable
                stringToSpan = rawPost.substring(start, end); // lower case because postWithoutHtml is lower case

                // reuse the start variable to find the start of stringToSpan
                start = postWithoutHtml.indexOf(stringToSpan);
                while (start >= 0) {

                    // reuse end to be the end of stringToSpan within the text already in the spannable
                    end = start + strlen;
                    if (end > span.length()) {
                        end = span.length();
                    }

                    // The magic happens here!
                    span.setSpan(new ForegroundColorSpan(quoteColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                    start = postWithoutHtml.indexOf(stringToSpan, start + stringToSpan.length());

                }

            }

            if (replyPos >= 0) {
                replyCursor = replyPos + replyToken.length();

                start = replyCursor;

                // set end to be the start of quoteEnd
                end = rawPost.indexOf(replyEnd, start);

                // reset the cursor for the next loop
                replyCursor = end;

                // this needs to be set because start and end aren't the same in the spannable
                strlen = end - start;

                // get the string we want to set as a custom spannable
                stringToSpan = rawPost.substring(start, end);

                // reuse the start variable to find the start of stringToSpan
                start = postWithoutHtml.indexOf(stringToSpan);

                while (start >= 0) {
                    // reuse end to be the end of stringToSpan within the text already in the spannable
                    end = start + strlen;
                    if (end > span.length()) {
                        end = span.length();
                    }

                    // The magic happens here!
                    if ((replies != null && replies.size() > 0) || demoMode) {

                        final String s = stringToSpan.substring(2).split(" ")[0];
                        if (StringUtils.isNumericSpace(s)) {
                            final long id = Long.valueOf(s);
                            final boolean highlight = (userPostIds != null && userPostIds.contains(id))
                                    || (highlightedPosts != null && highlightedPosts.contains(id) && (demoMode || replies.size() > 1));

                            final int textColor;
                            if (highlight) {
                                textColor = highlightReplyColor;
                            } else {
                                textColor = replyColor;
                            }

                            span.setSpan(new ReplySpan(boardName, threadId, replies, textColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        } else {
                            span.setSpan(new ReplySpan(boardName, threadId, replies, replyColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }

                    } else {
                        span.setSpan(new ForegroundColorSpan(replyColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }

                    start = postWithoutHtml.indexOf(stringToSpan, start + stringToSpan.length());

                }
            }

            if (codePos >= 0) {
                codeCursor = codePos + codeToken.length();

                start = codeCursor;
                end = rawPost.indexOf(codeEnd, start);
                if (end < 0) {
                    end = rawPost.indexOf(codeEnd2, start);
                }

                codeCursor = end;

                strlen = end - start;
                stringToSpan = rawPost.substring(start, end);
                start = postWithoutHtml.indexOf(stringToSpan.replace("<br>", "\n").trim());

                while (start >= 0) {
                    end = start + strlen;
                    if (end > span.length()) {
                        end = span.length();
                    }

                    span.setSpan(new CodeSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    start = postWithoutHtml.indexOf(stringToSpan, start + stringToSpan.length());
                }

            }

            if (spoilerPos >= 0) {
                try {
                    spoilerCursor = spoilerPos + spoilerToken.length();

                    start = spoilerCursor;
                    end = rawPost.indexOf(spoilerEnd, start);

                    codeCursor = end;
                    strlen = end - start;
                    stringToSpan = rawPost.substring(start, end);
                    start = postWithoutHtml.indexOf(stringToSpan);

                    while (start >= 0) {
                        end = start + strlen;
                        if (end > span.length()) {
                            end = span.length();
                        }

                        span.setSpan(new SpoilerSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        start = postWithoutHtml.indexOf(stringToSpan, start + stringToSpan.length());
                    }
                } catch (Exception e) {
                    Log.e(LOG_TAG, "Caught error while parsing spoiler text", e);
                }
            }

            if ((quotePos < 0) && (replyPos < 0) && (codePos < 0) && (spoilerPos < 0)) {
                done = true;
            }

        }

        return span;
    }

    private static String getYouTubeIdFromUrl(String url) {
        String vId = null;
        Matcher matcher = YOUTUBE_PATTERN.matcher(url);
        if (matcher.matches()) {
            vId = matcher.group(1);
        }
        return vId;
    }
}
