extension {
    name = "extensions/extension.mpe"
}

android {
    namespace = "app.xagefixer.extension"

    testOptions {
        // android.util.Log returns defaults in JVM unit tests instead of throwing.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    // X ships OkHttp unobfuscated; the extension runs against the app's own copy.
    compileOnly(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp)
    testImplementation(libs.okhttp.mockwebserver)
}
