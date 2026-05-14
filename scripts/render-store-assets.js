const { execFileSync } = require("child_process");
const fs = require("fs");
const path = require("path");
const { pathToFileURL } = require("url");

const root = path.resolve(__dirname, "..");

const chromeCandidates = [
  process.env.CHROME_BIN,
  "google-chrome",
  "chromium",
  "chromium-browser"
].filter(Boolean);

function findChrome() {
  for (const candidate of chromeCandidates) {
    try {
      execFileSync("which", [candidate], { stdio: "ignore" });
      return candidate;
    } catch {
      // Try the next candidate.
    }
  }

  throw new Error("Could not find Chrome. Set CHROME_BIN to a Chrome or Chromium executable.");
}

const outputs = [
  {
    html: "store-assets/screenshot-before-after.html",
    png: "store-assets/screenshot-before-after-1280x800.png",
    width: 1280,
    height: 800
  },
  {
    html: "store-assets/screenshot-native-privacy.html",
    png: "store-assets/screenshot-native-privacy-1280x800.png",
    width: 1280,
    height: 800
  },
  {
    html: "store-assets/small-promo-tile.html",
    png: "store-assets/small-promo-tile-440x280.png",
    width: 440,
    height: 280
  }
];

const chrome = findChrome();

for (const output of outputs) {
  const htmlPath = path.join(root, output.html);
  const pngPath = path.join(root, output.png);

  fs.mkdirSync(path.dirname(pngPath), { recursive: true });

  execFileSync(chrome, [
    "--headless=new",
    "--disable-gpu",
    "--no-sandbox",
    "--hide-scrollbars",
    "--force-device-scale-factor=1",
    `--window-size=${output.width},${output.height}`,
    `--screenshot=${pngPath}`,
    pathToFileURL(htmlPath).href
  ], {
    stdio: "inherit"
  });
}
