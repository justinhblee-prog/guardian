import test from "node:test";
import assert from "node:assert/strict";
import { validateIssueInput } from "../admin/admin-model.mjs";

test("fixed license plans preserve their explicit commercial duration", () => {
  assert.deepEqual(validateIssueInput("30", "2", "monthly_30d"), {
    validDays: 30, maxDevices: 2, planCode: "monthly_30d"
  });
  assert.deepEqual(validateIssueInput("90", "2", "quarterly_90d"), {
    validDays: 90, maxDevices: 2, planCode: "quarterly_90d"
  });
  assert.deepEqual(validateIssueInput("365", "2", "annual_365d"), {
    validDays: 365, maxDevices: 2, planCode: "annual_365d"
  });
  assert.deepEqual(validateIssueInput("7", "1", "short_7d"), {
    validDays: 7, maxDevices: 1, planCode: "short_7d"
  });
});

test("custom license duration remains supported", () => {
  assert.deepEqual(validateIssueInput("45", "3", "custom"), {
    validDays: 45, maxDevices: 3, planCode: "custom"
  });
});

test("fixed plan cannot be issued with a mismatched duration", () => {
  assert.throws(() => validateIssueInput("31", "2", "monthly_30d"), /상품과 유효 기간/);
});

test("unknown plan codes are rejected", () => {
  assert.throws(() => validateIssueInput("30", "2", "lifetime"), /상품 유형/);
});

test("device and duration bounds remain enforced", () => {
  assert.throws(() => validateIssueInput("0", "2", "custom"), /유효 기간/);
  assert.throws(() => validateIssueInput("30", "21", "custom"), /최대 기기/);
});
