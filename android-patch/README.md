# Android patch

X's Android app shows the same age-restricted placeholder as the website. The fix here is an
Xposed module that repairs the data shape the app receives; because the app is patched
rather than the system, this works **without root**.

Nothing is downloaded from anyone else: the patched app is built on your computer from the
copy of X already installed on your phone, with the module embedded, and signed with a key
you keep.

## What you need

- A phone with X installed, USB debugging on, and `adb` working (`adb devices` lists it).
- Java 17 or newer on the computer.
- The Android SDK, for building the module (only if you use the Gradle path below).
- Two tools, downloaded once:
  - [LSPatch](https://github.com/JingMatrix/LSPatch/releases) → `lspatch.jar`
  - [APKEditor](https://github.com/REAndroid/APKEditor/releases) → `APKEditor.jar`

Put both jars in one directory, for example `~/.local/share/lspatch/`.

## One command

```sh
./android-patch/build-lspatch.sh
```

The script:

1. builds the module APK (`app.xagefixer`);
2. pulls X's APK splits from the connected phone;
3. merges them into one APK, keeping the original signature information;
4. embeds the module and signs the result with your key.

It prints the patched APK path and the two `adb` commands that install it.

Override anything with environment variables:

| Variable                           | Default                     | Meaning                                          |
| ---------------------------------- | --------------------------- | ------------------------------------------------ |
| `ADB_SERIAL`                       | first device `adb` lists    | which phone to read X from                       |
| `WORK_DIR`                         | `~/phone-backups/x-lspatch` | where the APKs are written                       |
| `LSPATCH_JAR`, `APKEDITOR_JAR`     | `~/.local/share/lspatch/…`  | the two tools                                    |
| `KEYSTORE`, `KEYSTORE_CREDENTIALS` | `~/phone-backups/keys/…`    | the signing key and its password file            |
| `SIGBYPASS`                        | `2`                         | LSPatch signature-bypass level                   |
| `SKIP_BUILD`                       | `0`                         | set to `1` to reuse the module APK already built |

### The signing key

LSPatch re-signs the app, so the patched X is not the same app as the Play one: it must be
installed fresh, and it cannot use Google sign-in (use username and password with 2FA).

Keep the same key for every future patch and later patches install _over_ the current app
instead of replacing it. The script expects a keystore plus a small credentials file:

```sh
keytool -genkeypair -keystore ~/phone-backups/keys/x-age-restriction-fixer.keystore \
  -alias xagefixer -keyalg RSA -keysize 4096 -validity 10950 \
  -storepass 'CHOOSE-A-PASSWORD' -keypass 'CHOOSE-A-PASSWORD' \
  -dname "CN=X Age Restriction Fixer"
```

and a `…credentials.txt` next to it with `storepass:`, `alias:` and `keypass:` lines (the
script reads those three values). Losing this key is not fatal, but the next patch then
needs a fresh install and a fresh login.

## Installing the patched app

```sh
adb uninstall com.twitter.android
adb install /path/to/x-<version>-standalone-<n>-lspatched.apk
```

Then open X and sign in. The first launch asks for the app's usual permissions.

## After an X update

X updates break the patch, so keep Play auto-update off for X. When you do want a new
version: update X, run `build-lspatch.sh` again, then `adb install -r` the new APK — same
key, so it installs over the old one and keeps you signed in.

If you would rather not keep the Play build around, the script keeps a copy of the stock
APKs it pulled (`x-<version>-stock/`). Installing those again is the rollback: uninstall the
patched app, then `adb install-multiple x-<version>-stock/*.apk`.

## Checking that it works

The patched app logs what it loaded at startup:

```sh
adb logcat -c && adb shell monkey -p com.twitter.android -c android.intent.category.LAUNCHER 1
adb logcat -d | grep -E 'LSPatch|VectorLegacyBridge'
```

A healthy start shows `LSPatch-MetaLoader: Bootstrap loader from embedment`, then
`VectorLegacyBridge: Loading legacy module app.xagefixer`, then
`Loading class app.xagefixer.XAgeRestrictionFixerHook`.

The real test is a post that shows the placeholder in the stock app: it should render
normally in the patched one, on the same account.

## Troubleshooting

- **"already an LSPatch build"** — the script refuses to patch a phone whose X is the
  patched build, because that would embed the loader twice. Reinstall the stock APKs first,
  or pass `STOCK_DIR=` pointing at the stock copy kept from an earlier run.
- **Play Protect warning on install** — expected for any self-signed app; allow it.
- **Google sign-in fails** — expected; use username and password with 2FA.
- **The app crashes at startup** — reinstall the stock APKs and re-run the script; if it
  persists, capture `adb logcat` around the crash and open an issue.
