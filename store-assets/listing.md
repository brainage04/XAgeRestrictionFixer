# Chrome Web Store listing

Text for the developer dashboard. The name and summary come from `manifest.json`.

## Store listing tab

Name: X Age Restriction Fixer

Summary (132 characters at most):

> Fixes X's broken age-restricted media rendering for already age-verified accounts.

Description:

> Fixes X's broken age-restricted media rendering for already age-verified accounts.

Category: Accessibility

Language: English

Graphics:

- Store icon: `icons/icon128.png`
- Screenshots, in this order: `store-assets/screenshot-before-after-1280x800.png`, `store-assets/screenshot-native-privacy-1280x800.png`
- Small promo tile: `store-assets/small-promo-tile-440x280.png`

Homepage URL: https://github.com/brainage04/XAgeRestrictionFixer

Support URL: https://github.com/brainage04/XAgeRestrictionFixer/issues

## Privacy practices tab

Single purpose:

> Fix broken age-restricted media rendering on X for accounts that have already verified their age and already have access to the media.

Host permission justification, for the content scripts on `https://x.com/*` and `https://twitter.com/*`:

> The extension needs host access to x.com and twitter.com so it can run only on X pages and normalize X's in-page frontend API response shape before X renders age-restricted media. This lets X's own native image, video, GIF, and quote tweet components render media for accounts that have already passed X's access checks.

Remote code: No, I am not using remote code.

Data usage: tick none of the data types, then tick all three certifications.

Privacy policy URL: https://github.com/brainage04/XAgeRestrictionFixer/blob/main/PRIVACY.md
