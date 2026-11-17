import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
const worker = require("../chrome-extension/service-worker.js");
const manifest = JSON.parse(await readFile(resolve(root, "chrome-extension/manifest.json"), "utf8"));
assert.equal(manifest.manifest_version, 3);
assert.equal(manifest.background.service_worker, "service-worker.js");
assert.ok(manifest.permissions.includes("declarativeNetRequest"));
assert.ok(!manifest.permissions.includes("webRequestBlocking"));

const shared = await readFile(resolve(root, "shared/filter_rules.txt"), "utf8");
const bundled = await readFile(resolve(root, "chrome-extension/rules.txt"), "utf8");
const parsed = worker.parseSharedRules(shared);
assert.deepEqual(worker.parseSharedRules(bundled), parsed);
assert.equal(parsed.length, 10);
const rules = worker.toDynamicRules(parsed);
assert.equal(rules.length, parsed.length);
assert.equal(rules[0].action.type, "block");
assert.deepEqual(rules[0].condition.requestDomains, ["doubleclick.net"]);
assert.throws(() => worker.parseSharedRules("block=not a domain"), /Unsupported shared rule/);
assert.throws(() => worker.parseSharedRules("block=-invalid.example"), /Invalid domain/);
console.log("Chrome MV3 manifest, shared-rule parity, DNR conversion and parser rejection tests passed.");
