# X Age Restriction Fixer

Chrome Manifest V3 extension that fixes X's broken age-restricted media rendering for already age-verified accounts.

This extension does not scrape private APIs or remove X's access checks. It normalizes X's own frontend API response shape so X's native image, GIF, video, and quote renderers can run for accounts that have already passed X's access checks.

See [PRIVACY.md](./PRIVACY.md) for the privacy policy.

## Install locally

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Click **Load unpacked**.
4. Select this folder.

## Development

The development tooling requires Node.js `^20.19.0`, `^22.13.0`, or `>=24`.

Install the development dependencies and run the lint and formatting checks with:

```sh
npm install
npm run check
```

Use `npm run lint` or `npm run format:check` to run one check independently. Run `npm run format` to apply Prettier formatting.

The extension itself has no build step. After editing files, reload the unpacked extension in `chrome://extensions`, or run:

```sh
npm run reload:extension
```

That helper requires Chrome to already be running with `--remote-debugging-port=9222`.

For manual debugging, `npm run compare:x-dom -- label=https://x.com/user/status/123` captures a compact DOM snapshot for one or more direct status URLs. It does not include bundled test cases or navigate anywhere unless you provide URLs.

To build the Chrome Web Store upload package, run:

```sh
npm run build:zip
```

This writes `dist/x-age-restriction-fixer-1.0.1.zip` with only the runtime extension files.

To regenerate the Chrome Web Store listing images, run:

```sh
npm run render:store-assets
```

That command uses local headless Chrome to render the sanitized listing pages in `store-assets/`.

## Android patch

The repository also contains an Xposed module for the official Android X app. It hooks the app's response-body and JSON parser boundaries, applies the same `TweetWithVisibilityResults` normalization, and leaves X's native media renderers in control.

The module is loaded **without root**: the X APK is patched with [LSPatch](https://github.com/JingMatrix/LSPatch), which embeds the module and the hook engine into that one app. Nothing else on the device is modified, and no bootloader unlock or system-level framework is involved.

Build the module, then patch a merged copy of the installed X APK:

```sh
./android-patch/gradlew -p android-patch testDebugUnitTest assembleDebug
./android-patch/build-lspatch.sh            # see the script for env overrides
```

The script pulls X's split APKs from the device, merges them, embeds the module, and signs the result with the stable keystore so a later patch installs over the current one. Installing requires uninstalling the Play build first (different signature), after which X has to be logged into again — Google sign-in will not work in a re-signed app, so use username/password with 2FA.

Because the patch is applied to an APK, each X update needs a re-run of the script; the app's own updates must stay disabled. The stock split APKs are kept next to the patch as the rollback path.

The module APK itself is written to `android-patch/app/build/outputs/apk/debug/app-debug.apk`. The module does not request network permission and does not log, store, or transmit response data.

The Android implementation is intentionally limited to the official `com.twitter.android` main process and to responses containing X's visibility wrapper. It is intended for accounts that already pass X's access checks; it does not obtain access or credentials.

## Behavior

- Runs only on `x.com` and `twitter.com` in the browser extension.
- Runs at `document_start` in the browser extension.
- Runs only in the official `com.twitter.android` process in the Android module.
- Converts `TweetWithVisibilityResults` wrappers into the wrapped `Tweet` objects before X consumes API JSON.
- Lets X render media with its own native components.
- Does not add custom media cards, video controls, or diagnostic popups.
- Does not store or display cookies, auth headers, request bodies, or API payloads.

## Files

- `manifest.json`: Chrome Manifest V3 manifest.
- `src/page-probe.js`: Browser main-world API normalization.
- `android-patch/app/src/main/java/app/xagefixer/XAgeRestrictionFixerHook.java`: Xposed entry point and Android parser hooks.
- `android-patch/app/src/main/java/app/xagefixer/JsonNormalizer.java`: Android JSON normalization.
- `android-patch/app/src/main/AndroidManifest.xml`: Module metadata and launcher activity.
- `android-patch/app/src/main/assets/xposed_init`: Xposed entry point declaration.
- `android-patch/`: Android Gradle project.

## Extension files

- `icons/`: Extension icon source and generated PNG assets.
- `scripts/build-extension-zip.js`: Chrome Web Store ZIP builder.
- `scripts/compare-x-dom.js`: Optional CDP helper for manually comparing direct status DOM snapshots.
- `scripts/render-store-assets.js`: Local Chrome renderer for Web Store listing images.
- `scripts/reload-extension.js`: Chrome DevTools Protocol helper for reloading the unpacked extension.
- `store-assets/`: Sanitized listing captures, editable listing pages, and generated Chrome Web Store images.
