package app.xagefixer.extension;

import static app.xagefixer.extension.AgeRestrictionFix.TAG;

import android.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Rewrites X API JSON responses so that every {@code TweetWithVisibilityResults} wrapper becomes
 * its inner {@code Tweet}, the same transform the browser extension applies (src/page-probe.js).
 *
 * <p>Only OkHttp's public API is used: X ships OkHttp unobfuscated but Okio obfuscated, so the body
 * is read with {@link ResponseBody#bytes()} and rebuilt with {@code ResponseBody.create(MediaType,
 * byte[])}, never through Okio types.
 */
final class VisibilityResultsInterceptor implements Interceptor {
    /** Larger bodies are passed through: X's timeline pages are well below this. */
    static final long MAX_BODY_BYTES = 16L * 1024 * 1024;

    private static final byte[] MARKER =
            "TweetWithVisibilityResults".getBytes(StandardCharsets.US_ASCII);

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());

        boolean candidate;
        try {
            candidate = isCandidate(response);
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not inspect a response", throwable);
            return response;
        }
        if (!candidate) {
            return response;
        }

        ResponseBody body = response.body();
        MediaType contentType = body.contentType();
        // Reads and closes the original body; from here on a rebuilt body is always returned.
        byte[] raw = body.bytes();
        byte[] rewritten = rewrite(raw);

        Response.Builder builder = response.newBuilder()
                .body(ResponseBody.create(contentType, rewritten));
        if (rewritten != raw) {
            builder.header("Content-Length", Integer.toString(rewritten.length));
            HttpUrl url = response.request().url();
            Log.i(TAG, "Unwrapped age-restricted posts in " + url.host() + url.encodedPath()
                    + " (" + raw.length + " -> " + rewritten.length + " bytes)");
        }
        return builder.build();
    }

    /**
     * Whether {@code response} is an uncompressed JSON body of bounded size from an X API host.
     * The host check comes first: every OkHttp client in the app gets this interceptor, the
     * third-party SDKs' ones included. Responses that carry no body (HEAD, 1xx, 204, 304) are
     * skipped, as {@link ResponseBody#bytes()} rejects a Content-Length header with no content.
     */
    static boolean isCandidate(Response response) {
        HttpUrl url = response.request().url();
        if (!isXHost(url.host())) {
            return false;
        }
        ResponseBody body = response.body();
        if (body == null || !hasBody(response)) {
            return false;
        }
        MediaType contentType = body.contentType();
        if (contentType == null || !contentType.subtype().contains("json")) {
            return false;
        }
        String encoding = response.header("Content-Encoding");
        if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
            return false;
        }
        long length = body.contentLength();
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Candidate " + url.host() + url.encodedPath() + " type=" + contentType
                    + " length=" + length);
        }
        return length <= MAX_BODY_BYTES;
    }

    private static boolean hasBody(Response response) {
        int code = response.code();
        return !response.request().method().equals("HEAD")
                && (code < 100 || code >= 200) && code != 204 && code != 304;
    }

    static boolean isXHost(String host) {
        return host.equals("x.com") || host.endsWith(".x.com")
                || host.equals("twitter.com") || host.endsWith(".twitter.com");
    }

    /**
     * Returns the normalised body, or {@code raw} itself when nothing changed or the body is not
     * valid JSON. Never throws.
     */
    static byte[] rewrite(byte[] raw) {
        if (indexOf(raw, MARKER) < 0) {
            return raw;
        }
        try {
            String text = new String(raw, StandardCharsets.UTF_8);
            String normalized = JsonNormalizer.normalize(text);
            // normalize() returns its argument when it changed nothing.
            return normalized == text ? raw : normalized.getBytes(StandardCharsets.UTF_8);
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not rewrite a response", throwable);
            return raw;
        }
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int start = 0; start <= haystack.length - needle.length; start += 1) {
            for (int offset = 0; offset < needle.length; offset += 1) {
                if (haystack[start + offset] != needle[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }
}
