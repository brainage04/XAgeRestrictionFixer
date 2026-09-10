# Privacy Policy

X Age Restriction Fixer does not collect, store, sell, or transmit user data.

The browser extension runs only on `x.com` and `twitter.com`. The Android build is an LSPosed/Xposed module that runs only inside the targeted `com.twitter.android` main process. Both modify X's in-page or in-process frontend data handling so X can render media with its own native components for accounts that already have access to that media.

Neither component:

- collects personal information
- collects browsing history
- collects cookies, authentication headers, request bodies, or API payloads
- sends data to external servers
- uses analytics
- executes remotely hosted code

The Android module requests no network permission. All code runs locally; matching response bodies are transformed in memory and discarded after X consumes them.
