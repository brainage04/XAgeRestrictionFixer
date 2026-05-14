const CDP = require("chrome-remote-interface");

const PORT = Number(process.env.CDP_PORT || 9222);
const EXTENSION_ID = process.env.EXTENSION_ID || "";
const EXTENSION_NAME = process.env.EXTENSION_NAME || "X Age Restriction Fixer";
const LOAD_DELAY_MS = Number(process.env.LOAD_DELAY_MS || 1200);
const RELOAD_DELAY_MS = Number(process.env.RELOAD_DELAY_MS || 1000);

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function createTarget(url) {
  const response = await fetch(`http://127.0.0.1:${PORT}/json/new?${encodeURIComponent(url)}`, {
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

function reloadExtensionInPage(extensionId, extensionName) {
  const manager = document.querySelector("extensions-manager");
  const list = manager?.shadowRoot?.querySelector("extensions-item-list");
  const items = Array.from(list?.shadowRoot?.querySelectorAll("extensions-item") || []);
  const item = items.find((candidate) => {
    const id = candidate.getAttribute("id") || candidate.id || "";
    const name = candidate.shadowRoot?.querySelector("#name")?.innerText || "";

    return extensionId
      ? id === extensionId
      : name === extensionName;
  });

  if (!item) {
    return {
      ok: false,
      reason: "extension not found",
      available: items.map((candidate) => ({
        id: candidate.getAttribute("id") || candidate.id || "",
        name: candidate.shadowRoot?.querySelector("#name")?.innerText || "",
        version: candidate.shadowRoot?.querySelector("#version")?.innerText || ""
      }))
    };
  }

  const button = item.shadowRoot?.querySelector("#dev-reload-button");

  if (!button) {
    return {
      ok: false,
      reason: "reload button not found",
      id: item.getAttribute("id") || item.id || "",
      name: item.shadowRoot?.querySelector("#name")?.innerText || ""
    };
  }

  button.click();

  return {
    ok: true,
    id: item.getAttribute("id") || item.id || "",
    name: item.shadowRoot?.querySelector("#name")?.innerText || "",
    version: item.shadowRoot?.querySelector("#version")?.innerText || ""
  };
}

async function main() {
  const target = await createTarget("chrome://extensions/?developer=true");
  const client = await CDP({
    port: PORT,
    target: target.id
  });

  try {
    const { Page, Runtime } = client;
    await Promise.all([Page.enable(), Runtime.enable()]);
    await delay(LOAD_DELAY_MS);

    const result = await Runtime.evaluate({
      returnByValue: true,
      expression: `(${reloadExtensionInPage.toString()})(${JSON.stringify(EXTENSION_ID)}, ${JSON.stringify(EXTENSION_NAME)})`
    });

    if (result.exceptionDetails) {
      throw new Error(result.exceptionDetails.text || "Failed to evaluate extension reload script");
    }

    const reloadResult = result.result.value;

    if (!reloadResult?.ok) {
      console.error(JSON.stringify(reloadResult, null, 2));
      process.exitCode = 1;
      return;
    }

    await delay(RELOAD_DELAY_MS);
    console.log(JSON.stringify(reloadResult, null, 2));
  } finally {
    await client.close();
    await closeTarget(target.id);
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
