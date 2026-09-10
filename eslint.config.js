const js = require("@eslint/js");
const globals = require("globals");

module.exports = [
  {
    ignores: ["dist/**", "node_modules/**", "android-patch/**/build/**"]
  },
  js.configs.recommended,
  {
    files: ["src/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "script",
      globals: {
        ...globals.browser,
        ...globals.serviceworker,
        chrome: "readonly"
      }
    }
  },
  {
    files: ["eslint.config.js", "scripts/**/*.js"],
    languageOptions: {
      ecmaVersion: "latest",
      sourceType: "commonjs",
      globals: {
        ...globals.browser,
        ...globals.node
      }
    }
  }
];
