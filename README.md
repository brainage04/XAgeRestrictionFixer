# X Age Restriction Fixer

Some posts on X never show their picture, GIF or video. Instead you get an age-restricted
placeholder, even though you have already confirmed your age and the same post opens fine
elsewhere. This fixes that, in Chrome and in the Android app.

It does not unlock anything you do not already have access to. It does not touch your
account, your login, or X's servers: it repairs the data shape X's own page or app receives,
so X's normal media players can run.

## Install in Chrome

There is no Chrome Web Store listing yet, so it is installed by hand, which takes about a
minute:

1. Click the green **Code** button on this page, choose **Download ZIP**, and unzip the
   folder somewhere you will keep it.
2. In Chrome, type `chrome://extensions` in the address bar and press Enter.
3. Turn on **Developer mode** with the switch in the top-right corner.
4. Drag the unzipped folder onto that page. The extension appears in the list, switched on.
5. Open, or reload, x.com.

There is nothing else to configure: no account, no settings, no permissions to grant.
To remove it later, click **Remove** on the same page.

## Install in the Android app

There is no one-tap install for the Android app. X's own app has to be patched on your
phone, which needs a computer, a USB cable and about ten minutes. The full instructions,
written out step by step, are in [android-patch/README.md](android-patch/README.md).

If that is more than you want to do, use X in a browser with the extension above instead —
it fixes the same thing.

## For developers

### Development

The development tooling requires Node.js `^20.19.0`, `^22.13.0`, or `>=24`.

Install the development dependencies and run the lint and formatting checks with:

```sh
npm install
npm run check
```

Use `npm run lint` or `npm run format:check` to run one check independently. Run
`npm run format` to apply Prettier formatting.

The extension itself has no build step. After editing files, reload the unpacked extension
in `chrome://extensions`, or run:

```sh
npm run reload:extension
```

That helper requires Chrome to already be running with `--remote-debugging-port=9222`.

For manual debugging, `npm run compare:x-dom -- label=https://x.com/user/status/123`
captures a compact DOM snapshot for one or more direct status URLs. It does not include
bundled test cases or navigate anywhere unless you provide URLs.

To build the Chrome Web Store upload package, run:

```sh
npm run build:zip
```

This writes `dist/x-age-restriction-fixer-1.0.1.zip` with only the runtime extension files.

To regenerate the Chrome Web Store listing images, run:

```sh
npm run render:store-assets
```

That command uses local headless Chrome to render the sanitized listing pages in
`store-assets/`.

### Android patch

The repository also contains an Xposed module for the official Android X app. It hooks the
app's response-body and JSON parser boundaries, applies the same
`TweetWithVisibilityResults` normalization, and leaves X's native media renderers in
control.

The module is loaded **without root**: the X APK is patched with
[LSPatch](https://github.com/JingMatrix/LSPatch), which embeds the module and the hook
engine into that one app. Nothing else on the device is modified, and no bootloader unlock
or system-level framework is involved.

```sh
npm run build:android   # build and unit-test the module
npm run build:lspatch   # pull X from the device, merge, embed the module, sign
```

Installing the result replaces the Play build, so X is signed in again afterwards with a
username and password (Google sign-in cannot work in a re-signed app). Each X update needs
a re-run of `build:lspatch`; keep Play auto-updates off for X. See
[android-patch/README.md](android-patch/README.md) for the details, including the signing
key, the rollback copy of the stock APKs, and what the patched app logs on startup.

### Behavior

- Runs only on `x.com` and `twitter.com` in the browser extension.
- Runs at `document_start` in the browser extension.
- Runs only in the official `com.twitter.android` process in the Android module.
- Converts `TweetWithVisibilityResults` wrappers into the wrapped `Tweet` objects before X
  consumes API JSON.
- Lets X render media with its own native components.
- Does not add custom media cards, video controls, or diagnostic popups.
- Does not store or display cookies, auth headers, request bodies, or API payloads.

### Files

- `manifest.json`: Chrome Manifest V3 manifest.
- `src/page-probe.js`: Browser main-world API normalization.
- `android-patch/app/src/main/java/app/xagefixer/XAgeRestrictionFixerHook.java`: Xposed
  entry point and Android parser hooks.
- `android-patch/app/src/main/java/app/xagefixer/JsonNormalizer.java`: Android JSON
  normalization.
- `android-patch/app/src/main/AndroidManifest.xml`: Module metadata and launcher activity.
- `android-patch/app/src/main/assets/xposed_init`: Xposed entry point declaration.
- `android-patch/build-lspatch.sh`: builds and signs the patched Android app.
- `android-patch/`: Android Gradle project.

### Extension files

- `icons/`: Extension icon source and generated PNG assets.
- `scripts/build-extension-zip.js`: Chrome Web Store ZIP builder.
- `scripts/compare-x-dom.js`: Optional CDP helper for manually comparing direct status DOM
  snapshots.
- `scripts/render-store-assets.js`: Local Chrome renderer for Web Store listing images.
- `scripts/reload-extension.js`: Chrome DevTools Protocol helper for reloading the unpacked
  extension.
- `store-assets/`: Sanitized listing captures, editable listing pages, and generated Chrome
  Web Store images.
