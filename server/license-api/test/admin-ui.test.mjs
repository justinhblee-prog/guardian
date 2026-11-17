import test from "node:test";
import assert from "node:assert/strict";
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
  assert.deepEqual(validateIssueInput("365", "2"), { validDays: 365, maxDevices: 2 });
  for (const [days, devices] of [["0", "1"], ["36501", "1"], ["1.5", "1"], ["365", "0"], ["365", "21"], ["365", "1.5"]]) {
    assert.throws(() => validateIssueInput(days, devices), RangeError);
  }
});
