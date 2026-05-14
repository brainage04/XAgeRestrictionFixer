const CDP = require("chrome-remote-interface");

const PORT = Number(process.env.CDP_PORT || 9222);
const LOAD_DELAY_MS = Number(process.env.LOAD_DELAY_MS || 7000);

function usage() {
  console.log(`Usage:
  node scripts/compare-x-dom.js label=https://x.com/user/status/123 [label2=https://x.com/user/status/456]
  CASES='[["label","https://x.com/user/status/123"]]' node scripts/compare-x-dom.js

Prerequisite: Chrome must already be running with --remote-debugging-port=9222.
The script navigates tabs through Chrome DevTools Protocol and does not read or export cookies.`);
}

function casesFromArgs() {
  if (process.env.CASES) {
    const parsed = JSON.parse(process.env.CASES);

    if (!Array.isArray(parsed)) {
      throw new Error("CASES must be a JSON array.");
    }

    return parsed.map((entry, index) => {
      if (Array.isArray(entry)) {
        return [String(entry[0] || `case-${index + 1}`), String(entry[1] || "")];
      }

      return [String(entry.label || `case-${index + 1}`), String(entry.url || "")];
    });
  }

  return process.argv.slice(2).map((arg, index) => {
    const separator = arg.indexOf("=");

    if (separator === -1) {
      return [`case-${index + 1}`, arg];
    }

    return [
      arg.slice(0, separator) || `case-${index + 1}`,
      arg.slice(separator + 1)
    ];
  });
}

if (process.argv.includes("--help") || process.argv.includes("-h")) {
  usage();
  process.exit(0);
}

const CASES = casesFromArgs().filter(([, url]) => {
  return /^https:\/\/(?:x|twitter)\.com\/[^/]+\/status\/\d+/.test(url);
});

if (!CASES.length) {
  usage();
  process.exit(1);
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function createTarget(url) {
  const endpoint = `http://127.0.0.1:${PORT}/json/new?${encodeURIComponent(url)}`;
  const response = await fetch(endpoint, {
    method: "PUT"
  });

  if (!response.ok) {
    throw new Error(`Failed to create Chrome target: ${response.status} ${response.statusText}`);
  }

  return response.json();
}

async function closeTarget(id) {
  await fetch(`http://127.0.0.1:${PORT}/json/close/${id}`).catch(() => {});
}

async function snapshot(label, url) {
  const target = await createTarget("about:blank");
  const client = await CDP({
    port: PORT,
    target: target.id
  });

  try {
    const { Page, Runtime, Network } = client;
    await Promise.all([Page.enable(), Runtime.enable(), Network.enable()]);
    await Page.navigate({
      url
    });
    await Page.loadEventFired().catch(() => {});
    await delay(LOAD_DELAY_MS);

    const evaluated = await Runtime.evaluate({
      returnByValue: true,
      expression: `(() => {
        const statusId = location.pathname.match(/\\/status\\/(\\d+)/)?.[1] || "";

        function pathFor(node) {
          const parts = [];
          let current = node;

          while (current && current.nodeType === 1 && parts.length < 14) {
            const parent = current.parentElement;
            const index = parent ? Array.from(parent.children).indexOf(current) + 1 : 1;
            const testId = current.getAttribute("data-testid");
            const id = current.id;
            const tag = current.tagName.toLowerCase();
            parts.unshift(id ? tag + "#" + id : testId ? tag + "[data-testid=" + testId + "]" : tag + ":nth-child(" + index + ")");
            current = parent;
          }

          return parts.join(" > ");
        }

        function box(node) {
          const rect = node.getBoundingClientRect();
          return {
            x: Math.round(rect.x),
            y: Math.round(rect.y),
            w: Math.round(rect.width),
            h: Math.round(rect.height)
          };
        }

        function styleFor(node) {
          const style = getComputedStyle(node);
          return {
            display: style.display,
            position: style.position,
            overflow: style.overflow,
            borderRadius: style.borderRadius,
            opacity: style.opacity,
            filter: style.filter,
            objectFit: style.objectFit,
            backgroundImage: style.backgroundImage.slice(0, 160)
          };
        }

        function describe(node) {
          return {
            tag: node.tagName.toLowerCase(),
            id: node.id || "",
            testId: node.getAttribute("data-testid") || "",
            role: node.getAttribute("role") || "",
            aria: node.getAttribute("aria-label") || "",
            text: (node.innerText || "").trim().slice(0, 220),
            path: pathFor(node),
            rect: box(node),
            style: styleFor(node),
            childCount: node.children.length,
            imgCount: node.querySelectorAll("img").length,
            videoCount: node.querySelectorAll("video").length,
            src: node.currentSrc || node.src || "",
            href: node.href || ""
          };
        }

        const articles = Array.from(document.querySelectorAll("article"));
        const article = articles.find((candidate) => {
          return Array.from(candidate.querySelectorAll("a[href]")).some((link) => link.href.includes("/status/" + statusId));
        }) || articles[0] || null;

        const blocker = article
          ? Array.from(article.querySelectorAll("div, span")).find((node) => {
            return /age-restricted adult content|verify your age|to view this media/i.test(node.innerText || "");
          })
          : null;

        const mediaNodes = article
          ? Array.from(article.querySelectorAll('[data-testid="tweetPhoto"], [data-testid="videoPlayer"], [data-testid="placementTracking"], img, video'))
          : [];

        const blockerAncestors = [];
        let current = blocker;

        while (current && current !== article && blockerAncestors.length < 12) {
          blockerAncestors.push(describe(current));
          current = current.parentElement;
        }

        return {
          label: ${JSON.stringify(label)},
          url: location.href,
          title: document.title,
          statusId,
          article: article ? describe(article) : null,
          articleText: article ? article.innerText.slice(0, 1200) : "",
          blocker: blocker ? describe(blocker) : null,
          blockerAncestors,
          mediaNodes: mediaNodes.map(describe)
        };
      })()`
    });

    return evaluated.result.value;
  } finally {
    await client.close();
    await closeTarget(target.id);
  }
}

async function main() {
  const results = [];

  for (const [label, url] of CASES) {
    console.error(`Capturing ${label}`);
    results.push(await snapshot(label, url));
  }

  console.log(JSON.stringify(results, null, 2));
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
