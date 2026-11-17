export function effectiveLicenseStatus(license, now = Date.now()) {
  if (license?.status === "revoked") return "revoked";
  if (license?.status === "expired") return "expired";
  const expiry = Date.parse(license?.expires_at || "");
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
  if (sort === "expiry") return copy.sort((a, b) => time(a.expires_at) - time(b.expires_at));
  if (sort === "devices") return copy.sort((a, b) =>
    (Number(b.active_devices) || 0) - (Number(a.active_devices) || 0)
  );
  return copy.sort((a, b) => time(b.created_at) - time(a.created_at));
}

export function validateIssueInput(validDays, maxDevices) {
  const days = Number(validDays);
  const devices = Number(maxDevices);
  if (!Number.isInteger(days) || days < 1 || days > 36500) {
    throw new RangeError("유효 기간은 1~36500일의 정수여야 합니다.");
  }
  if (!Number.isInteger(devices) || devices < 1 || devices > 20) {
    throw new RangeError("최대 기기는 1~20대의 정수여야 합니다.");
  }
  return { validDays: days, maxDevices: devices };
}
