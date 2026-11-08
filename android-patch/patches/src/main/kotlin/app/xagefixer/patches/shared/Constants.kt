package app.xagefixer.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {
    val COMPATIBILITY_X = Compatibility(
        name = "X",
        packageName = "com.twitter.android",
        // X is an .apkm bundle on APKMirror; Manager only sends users there when this matches.
        apkFileType = ApkFileType.APKM,
        appIconColor = 0x000000,
        // Any X version: the patch anchors only on OkHttp's public API, not on X's own
        // obfuscated classes. Tested with 12.25.2-prod.01.
        targets = listOf(AppTarget(version = null)),
    )
}
