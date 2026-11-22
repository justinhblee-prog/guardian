export function effectiveLicenseStatus(license, now = Date.now()) {
  if (license?.status === "revoked") return "revoked";
  if (license?.status === "expired") return "expired";
  if (license?.expires_at == null) return "active";
  const expiry = Date.parse(license.expires_at);
  return Number.isFinite(expiry) && expiry <= now ? "expired" : "active";
}

export function summarizeLicenses(items, now = Date.now()) {
  const summary = { total: items.length, active: 0, expired: 0, revoked: 0 };
  for (const item of items) summary[effectiveLicenseStatus(item, now)] += 1;
  return summary;
}

export function filterLicenses(items, query = "", status = "all", now = Date.now()) {
  const needle = String(query).trim().toLowerCase();
  return items.filter(item => {
    if (status !== "all" && effectiveLicenseStatus(item, now) !== status) return false;
    if (!needle) return true;
    const haystack = [
      item.license_ref, item.id, item.status, item.expires_at, item.created_at
    ].filter(Boolean).join(" ").toLowerCase();
    return haystack.includes(needle);
  });
}

export function sortLicenses(items, sort = "newest") {
  const copy = [...items];
  const time = value => {
    const parsed = Date.parse(value || "");
    return Number.isFinite(parsed) ? parsed : 0;
  };
  if (sort === "expiry") return copy.sort((a, b) => {
    const expiryA = a.expires_at == null ? Number.POSITIVE_INFINITY : time(a.expires_at);
    const expiryB = b.expires_at == null ? Number.POSITIVE_INFINITY : time(b.expires_at);
    return expiryA - expiryB;
  });
  if (sort === "devices") return copy.sort((a, b) =>
    (Number(b.active_devices) || 0) - (Number(a.active_devices) || 0)
  );
  return copy.sort((a, b) => time(b.created_at) - time(a.created_at));
}

export function validateIssueInput(validDays, maxDevices, planCode = "custom") {
  const days = planCode === "permanent" ? null : Number(validDays);
  const devices = Number(maxDevices);
  if (planCode === "permanent" && String(validDays).trim() !== "") throw new RangeError("영구 라이선스는 만료 기간을 입력하지 않습니다.");
  if (planCode !== "permanent" && (!Number.isInteger(days) || days < 1 || days > 36500)) {
    throw new RangeError("유효 기간은 1~36500일의 정수여야 합니다.");
  }
  if (!Number.isInteger(devices) || devices < 1 || devices > 20) {
    throw new RangeError("최대 기기는 1~20대의 정수여야 합니다.");
  }
  const planDurations = { monthly_30d: 30, quarterly_90d: 90, annual_365d: 365, short_7d: 7, permanent: null };
  if (planCode !== "custom" && !Object.hasOwn(planDurations, planCode)) {
    throw new RangeError("라이선스 상품 유형이 올바르지 않습니다.");
  }
  if (planCode !== "custom" && planDurations[planCode] !== days) {
    throw new RangeError("선택한 라이선스 상품과 유효 기간이 일치하지 않습니다.");
  }
  return { validDays: days, maxDevices: devices, planCode };
}
