import assert from "node:assert/strict";
import fs from "node:fs";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import path from "node:path";

const require = createRequire(import.meta.url);
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const manifest = JSON.parse(fs.readFileSync(path.join(root, "chrome-extension/manifest.json"), "utf8"));
const css = fs.readFileSync(path.join(root, "chrome-extension/cosmetic.css"), "utf8");
const script = fs.readFileSync(path.join(root, "chrome-extension/cosmetic.js"), "utf8");
const { normalizeGuardianAdLabel, shouldHideGuardianAdLabel } = require("../chrome-extension/cosmetic.js");

const contentScript = manifest.content_scripts?.find(entry =>
  entry.matches?.includes("http://*/*") &&
  entry.matches?.includes("https://*/*")
);
assert.ok(contentScript, "Cosmetic content script must run on HTTP and HTTPS pages");
assert.ok(contentScript.css?.includes("cosmetic.css"), "Cosmetic stylesheet must be registered");
assert.ok(contentScript.js?.includes("cosmetic.js"), "Cosmetic DOM observer must be registered");
assert.equal(contentScript.run_at, "document_idle");

for (const selector of [
  ".adsbygoogle",
  '[id*="ad-placeholder" i]',
  '[class*="ad-placement" i]',
  'iframe[src*="doubleclick.net"]',
  'iframe[src*="googlesyndication.com"]',
  ':has(> iframe[src*="doubleclick.net"])',
  ':has(> iframe[src*="googlesyndication.com"])',
  ':has(> ins.adsbygoogle[data-ad-status="unfilled"])',
  '[data-ad-status="unfilled"]',
  '.google-auto-placed:empty'
]) {
  assert.ok(css.includes(selector), `Missing cosmetic selector: ${selector}`);
}

assert.equal(normalizeGuardianAdLabel("  Advertisement\n "), "advertisement");
assert.equal(shouldHideGuardianAdLabel("Advertisement", { width: 300, height: 250 }), true);
assert.equal(shouldHideGuardianAdLabel("Sponsored advertisement", { width: 320, height: 100 }), true);
assert.equal(shouldHideGuardianAdLabel("Advertisement", { width: 120, height: 20 }), false);
assert.equal(shouldHideGuardianAdLabel("Advertisement", { width: 1000, height: 900 }), false);
assert.equal(shouldHideGuardianAdLabel("Sponsored article", { width: 320, height: 250 }), false);
assert.match(script, /MutationObserver/);
assert.match(script, /guardianCosmeticHidden/);
assert.match(script, /requestAnimationFrame/);

console.log("Guardian Chrome extension cosmetic selector and Advertisement placeholder tests passed.");
