package app.xagefixer.patches.agefix

import app.morphe.patcher.Fingerprint

/**
 * `okhttp3.OkHttpClient.Builder.build()`. X ships OkHttp unobfuscated, so the class and method are
 * matched by name; there is exactly one such method in the app.
 */
internal object OkHttpClientBuilderBuildFingerprint : Fingerprint(
    definingClass = "Lokhttp3/OkHttpClient\$Builder;",
    name = "build",
    returnType = "Lokhttp3/OkHttpClient;",
    parameters = listOf(),
)
