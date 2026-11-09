# X Age Restriction Fixer patches

X's Android app shows "age-restricted adult content" placeholders instead of some posts, the
same as the website. This folder is a patch bundle for [Morphe](https://morphe.software) that
fixes that inside the official X app (`com.twitter.android`), **without root**: you patch X once
with Morphe Manager on the phone and install the result over your current X.

These are third-party patches for use with Morphe; they are not made by the Morphe project.

## What it does

X sends age-restricted posts wrapped in a `TweetWithVisibilityResults` object, and the app shows
the placeholder for that wrapper. The patch adds a small piece of code to the app that rewrites
X's API responses before the app reads them, replacing each wrapper with the post inside it.
This is the same change the browser extension in this repository makes on the website.

In detail: one patch, **Show age-restricted posts**, hooks `OkHttpClient.Builder.build()` (the
network library X uses, which X does not obfuscate) and adds one interceptor to every HTTP
client the app creates. The interceptor only looks at JSON responses from `x.com` and
`twitter.com` hosts that contain `TweetWithVisibilityResults`; everything else passes through
untouched. Because it does not depend on X's own (renamed every release) code, the same patch
should work for every X version.

It logs under the tag `XAgeFix`: one line when it is installed in the app's process, and one
line for each response it rewrites.

Tested on 2026-11-09 with patches 1.0.0 on X 12.25.2-prod.01 (Samsung Galaxy S21, Android 15,
not rooted), patched on the PC and installed with `adb install -r` over the logged-in X: the
age-restricted post <https://x.com/takeru557/status/2033215789873180870> shows its image instead
of the placeholder, and the X accounts stayed logged in. Patching in Morphe Manager on the phone
has not been tried yet.

## Before you start: logins

Read this first, it is the one way to lose your X account session.

- **Never uninstall X and never clear its data** while you use a patched X. A patched X is
  signed with your own key, and since October 2025 X refuses fresh logins on re-signed apps
  (`LoginError.AttestationDenied`). An existing session keeps working; a new login usually
  does not.
- So the patched app must always be installed **as an update over** the X that is already
  logged in. That works only when it is signed with the **same key** as that X: for you, the key
  `~/phone-backups/keys/x-age-restriction-fixer.keystore` that signed the earlier LSPatch build.
- **Keep the APK you have installed now** as the rollback. For the current phone that is
  `~/phone-backups/x-lspatch/x-12.25.2-prod.01-standalone-487-lspatched.apk`. If the patched X
  misbehaves, `adb install -r` that file restores it (same key, so it installs over and keeps
  the session).
- Google sign-in does not work in any re-signed X. Push notifications behave as they did in your
  previous patched build.

## Install with Morphe Manager on the phone

1. **Install Morphe Manager** from <https://morphe.software> (or its
   [GitHub releases](https://github.com/MorpheApp/morphe-manager/releases)). On a Samsung phone,
   turn off **Auto Blocker** while installing (Settings → Security and privacy), and allow
   Manager to "install unknown apps" when Android asks.
2. **Import your signing key, before patching anything.** Copy
   `x-age-restriction-fixer.keystore` to the phone (for example into `Download/`). In Manager open
   **Settings → Signing key**, tap **Import** and pick the file. When it asks for credentials,
   enter the alias `xagefixer`, the password from `x-age-restriction-fixer.credentials.txt`, and
   the format **PKCS12**. The dialog then shows the imported key. Delete the copy from
   `Download/` afterwards. Without this step Manager signs with a new key of its own, and Android
   refuses to install the result over your X.
3. **Add the patch source.** In Manager open **Sources**, tap **+**, choose **Remote** and paste:

   ```
   https://raw.githubusercontent.com/brainage04/XAgeRestrictionFixer/main/android-patch/patches-bundle.json
   ```

   Manager then shows "X Age Restriction Fixer patches" and checks it for updates. (Instead of a
   remote source you can add a `.mpp` file you built yourself as a **Local** source.)

4. **Get X from APKMirror.** In Manager tap X and let it open the download page, or go to
   [APKMirror's X page](https://www.apkmirror.com/apk/x-corp/twitter/) yourself. Download the
   **APK bundle (`.apkm`)** for your phone (arm64-v8a), of the **same version as the X you have
   installed, or newer**: Android does not install an older version over a newer one. Your X
   version is under Settings → Apps → X.
5. **Patch.** In Manager pick X, pick the downloaded `.apkm`, check that **Show age-restricted
   posts** is selected, and start patching. It takes a few minutes.
6. **Install over the existing X.** Use the system installer and confirm the update. If Android
   says the app conflicts or asks you to uninstall first, **stop**: the key does not match
   (go back to step 2). Do not uninstall.
7. **Check it.** Open X; you should still be logged in. Posts that showed the placeholder now
   show normally. With USB debugging on, `adb logcat -s XAgeFix` shows "Installed the response
   interceptor" at app start and an "Unwrapped age-restricted posts in api.x.com/graphql/..."
   line for each response it fixed.

To see every response the interceptor considers (for troubleshooting), run
`adb shell setprop log.tag.XAgeFix DEBUG` and restart X.

## When X updates

Keep automatic updates for X off in the Play Store: Play cannot update a re-signed app, and if
it replaced it you would lose the patch and the session.

When you want a newer X, download its `.apkm` from APKMirror and repeat steps 5 to 7. Manager
keeps your key, so the new build installs over the old one. Manager does not tell you about new
X versions (the patch supports any version), only about new versions of these patches; when it
shows a patch update, re-patch with the **saved APK** it kept.

## Build and patch on the PC

You need Java 17 or newer, the Android SDK (`ANDROID_HOME`), and Git. Everything below runs in
this `android-patch/` folder.

1. **Morphe build tools, once.** Morphe publishes its Gradle plugin and patcher only to GitHub
   Packages, which asks for a token even to read. This script builds both from their public
   source instead and puts them in `~/.m2/repository` (rerun it when you change their versions
   in `settings.gradle.kts` or `gradle/libs.versions.toml`):

   ```sh
   ./scripts/install-morphe-tooling.sh
   ```

2. **Test and build the bundle:**

   ```sh
   ./gradlew --no-daemon :extensions:extension:testDebugUnitTest :patches:buildAndroid
   ```

   The bundle is `patches/build/libs/patches-<version>.mpp`.

3. **Patch X.** Download the Morphe CLI (`morphe-desktop-<version>-all.jar` from
   [morphe-desktop releases](https://github.com/MorpheApp/morphe-desktop/releases)), then:

   ```sh
   MORPHE_CLI=~/Downloads/morphe-desktop-2.1.1-all.jar \
     ./scripts/patch-x.sh x-<version>.apkm ~/phone-backups/x-morphe/x-<version>-patched.apk
   ```

   The input can be an `.apkm`, `.apks` or `.xapk` bundle or a single APK. The script signs with
   `~/phone-backups/keys/x-age-restriction-fixer.keystore`, reading the passwords from the
   credentials file next to it (`KEYSTORE` and `KEYSTORE_CREDENTIALS` override both).

4. **Install over the existing X:** `adb install -r ~/phone-backups/x-morphe/x-<version>-patched.apk`.
   Never `adb uninstall` X first.

A bundle you built can also go straight to Manager on the phone as a local source:

```sh
adb push patches/build/libs/patches-<version>.mpp /sdcard/Download/latest.mpp
adb shell content query --uri content://media/external/file --projection _id:_display_name
adb shell am start -a android.intent.action.VIEW -t application/vnd.ms-project \
  --grant-read-uri-permission -d content://media/external/file/<id> \
  -n app.morphe.manager/app.morphe.manager.MainActivity
```

## Publishing a new version of the patches

Manager reads `patches-bundle.json` in this folder on the `main` branch, and downloads the
`.mpp` it points to.

1. Raise `version` in `gradle.properties`, add an entry to `CHANGELOG.md`, and build (above).
2. Create a GitHub release tagged `patches-v<version>` with
   `patches/build/libs/patches-<version>.mpp` attached:

   ```sh
   gh release create patches-v<version> patches/build/libs/patches-<version>.mpp \
     --title "X Age Restriction Fixer patches <version>" --notes-file <notes>
   ```

3. Update `version`, `created_at` and `download_url` in `patches-bundle.json` and push it to
   `main`. Manager picks the new version up on its next check.

## Files

- `patches/src/main/kotlin/app/xagefixer/patches/agefix/`: the patch and its fingerprint for
  `OkHttpClient.Builder.build()`.
- `patches/src/main/kotlin/app/xagefixer/patches/shared/Constants.kt`: which app the patch is
  for (X, any version, `.apkm` on APKMirror).
- `extensions/extension/src/main/java/app/xagefixer/extension/`: the code added to X:
  `AgeRestrictionFix` (installs the interceptor), `VisibilityResultsInterceptor` (picks and
  rewrites responses) and `JsonNormalizer` (the JSON rewrite itself, without dependencies).
- `extensions/extension/src/test/`: unit tests, run against a real OkHttp client and a local
  test server.
- `scripts/install-morphe-tooling.sh`, `scripts/patch-x.sh`: the PC build and patch helpers.
- `patches-bundle.json`: what Morphe Manager reads for the remote source (version and download
  link). `CHANGELOG.md`: the release notes. Manager looks for a changelog only at the
  repository root, so it shows none for these patches; updates are still detected from
  `patches-bundle.json`.

## Troubleshooting

- **"App not installed" / signature conflict:** the patched APK is signed with a different key
  from the installed X. Import the keystore in Manager (step 2) and patch again. Do not uninstall.
- **No `XAgeFix` lines at all:** the patch was not applied; check that **Show age-restricted
  posts** was selected and that the patching log shows it as applied.
- **`Installed` line but no `Unwrapped` lines while placeholders still show:** turn on the debug
  log (above) and look at which hosts and content types the app's responses have.
- **Patching fails with `OkHttpClientBuilderBuildFingerprint`:** a new X version changed how it
  ships OkHttp; the patch needs an update.
- **Play Protect warns on install:** expected for any app not signed by its original developer.

## Licence

GPLv3 (see [LICENSE](LICENSE)), with the additional terms in [NOTICE](NOTICE) that come from the
[Morphe patches template](https://github.com/MorpheApp/morphe-patches-template) this project is
based on.
