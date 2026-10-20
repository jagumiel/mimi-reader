package com.emogoth.android.phone.mimi.fourchan;

import com.emogoth.android.phone.mimi.fourchan.api.FourChanPostApi;
import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Test;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import static org.junit.Assert.*;

public class PostingProtocolTest {
    private String answer(String state, String response, long deadline) {
        return "{\"state\":\"" + state + "\",\"challenge\":\"fixture-only\",\"response\":\"" + response + "\",\"expiresAt\":" + deadline + "}";
    }
    private Response<ResponseBody> response(int code, String body) {
        ResponseBody payload = ResponseBody.create(MediaType.parse("text/html"), body);
        return code == 200 ? Response.success(payload) : Response.error(code, payload);
    }
    @Test public void decodesTheWebViewJsonStringWithoutRecaptchaFields() {
        PostingProtocol.Answer proof = PostingProtocol.answerFromWebView(new Gson().toJson(answer("ready", "123", 10_000)), "g", 42, 100);
        Map<String, Object> fields = new HashMap<>();
        PostingProtocol.addCaptcha(fields, proof, "g", 42, 101);
        assertEquals("fixture-only", fields.get("t-challenge")); assertEquals("123", fields.get("t-response"));
        assertFalse(fields.containsKey("g-recaptcha-response"));
    }
    @Test public void permitsAnOfficialNoTaskChallengeWithAnEmptyResponse() {
        assertEquals("", PostingProtocol.answerFromWebView(answer("ready", "", 1000), "g", 0, 100).response);
    }
    @Test public void rejectsPendingAndExpiredResults() {
        assertThrows(RuntimeException.class, () -> PostingProtocol.answerFromWebView(answer("pending", "1", 1000), "g", 0, 100));
        assertThrows(RuntimeException.class, () -> PostingProtocol.answerFromWebView(answer("ready", "1", 100), "g", 0, 100));
    }
    @Test public void rejectsMalformedOrImplausiblyLongLivedResults() {
        for (String raw : new String[]{"null", "{}", "false", "not JSON", answer("ready", "1", Long.MAX_VALUE)}) {
            assertThrows(RuntimeException.class, () -> PostingProtocol.answerFromWebView(raw, "g", 0, 100));
        }
    }
    @Test public void proofIsBoundToItsBoardAndThread() {
        PostingProtocol.Answer proof = new PostingProtocol.Answer("g", 42, "fixture", "1", 1000);
        assertFalse(proof.usableFor("a", 42, 100)); assertFalse(proof.usableFor("g", 43, 100));
        assertThrows(IllegalArgumentException.class, () -> PostingProtocol.addCaptcha(new HashMap<>(), proof, "g", 0, 100));
    }
    @Test public void validatesBoardNamesAndBrowserTargets() {
        assertEquals("https://boards.4chan.org/g/thread/42", PostingProtocol.browserUrl("g", 42));
        assertEquals("https://boards.4chan.org/3/", PostingProtocol.browserUrl("3", 0));
        for (String board : new String[]{"../g", "g?x=1", "", "g/#", "G"}) assertThrows(IllegalArgumentException.class, () -> PostingProtocol.board(board));
    }
    @Test public void captchaNetworkPolicyAllowsOnlyVerificationResources() {
        assertTrue(PostingProtocol.allowedCaptchaResource(PostingProtocol.WIDGET_URL));
        assertTrue(PostingProtocol.allowedCaptchaResource("data:image/png;base64,aGVsbG8="));
        assertFalse(PostingProtocol.allowedCaptchaResource("data:text/html,<script>alert(1)</script>"));
        assertFalse(PostingProtocol.allowedCaptchaResource("data:image/svg+xml;base64,aGVsbG8="));
        assertTrue(PostingProtocol.allowedCaptchaResource("https://sys.4chan.org/captcha?board=g&ext=1"));
        assertTrue(PostingProtocol.allowedCaptchaResource("https://newassets.hcaptcha.com/captcha/v1"));
        assertTrue(PostingProtocol.allowedCaptchaResource("https://challenges.cloudflare.com/turnstile/v0/api.js"));
        assertTrue(PostingProtocol.allowedCaptchaResource("https://js.mcl.io/d/mcl.js"));
        assertTrue(PostingProtocol.allowedCaptchaResource("https://api.mcl.io/"));
        assertTrue(PostingProtocol.allowedCaptchaResource("https://api.spur.us/"));
        assertFalse(PostingProtocol.allowedCaptchaResource("https://mcl.io.evil.test/"));
        assertFalse(PostingProtocol.allowedCaptchaResource("https://evil-spur.us/"));
        assertFalse(PostingProtocol.allowedCaptchaResource("http://js.mcl.io/d/mcl.js"));
        for (String url : new String[]{"https://sys.4chan.org/g/post", "https://sys.4chan.org.evil.test/captcha", "http://sys.4chan.org/captcha", "file:///etc/passwd", "content://private", "https://sys.4chan.org:444/captcha", "https://user:password@sys.4chan.org/captcha", "https://evil-hcaptcha.com/", "https://captcha.mimi.invalid/secret"}) assertFalse(url, PostingProtocol.allowedCaptchaResource(url));
    }
    @Test public void parsesReplyReceiptWithLongIdentifiers() throws Exception {
        PostingProtocol.Receipt receipt = PostingProtocol.parseResponse(response(200, "<!-- thread:3000000001,no:3000000002 -->"));
        assertEquals(3000000001L, receipt.thread); assertEquals(3000000002L, receipt.post);
    }
    @Test public void usesThePostIdForANewThreadReceipt() throws Exception {
        assertEquals(42, PostingProtocol.parseResponse(response(200, "<!-- thread:0,no:42 -->")).thread);
    }
    @Test public void explicitServerErrorTakesPrecedenceOverReceipt() {
        IOException error = assertThrows(IOException.class, () -> PostingProtocol.parseResponse(response(200, "<span id='errmsg'>CAPTCHA expired</span><!-- thread:1,no:2 -->")));
        assertEquals("CAPTCHA expired", error.getMessage());
    }
    @Test public void neverTreatsUnknownOrBlockedHtmlAsSuccess() {
        for (String body : new String[]{"", "<h1>Verification Required</h1>", "<!-- thread:1,no:0 -->", "<!-- thread:0,no:999999999999999999999 -->"}) assertThrows(IOException.class, () -> PostingProtocol.parseResponse(response(200, body)));
        assertThrows(IOException.class, () -> PostingProtocol.parseResponse(response(403, "<!-- thread:1,no:2 -->")));
    }
    @Test public void missingAttachmentsAreNotSilentlyDropped() {
        Map<String, Object> fields = new HashMap<>(); fields.put("upfile", new File("definitely-not-an-upload-fixture.bin"));
        assertThrows(IllegalArgumentException.class, () -> PostingProtocol.parts(fields));
    }
    @Test public void rejectsInjectedMultipartFieldNames() {
        Map<String, Object> fields = new HashMap<>(); fields.put("com\r\nInjected: value", "no");
        assertThrows(IllegalArgumentException.class, () -> PostingProtocol.parts(fields));
    }
    @Test public void mockServerReceivesTextCaptchaAndAttachmentInOneExplicitRequest() throws Exception {
        File file = Files.createTempFile("posting-fixture", ".png").toFile();
        Files.write(file.toPath(), new byte[]{1, 2, 3});
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("<!-- thread:42,no:43 -->")); server.start();
            FourChanPostApi api = new Retrofit.Builder().baseUrl(server.url("/"))
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create()).build().create(FourChanPostApi.class);
            Map<String, Object> fields = new HashMap<>(); fields.put("mode", "regist"); fields.put("com", "Unicode: 日本語"); fields.put("resto", "42"); fields.put("upfile", file);
            PostingProtocol.addCaptcha(fields, new PostingProtocol.Answer("g", 42, "fixture-only", "123", 1000), "g", 42, 100);
            assertEquals(43, PostingProtocol.parseResponse(api.post("g", PostingProtocol.parts(fields)).blockingGet()).post);
            okhttp3.mockwebserver.RecordedRequest request = server.takeRequest(2, TimeUnit.SECONDS);
            assertEquals("/g/post", request.getPath()); assertEquals("POST", request.getMethod());
            String body = request.getBody().readUtf8();
            assertTrue(body.contains("name=\"t-challenge\"")); assertTrue(body.contains("fixture-only"));
            assertTrue(body.contains("name=\"t-response\"")); assertTrue(body.contains("Unicode: 日本語"));
            assertTrue(body.contains("filename=\"" + file.getName())); assertFalse(body.contains("g-recaptcha-response"));
            assertEquals(1, server.getRequestCount());
        } finally { assertTrue(file.delete()); }
    }
}
