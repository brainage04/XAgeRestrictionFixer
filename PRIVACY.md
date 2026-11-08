# Privacy Policy

X Age Restriction Fixer does not collect, store, sell, or transmit user data.

The browser extension runs only on `x.com` and `twitter.com`. The Android build is a patch for the official X app (`com.twitter.android`), applied on your own device with [Morphe](https://morphe.software); the code it adds runs only inside that app and only looks at the app's own API responses from `x.com` and `twitter.com`. Both modify X's in-page or in-process frontend data handling so X can render media with its own native components for accounts that already have access to that media.

Neither component:

- collects personal information
- collects browsing history
- collects cookies, authentication headers, request bodies, or API payloads
- sends data to external servers
- uses analytics
- executes remotely hosted code

The Android patch adds no permissions and makes no network requests of its own. All code runs locally; matching response bodies are transformed in memory and discarded after X consumes them.
