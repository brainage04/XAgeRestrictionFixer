package app.xagefixer.patches.agefix

import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.patch.patch
import app.xagefixer.patches.shared.Constants.COMPATIBILITY_X

private const val EXTENSION_CLASS = "Lapp/xagefixer/extension/AgeRestrictionFix;"

@Suppress("unused")
val ageRestrictionFixPatch = patch(
    name = "Show age-restricted posts",
    description = "Replaces the age-verification placeholder with the post by unwrapping " +
        "TweetWithVisibilityResults in X's API responses before the app parses them.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_X)

    extendWith("extensions/extension.mpe")

    execute {
        // p0 is the builder; this runs before build() copies its interceptor list into the client.
        OkHttpClientBuilderBuildFingerprint.method.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS->install(Lokhttp3/OkHttpClient\$Builder;)V",
        )
    }
}
