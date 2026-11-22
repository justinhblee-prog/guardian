import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import {
  effectiveLicenseStatus, filterLicenses, sortLicenses, summarizeLicenses, validateIssueInput
} from "../admin/admin-model.mjs";

const now = Date.parse("2026-11-17T00:00:00Z");
const licenses = [
  { id: "a", license_ref: "GDN-A", status: "active", expires_at: "2026-12-01T00:00:00Z", created_at: "2026-11-01T00:00:00Z", active_devices: 1 },
  { id: "b", license_ref: "GDN-B", status: "active", expires_at: "2026-11-16T00:00:00Z", created_at: "2026-11-03T00:00:00Z", active_devices: 0 },
  { id: "c", license_ref: "GDN-C", status: "revoked", expires_at: "2027-01-01T00:00:00Z", created_at: "2026-11-02T00:00:00Z", active_devices: 0 }
];

test("effective status treats expired timestamps as expired without changing API data", () => {
  assert.equal(effectiveLicenseStatus(licenses[0], now), "active");
  assert.equal(effectiveLicenseStatus(licenses[1], now), "expired");
  assert.equal(effectiveLicenseStatus(licenses[2], now), "revoked");
  assert.equal(effectiveLicenseStatus({ status: "active", expires_at: null }, now), "active");
  assert.equal(licenses[1].status, "active");
});

test("summary counts active, expired and revoked licenses", () => {
  assert.deepEqual(summarizeLicenses(licenses, now), { total: 3, active: 1, expired: 1, revoked: 1 });
});

test("search and status filters compose", () => {
  assert.deepEqual(filterLicenses(licenses, "gdn-b", "all", now), [licenses[1]]);
  assert.deepEqual(filterLicenses(licenses, "", "expired", now), [licenses[1]]);
  assert.deepEqual(filterLicenses(licenses, "gdn", "active", now), [licenses[0]]);
});

test("license sorting supports expiry, device count and newest", () => {
  assert.deepEqual(sortLicenses(licenses, "expiry").map(item => item.id), ["b", "a", "c"]);
  assert.deepEqual(sortLicenses(licenses, "devices").map(item => item.id), ["a", "b", "c"]);
  assert.deepEqual(sortLicenses(licenses, "newest").map(item => item.id), ["b", "c", "a"]);
});

test("license issue form accepts only bounded integer values", () => {
  assert.deepEqual(validateIssueInput("365", "2"), { validDays: 365, maxDevices: 2, planCode: "custom" });
  assert.deepEqual(validateIssueInput("30", "2", "monthly_30d"), { validDays: 30, maxDevices: 2, planCode: "monthly_30d" });
  assert.deepEqual(validateIssueInput("", "5", "permanent"), { validDays: null, maxDevices: 5, planCode: "permanent" });
  assert.throws(() => validateIssueInput("31", "2", "monthly_30d"), /상품과 유효 기간/);
  assert.throws(() => validateIssueInput("30", "2", "lifetime"), /상품 유형/);
  for (const [days, devices] of [["0", "1"], ["36501", "1"], ["1.5", "1"], ["365", "0"], ["365", "21"], ["365", "1.5"]]) {
    assert.throws(() => validateIssueInput(days, devices), RangeError);
  }
});


test("admin dashboard exposes responsive management controls and visible operation status", async () => {
  const html = await readFile(new URL("../admin/index.html", import.meta.url), "utf8");
  for (const id of ["status", "dashboard", "statTotal", "statActive", "statExpired", "statRevoked", "issue", "search", "statusFilter", "sortOrder", "refresh", "disconnect", "licenses", "events", "customerName", "customerEmail", "orderRef", "customerSearch", "refreshCustomers", "customers"]) {
    assert.match(html, new RegExp(`id=["']${id}["']`));
  }
  assert.ok(html.indexOf('id="status"') < html.indexOf('id="authPanel"'), "operation status must remain visible after authentication");
  assert.match(html, /<meta name="viewport" content="width=device-width,initial-scale=1">/);
  assert.match(html, /@media\(max-width:850px\)/);
  assert.match(html, /@media\(max-width:560px\)/);
  assert.match(html, /<script type="module" src="\/admin\.js"><\/script>/);
});

test("admin browser code does not persist credentials or inject API data as HTML", async () => {
  const js = await readFile(new URL("../admin/admin.js", import.meta.url), "utf8");
  assert.doesNotMatch(js, /\b(?:localStorage|sessionStorage)\b/);
  assert.doesNotMatch(js, /\.innerHTML\s*=/);
  assert.match(js, /authorization:\s*`Bearer/);
  assert.match(js, /textContent/);
});

test("customer directory is wired to the authenticated API and renders customer data as text", async () => {
  const html = await readFile(new URL("../admin/index.html", import.meta.url), "utf8");
  const js = await readFile(new URL("../admin/admin.js", import.meta.url), "utf8");
  assert.match(html, /id="customers"/);
  assert.match(js, /api\("\/v1\/admin\/customers"\)/);
  assert.match(js, /customerItems = customers\.customers \|\| \[\]/);
  assert.match(js, /name\.textContent = item\.name/);
  assert.match(js, /email\.textContent = item\.email/);
  assert.match(js, /JSON\.stringify\(\{ \.\.\.input/);
  assert.doesNotMatch(js, /\.innerHTML\s*=/);
});

test("customer migration is additive and protects email uniqueness", async () => {
  const migration = await readFile(new URL("../migrations/004_customers.sql", import.meta.url), "utf8");
  assert.match(migration, /CREATE TABLE IF NOT EXISTS customers/);
  assert.match(migration, /CREATE UNIQUE INDEX IF NOT EXISTS customers_email_lower_uq ON customers \(lower\(email\)\)/);
  assert.match(migration, /ADD COLUMN IF NOT EXISTS customer_id/);
  assert.doesNotMatch(migration, /DROP TABLE|TRUNCATE|DELETE FROM/i);
});

test("support inbox and license plan controls are wired without replacing the customer directory", async () => {
  const html = await readFile(new URL("../admin/index.html", import.meta.url), "utf8");
  const js = await readFile(new URL("../admin/admin.js", import.meta.url), "utf8");
  assert.match(html, /id="planPreset"/);
  assert.match(html, /value="permanent"[^>]*>영구 이용 · PERMANENT/);
  assert.match(html, /id="supportTickets"/);
  assert.match(html, /id="customers"/);
  assert.match(js, /api\("\/v1\/admin\/support\/tickets"\)/);
  assert.match(js, /supportItems = support\.tickets \|\| \[\]/);
  assert.match(js, /renderSupportTickets\(\)/);
  assert.match(js, /validateIssueInput\(\$\("days"\)\.value, \$\("devices"\)\.value, \$\("planPreset"\)\.value\)/);
  assert.doesNotMatch(js, /\.innerHTML\s*=/);
});
