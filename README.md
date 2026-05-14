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

This writes `dist/x-age-restriction-fixer-1.0.0.zip` with only the runtime extension files.

To regenerate the Chrome Web Store listing images, run:

```sh
npm run render:store-assets
```

That command uses local headless Chrome to render the sanitized listing pages in `store-assets/`.

## Behavior

- Runs only on `x.com` and `twitter.com`.
- Runs at `document_start`.
- Converts `TweetWithVisibilityResults` wrappers into the wrapped `Tweet` objects before X consumes API JSON.
- Lets X render media with its own native components.
- Does not add custom media cards, video controls, or diagnostic popups.
- Does not store or display cookies, auth headers, request bodies, or API payloads.

## Files

- `manifest.json`: Chrome extension manifest.
- `icons/`: Extension icon source and generated PNG assets.
- `src/page-probe.js`: Main-world API normalization.
- `scripts/build-extension-zip.js`: Chrome Web Store ZIP builder.
- `scripts/compare-x-dom.js`: Optional CDP helper for manually comparing direct status DOM snapshots.
- `scripts/render-store-assets.js`: Local Chrome renderer for Web Store listing images.
- `scripts/reload-extension.js`: Chrome DevTools Protocol helper for reloading the unpacked extension.
- `store-assets/`: Sanitized listing captures, editable listing pages, and generated Chrome Web Store images.
