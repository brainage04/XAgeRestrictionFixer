package app.xagefixer.extension;

import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

/**
 * Entry point of the age-restriction fix. The patch calls {@link #install} at the start of every
 * {@code OkHttpClient.Builder.build()} in X, before the builder's interceptor list is copied into
 * the new client.
 */
@SuppressWarnings("unused")
public final class AgeRestrictionFix {
    /** Log tag: {@code adb logcat -s XAgeFix}. */
    static final String TAG = "XAgeFix";

    static final Interceptor INTERCEPTOR = new VisibilityResultsInterceptor();

    private static final AtomicBoolean INSTALL_LOGGED = new AtomicBoolean();

    private AgeRestrictionFix() {}

    /**
     * Adds the interceptor as the outermost one of {@code builder}, unless it is there already
     * ({@code newBuilder()} copies the list, so a derived builder reaches here with it). Being
     * outermost, it sees the body after every decoding interceptor (Brotli included) has run, the
     * same body X's parsers read. Runs for every client in the app, so it is cheap and never throws.
     */
    public static void install(OkHttpClient.Builder builder) {
        try {
            List<Interceptor> interceptors = builder.interceptors();
            if (interceptors.contains(INTERCEPTOR)) {
                return;
            }
            interceptors.add(0, INTERCEPTOR);
            if (INSTALL_LOGGED.compareAndSet(false, true)) {
                Log.i(TAG, "Installed the response interceptor");
            }
        } catch (Throwable throwable) {
            Log.w(TAG, "Could not install the response interceptor", throwable);
        }
    }
}
