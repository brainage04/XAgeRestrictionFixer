(() => {
  if (window.__fixupXAgeProbeInstalled) {
    return;
  }

  window.__fixupXAgeProbeInstalled = true;

  const API_URL_RE = /\/i\/api\/|\/graphql\//;
  const originalFetch = window.fetch;
  const originalJsonParse = JSON.parse;

  function requestUrl(input) {
    if (typeof input === "string") {
      return input;
    }

    if (input instanceof URL) {
      return input.href;
    }

    return input?.url || "";
  }

  function responseHeaders(response) {
    const headers = new Headers(response.headers);

    headers.delete("content-encoding");
    headers.delete("content-length");
    return headers;
  }

  function shouldNormalizeResponse(url, contentType) {
    return API_URL_RE.test(url) && /json|text|javascript|graphql/i.test(contentType || "");
  }

  function normalizeVisibilityResults(value) {
    let changed = false;

    function visit(current) {
      if (!current || typeof current !== "object") {
        return current;
      }

      if (Array.isArray(current)) {
        for (let index = 0; index < current.length; index += 1) {
          const next = visit(current[index]);

          if (next !== current[index]) {
            current[index] = next;
            changed = true;
          }
        }

        return current;
      }

      if (current.__typename === "TweetWithVisibilityResults" && current.tweet) {
        const tweet = visit(current.tweet);

        if (tweet && typeof tweet === "object") {
          if (!tweet.__typename || tweet.__typename === "TweetWithVisibilityResults") {
            tweet.__typename = "Tweet";
          }

          if (tweet.legacy?.possibly_sensitive_editable === false) {
            tweet.legacy.possibly_sensitive_editable = true;
          }
        }

        changed = true;
        return tweet;
      }

      for (const [key, child] of Object.entries(current)) {
        const next = visit(child);

        if (next !== child) {
          current[key] = next;
          changed = true;
        }
      }

      return current;
    }

    return {
      value: visit(value),
      changed
    };
  }

  function normalizeApiBody(body) {
    if (typeof body !== "string" || !body.includes("TweetWithVisibilityResults")) {
      return {
        body,
        changed: false
      };
    }

    let parsed;

    try {
      parsed = originalJsonParse(body);
    } catch {
      return {
        body,
        changed: false
      };
    }

    const result = normalizeVisibilityResults(parsed);

    if (!result.changed) {
      return {
        body,
        changed: false
      };
    }

    return {
      body: JSON.stringify(result.value),
      changed: true
    };
  }

  JSON.parse = function patchedJsonParse(...args) {
    const parsed = originalJsonParse.apply(this, args);
    const body = args[0];

    if (typeof body !== "string" || !body.includes("TweetWithVisibilityResults")) {
      return parsed;
    }

    try {
      return normalizeVisibilityResults(parsed).value;
    } catch {
      return parsed;
    }
  };

  window.fetch = async function patchedFetch(...args) {
    const response = await originalFetch.apply(this, args);
    const url = requestUrl(args[0]) || response.url || "";
    const contentType = response.headers?.get("content-type") || "";

    if (!shouldNormalizeResponse(url, contentType)) {
      return response;
    }

    try {
      const normalized = normalizeApiBody(await response.clone().text());

      if (!normalized.changed) {
        return response;
      }

      return new Response(normalized.body, {
        status: response.status,
        statusText: response.statusText,
        headers: responseHeaders(response)
      });
    } catch {
      return response;
    }
  };
})();
