package app.xagefixer.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Runs real OkHttp clients against a local server. A fixed DNS answer makes X's and other hosts
 * resolve to that server, so the host filter sees the names it sees in the app.
 */
public final class VisibilityResultsInterceptorTest {
    private static final String WRAPPED = "{\"data\":{\"tweet_results\":{\"result\":"
            + "{\"__typename\":\"TweetWithVisibilityResults\",\"tweet\":{\"rest_id\":\"1\","
            + "\"legacy\":{\"possibly_sensitive_editable\":false}},"
            + "\"media_visibility_results\":{}}}}}";
    private static final String UNWRAPPED = "{\"data\":{\"tweet_results\":{\"result\":"
            + "{\"rest_id\":\"1\",\"legacy\":{\"possibly_sensitive_editable\":true},"
            + "\"__typename\":\"Tweet\"}}}}";

    private MockWebServer server;
    private OkHttpClient client;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start(InetAddress.getLoopbackAddress(), 0);
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .dns(hostname -> Collections.singletonList(InetAddress.getLoopbackAddress()));
        AgeRestrictionFix.install(builder);
        client = builder.build();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void unwrapsVisibilityResultsFromXApiJson() throws IOException {
        server.enqueue(json(WRAPPED));

        try (Response response = get("api.x.com", "/graphql/abc/HomeTimeline")) {
            String body = response.body().string();
            assertEquals(UNWRAPPED, body);
            assertEquals(String.valueOf(body.getBytes(StandardCharsets.UTF_8).length),
                    response.header("Content-Length"));
            assertEquals("application/json; charset=utf-8",
                    response.body().contentType().toString());
        }
    }

    @Test
    public void acceptsEveryXAndTwitterHost() throws IOException {
        for (String host : new String[] {"x.com", "api.x.com", "twitter.com", "api.twitter.com"}) {
            server.enqueue(json(WRAPPED));
            try (Response response = get(host, "/i/api/graphql/abc/TweetDetail")) {
                assertEquals(host, UNWRAPPED, response.body().string());
            }
        }
    }

    @Test
    public void rewritesGzipBodiesAfterOkHttpDecodesThem() throws IOException {
        server.enqueue(json("").setBody(gzip(WRAPPED)).setHeader("Content-Encoding", "gzip"));

        try (Response response = get("api.x.com", "/graphql/abc/UserMedia")) {
            assertEquals(UNWRAPPED, response.body().string());
        }
    }

    @Test
    public void leavesOtherHostsUntouched() throws IOException {
        for (String host : new String[] {"example.com", "notx.com", "x.com.example.org"}) {
            server.enqueue(json(WRAPPED));
            try (Response response = get(host, "/graphql/abc/HomeTimeline")) {
                assertEquals(host, WRAPPED, response.body().string());
            }
        }
    }

    @Test
    public void leavesNonJsonAndStillEncodedBodiesUntouched() throws IOException {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html").setBody(WRAPPED));
        try (Response response = get("api.x.com", "/graphql/abc/HomeTimeline")) {
            assertEquals(WRAPPED, response.body().string());
        }

        // OkHttp itself decodes only gzip; a body still encoded must not be parsed.
        server.enqueue(json(WRAPPED).setHeader("Content-Encoding", "br"));
        try (Response response = get("api.x.com", "/graphql/abc/HomeTimeline")) {
            assertEquals("br", response.header("Content-Encoding"));
            assertEquals(WRAPPED, response.body().string());
        }
    }

    @Test
    public void passesBodiesWithoutWrappersThroughUnchanged() throws IOException {
        String plain = "{\"data\":{\"__typename\":\"Tweet\",\"rest_id\":\"2\"}}";
        server.enqueue(json(plain).setHeader("X-Test", "kept"));

        try (Response response = get("api.x.com", "/graphql/abc/HomeTimeline")) {
            assertEquals(plain, response.body().string());
            assertEquals("kept", response.header("X-Test"));
            assertEquals(200, response.code());
        }
    }

    @Test
    public void passesInvalidJsonContainingTheMarkerThroughUnchanged() throws IOException {
        String invalid = "{\"__typename\":\"TweetWithVisibilityResults\",\"tweet\":";
        server.enqueue(json(invalid));

        try (Response response = get("api.x.com", "/graphql/abc/HomeTimeline")) {
            assertEquals(invalid, response.body().string());
        }
    }

    @Test
    public void passesNoContentResponsesThrough() throws IOException {
        server.enqueue(json("").setResponseCode(204));

        try (Response response = get("api.x.com", "/1.1/jot/client_event")) {
            assertEquals(204, response.code());
            assertEquals("", response.body().string());
        }
    }

    @Test
    public void rewriteReturnsTheSameArrayWhenNothingChanges() {
        byte[] noMarker = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] invalid = "{\"__typename\":\"TweetWithVisibilityResults\"".getBytes(StandardCharsets.UTF_8);

        assertSame(noMarker, VisibilityResultsInterceptor.rewrite(noMarker));
        assertSame(invalid, VisibilityResultsInterceptor.rewrite(invalid));
        assertEquals(UNWRAPPED, new String(VisibilityResultsInterceptor.rewrite(
                WRAPPED.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
    }

    @Test
    public void keepsNonAsciiTextIntact() throws IOException {
        String wrapped = "{\"__typename\":\"TweetWithVisibilityResults\","
                + "\"tweet\":{\"full_text\":\"caf\u00e9 \u2013 \ud83d\ude00\"}}";
        server.enqueue(json(wrapped));

        try (Response response = get("api.x.com", "/graphql/abc/TweetDetail")) {
            assertEquals("{\"full_text\":\"caf\u00e9 \u2013 \ud83d\ude00\",\"__typename\":\"Tweet\"}",
                    response.body().string());
        }
    }

    @Test
    public void isXHostMatchesOnlyXAndTwitterDomains() {
        assertEquals(true, VisibilityResultsInterceptor.isXHost("x.com"));
        assertEquals(true, VisibilityResultsInterceptor.isXHost("api.x.com"));
        assertEquals(true, VisibilityResultsInterceptor.isXHost("api.twitter.com"));
        assertEquals(false, VisibilityResultsInterceptor.isXHost("twimg.com"));
        assertEquals(false, VisibilityResultsInterceptor.isXHost("abs.twimg.com"));
        assertEquals(false, VisibilityResultsInterceptor.isXHost("evilx.com"));
        assertEquals(false, VisibilityResultsInterceptor.isXHost("x.com.evil.org"));
    }

    @Test
    public void passesHeadResponsesThrough() throws IOException {
        // The Content-Length of a HEAD response describes a body that is never sent, so
        // ResponseBody.bytes() would throw on it; the interceptor must not read it.
        server.enqueue(json(WRAPPED));

        Request head = new Request.Builder()
                .url("http://api.x.com:" + server.getPort() + "/graphql/abc/HomeTimeline")
                .head()
                .build();
        try (Response response = client.newCall(head).execute()) {
            assertEquals(200, response.code());
            assertEquals(String.valueOf(WRAPPED.length()), response.header("Content-Length"));
            assertEquals("", response.body().string());
        }
    }

    private Response get(String host, String path) throws IOException {
        Request request = new Request.Builder()
                .url("http://" + host + ":" + server.getPort() + path)
                .build();
        return client.newCall(request).execute();
    }

    private static MockResponse json(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(body);
    }

    private static Buffer gzip(String text) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream out = new GZIPOutputStream(bytes)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return new Buffer().write(bytes.toByteArray());
    }
}
