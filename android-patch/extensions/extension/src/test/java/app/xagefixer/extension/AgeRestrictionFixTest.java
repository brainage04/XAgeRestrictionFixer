package app.xagefixer.extension;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.Collections;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import org.junit.Test;

public final class AgeRestrictionFixTest {
    private static final Interceptor APP_INTERCEPTOR = chain -> chain.proceed(chain.request());

    @Test
    public void installsTheInterceptorFirstAndOnlyOnce() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(APP_INTERCEPTOR);

        AgeRestrictionFix.install(builder);
        AgeRestrictionFix.install(builder);

        assertEquals(2, builder.interceptors().size());
        assertSame(AgeRestrictionFix.INTERCEPTOR, builder.interceptors().get(0));
        assertSame(APP_INTERCEPTOR, builder.interceptors().get(1));
    }

    @Test
    public void derivedBuildersDoNotStackTheInterceptor() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder().addInterceptor(APP_INTERCEPTOR);
        AgeRestrictionFix.install(builder);
        OkHttpClient client = builder.build();

        // What the patched build() does for a client made with newBuilder().
        OkHttpClient.Builder derived = client.newBuilder();
        AgeRestrictionFix.install(derived);
        OkHttpClient derivedClient = derived.build();

        assertEquals(2, derivedClient.interceptors().size());
        assertSame(AgeRestrictionFix.INTERCEPTOR, derivedClient.interceptors().get(0));
        assertEquals(Collections.emptyList(), derivedClient.networkInterceptors());
    }

    @Test
    public void neverThrows() {
        // A null builder stands in for anything going wrong inside install().
        AgeRestrictionFix.install(null);
    }
}
