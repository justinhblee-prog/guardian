const RULES_PATH = "rules.txt";
const FIRST_DYNAMIC_RULE_ID = 1000;
const MAX_DYNAMIC_RULES_PER_UPDATE = 30000;

function parseSharedRules(source) {
  const parsed = [];
  for (const [index, raw] of source.split(/\r?\n/).entries()) {
    const line = raw.trim();
    if (!line || line.startsWith("#")) continue;
    const match = /^(block|allow)=([a-z0-9.-]+)$/i.exec(line);
    if (!match) throw new Error(`Unsupported shared rule at line ${index + 1}`);
    const domain = match[2].toLowerCase().replace(/^\.+|\.+$/g, "");
    if (!domain || domain.length > 253 || domain.split(".").some(label =>
      !label || label.length > 63 || !/^[a-z0-9](?:[a-z0-9-]*[a-z0-9])?$/.test(label)
    )) throw new Error(`Invalid domain at line ${index + 1}`);
    parsed.push({ action: match[1].toLowerCase(), domain });
  }
  return parsed;
}

function toDynamicRules(parsed) {
  if (parsed.length > MAX_DYNAMIC_RULES_PER_UPDATE) {
    throw new Error("Shared rules exceed the supported dynamic-rule budget");
  }
  return parsed.map((entry, index) => ({
    id: FIRST_DYNAMIC_RULE_ID + index,
    priority: entry.action === "allow" ? 2 : 1,
    action: { type: entry.action === "allow" ? "allow" : "block" },
    condition: {
      requestDomains: [entry.domain],
      resourceTypes: [
        "main_frame", "sub_frame", "stylesheet", "script", "image",
        "font", "object", "xmlhttprequest", "ping", "media", "websocket",
        "other"
      ]
    }
  }));
}

async function loadBundledRules() {
  const response = await fetch(chrome.runtime.getURL(RULES_PATH));
  if (!response.ok) throw new Error(`Unable to load bundled rules: HTTP ${response.status}`);
  return toDynamicRules(parseSharedRules(await response.text()));
}

async function setProtectionEnabled(enabled) {
  const existing = await chrome.declarativeNetRequest.getDynamicRules();
  const removeRuleIds = existing
    .filter(rule => rule.id >= FIRST_DYNAMIC_RULE_ID)
    .map(rule => rule.id);
  const addRules = enabled ? await loadBundledRules() : [];
  await chrome.declarativeNetRequest.updateDynamicRules({ removeRuleIds, addRules });
  const previous = await chrome.storage.local.get(["lastUpdatedAt"]);
  await chrome.storage.local.set({
    enabled,
    ruleCount: addRules.length,
    lastUpdatedAt: previous.lastUpdatedAt || new Date().toISOString(),
    source: "bundled-shared-rules"
  });
  return { enabled, ruleCount: addRules.length };
}

async function installBundledRules() {
  const saved = await chrome.storage.local.get(["enabled"]);
  await setProtectionEnabled(saved.enabled !== false);
}

if (typeof chrome !== "undefined") {
  chrome.runtime.onInstalled.addListener(() => {
    installBundledRules().catch(error => console.error("Guardian rule installation failed", error));
  });
  chrome.runtime.onStartup.addListener(() => {
    installBundledRules().catch(error => console.error("Guardian rule refresh failed", error));
  });
  chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
    if (message?.type !== "guardian:set-enabled" || typeof message.enabled !== "boolean") return false;
    setProtectionEnabled(message.enabled)
      .then(sendResponse)
      .catch(error => sendResponse({ error: error.message }));
    return true;
  });
}

if (typeof module !== "undefined") {
  module.exports = { parseSharedRules, toDynamicRules, FIRST_DYNAMIC_RULE_ID };
}
