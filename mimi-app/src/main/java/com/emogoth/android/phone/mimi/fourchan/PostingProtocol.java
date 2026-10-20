package com.emogoth.android.phone.mimi.fourchan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Response;

/** Wire contracts only; never solves a CAPTCHA or retries a submission. */
public final class PostingProtocol {
    // The official widget's iframe/cookies expect the real board's HTTPS site.
    // A synthetic URL at this origin serves only locally authored HTML via interception,
    // never a network board page or a privileged JS bridge.
    public static final String CAPTCHA_ORIGIN = "https://boards.4chan.org/";
    public static final String SYSTEM_ORIGIN = "https://sys.4chan.org/";
    public static final String WIDGET_URL = "https://s.4cdn.org/js/tcaptcha.min.7.js";
    private static final Pattern BOARD = Pattern.compile("[a-z0-9]{1,16}");
    private static final Pattern RECEIPT = Pattern.compile("<!--\\s*thread:\\s*(\\d+)\\s*,\\s*no:\\s*(\\d+)\\s*-->");
    private PostingProtocol() {}
    public static String board(String value) {
        if (value == null || !BOARD.matcher(value).matches()) throw new IllegalArgumentException("Invalid board");
        return value;
    }
    public static String browserUrl(String board, long thread) {
        return "https://boards.4chan.org/" + board(board) + "/" + (thread > 0 ? "thread/" + thread : "");
    }
    public static boolean allowedCaptchaResource(String url) {
        // Official task images are inline PNGs; WebView intercepts data: URLs too.
        if (url != null && url.length() <= 4 * 1024 * 1024
                && (url.startsWith("data:image/png;base64,") || url.startsWith("data:image/jpeg;base64,")
                || url.startsWith("data:image/webp;base64,") || url.startsWith("data:image/gif;base64,"))) return true;
        try {
            URI uri = new URI(url);
            if (!"https".equals(uri.getScheme()) || uri.getUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)) return false;
            String host = uri.getHost();
            if (host == null) return false;
            if ("sys.4chan.org".equals(host)) return "/captcha".equals(uri.getPath()) || uri.getPath().startsWith("/cdn-cgi/");
            if ("s.4cdn.org".equals(host)) return uri.getPath().startsWith("/js/tcaptcha.") || uri.getPath().startsWith("/image/");
            return "hcaptcha.com".equals(host) || host.endsWith(".hcaptcha.com")
                    || "challenges.cloudflare.com".equals(host)
                    || "mcl.io".equals(host) || host.endsWith(".mcl.io")
                    || "spur.us".equals(host) || host.endsWith(".spur.us");
        } catch (Exception ignored) { return false; }
    }
    public static final class Answer {
        public final String board, challenge, response;
        public final long thread, expiresAt;
        public Answer(String board, long thread, String challenge, String response, long expiresAt) {
            this.board = board(board); this.thread = Math.max(0, thread);
            if (challenge == null || challenge.isEmpty() || challenge.length() > 4096
                    || response == null || response.length() > 8192) throw new IllegalArgumentException("Invalid CAPTCHA result");
            this.challenge = challenge; this.response = response; this.expiresAt = expiresAt;
        }
        public boolean usableFor(String targetBoard, long targetThread, long now) {
            return board.equals(targetBoard) && thread == Math.max(0, targetThread) && expiresAt > now;
        }
    }
    public static Answer answerFromWebView(String raw, String board, long thread, long now) {
        JsonElement value = JsonParser.parseString(raw);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) value = JsonParser.parseString(value.getAsString());
        JsonObject json = value.getAsJsonObject();
        if (!"ready".equals(json.get("state").getAsString())) throw new IllegalArgumentException("CAPTCHA incomplete or expired");
        Answer answer = new Answer(board, thread, json.get("challenge").getAsString(), json.get("response").getAsString(), json.get("expiresAt").getAsLong());
        if (!answer.usableFor(board, thread, now) || answer.expiresAt - now > 900_000) throw new IllegalArgumentException("CAPTCHA expired");
        return answer;
    }
    public static void addCaptcha(Map<String, Object> fields, Answer answer, String board, long thread, long now) {
        if (answer == null || !answer.usableFor(board, thread, now)) throw new IllegalArgumentException("CAPTCHA expired");
        fields.put("t-challenge", answer.challenge); fields.put("t-response", answer.response);
    }
    public static List<MultipartBody.Part> parts(Map<String, ?> fields) {
        List<MultipartBody.Part> parts = new ArrayList<>();
        for (Map.Entry<String, ?> entry : fields.entrySet()) {
            if (!entry.getKey().matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid form field");
            Object value = entry.getValue();
            if (value instanceof File) {
                File file = (File) value;
                if (!file.isFile() || !file.canRead()) throw new IllegalArgumentException("Attachment is no longer available");
                parts.add(MultipartBody.Part.createFormData(entry.getKey(), file.getName(), RequestBody.create(MediaType.parse("application/octet-stream"), file)));
            } else if (value != null) parts.add(MultipartBody.Part.createFormData(entry.getKey(), value.toString()));
        }
        return parts;
    }
    public static final class Receipt {
        public final long thread, post;
        public Receipt(long thread, long post) { this.thread = thread == 0 ? post : thread; this.post = post; }
    }
    public static Receipt parseResponse(Response<ResponseBody> response) throws IOException {
        ResponseBody body = response.isSuccessful() ? response.body() : response.errorBody();
        String html;
        try (ResponseBody closeable = body) { html = closeable == null ? "" : closeable.string(); }
        String error = FourChanHtmlParser.parsePostError(html);
        if (error != null) throw new IOException(error.substring(0, Math.min(1000, error.length())));
        if (!response.isSuccessful()) throw new IOException("Posting failed (HTTP " + response.code() + "). Your draft is preserved.");
        Matcher match = RECEIPT.matcher(html);
        if (match.find()) {
            try {
                long thread = Long.parseLong(match.group(1)), post = Long.parseLong(match.group(2));
                if (post > 0 && thread >= 0) return new Receipt(thread, post);
            } catch (NumberFormatException ignored) { }
        }
        throw new IOException("The site did not confirm publication. Check the thread before retrying; your draft is preserved.");
    }
}
