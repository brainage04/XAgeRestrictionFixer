# Changelog

## 1.0.0 (2026-11-08)

- **Show age-restricted posts** for X (`com.twitter.android`, any version): replaces the
  `TweetWithVisibilityResults` wrapper in X's API responses with the post inside it, so the app
  shows the post instead of the age-verification placeholder. Replaces the earlier LSPatch and
  Xposed module build.
