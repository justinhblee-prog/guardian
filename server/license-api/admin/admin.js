import {
  effectiveLicenseStatus, filterLicenses, sortLicenses, summarizeLicenses, validateIssueInput
} from "./admin-model.mjs";

let adminToken = "";
let licenseItems = [];
let eventItems = [];
let customerItems = [];
let supportItems = [];
let loading = false;
const $ = id => document.getElementById(id);

function setStatus(message, kind = "neutral") {
  $("status").textContent = message;
  $("statusDot").className = "status-dot" + (kind === "ok" ? " ok" : kind === "error" ? " error" : "");
}

function dateLabel(value) {
  const date = new Date(value);
  return Number.isFinite(date.getTime()) ? date.toLocaleString() : "정보 없음";
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    credentials: "same-origin",
    headers: {
      ...(options.headers || {}),
      "content-type": "application/json",
      authorization: `Bearer ${adminToken}`
    },
    cache: "no-store"
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}

function setBusy(value) {
  loading = value;
  for (const id of ["connect", "issue", "refresh", "disconnect"]) $(id).disabled = value;
}

function renderSummary() {
  const summary = summarizeLicenses(licenseItems);
  $("statTotal").textContent = summary.total.toLocaleString();
  $("statActive").textContent = summary.active.toLocaleString();
  $("statExpired").textContent = summary.expired.toLocaleString();
  $("statRevoked").textContent = summary.revoked.toLocaleString();
}

function statusLabel(status) {
  return status === "active" ? "사용 가능" : status === "expired" ? "만료" : "정지";
}

function createButton(label, className, onClick) {
  const button = document.createElement("button");
  button.type = "button";
  button.className = className;
  button.textContent = label;
  button.addEventListener("click", onClick);
  return button;
}

async function loadActivations(license, root) {
  root.replaceChildren();
  const loadingText = document.createElement("p");
  loadingText.className = "muted";
  loadingText.textContent = "기기 등록 정보를 불러오는 중...";
  root.append(loadingText);
  try {
    const data = await api(`/v1/admin/licenses/${encodeURIComponent(license.id)}/activations`);
    renderActivations(license, root, data.activations || []);
  } catch (error) {
    root.replaceChildren();
    const message = document.createElement("p");
    message.className = "muted";
    message.textContent = error.message;
    root.append(message);
  }
}

function renderActivations(license, root, items) {
  root.replaceChildren();
  const heading = document.createElement("strong");
  heading.textContent = "등록 기기";
  root.append(heading);
  if (!items.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.textContent = "등록된 기기가 없습니다.";
    root.append(empty);
    return;
  }
  for (const item of items) {
    const row = document.createElement("div");
    row.className = "device-row";
    const info = document.createElement("div");
    info.className = "device-info";
    const title = document.createElement("strong");
    title.textContent = `기기 ····${item.device_hint || "--------"} · ${item.deactivated_at ? "해제됨" : "활성"}`;
    const detail = document.createElement("div");
    detail.className = "muted";
    detail.textContent = `앱 버전: ${item.app_version || "-"} · 최근 확인: ${dateLabel(item.last_seen_at)}`;
    info.append(title, detail);
    row.append(info);
    if (!item.deactivated_at) {
      const button = createButton("기기 해제", "danger", async () => {
        if (!confirm("이 기기만 라이선스에서 해제할까요? 다른 등록 기기와 라이선스는 유지됩니다.")) return;
        button.disabled = true;
        const succeeded = await mutateAndReload(
          () => api(
            `/v1/admin/licenses/${encodeURIComponent(license.id)}/activations/${encodeURIComponent(item.id)}/deactivate`,
            { method: "POST", body: "{}" }
          ),
          "선택한 기기의 등록을 해제했습니다."
        );
        if (!succeeded) button.disabled = false;
      });
      row.append(button);
    }
    root.append(row);
  }
}

function renderLicenses() {
  const root = $("licenses");
  root.replaceChildren();
  const filtered = sortLicenses(
    filterLicenses(licenseItems, $("search").value, $("statusFilter").value),
    $("sortOrder").value
  );
  if (!filtered.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = licenseItems.length ? "검색 조건에 맞는 라이선스가 없습니다." : "발급된 라이선스가 없습니다.";
    root.append(empty);
    return;
  }
  for (const item of filtered) {
    const status = effectiveLicenseStatus(item);
    const card = document.createElement("article");
    card.className = "license-card";
    const top = document.createElement("div");
    top.className = "license-top";
    const ref = document.createElement("div");
    ref.className = "license-ref";
    ref.textContent = item.license_ref || item.id;
    const pill = document.createElement("span");
    pill.className = `pill ${status}`;
    pill.textContent = statusLabel(status);
    top.append(ref, pill);

    const meta = document.createElement("div");
    meta.className = "license-meta";
    const expiry = document.createElement("span");
    expiry.textContent = `만료: ${dateLabel(item.expires_at)}`;
    const devices = document.createElement("span");
    devices.textContent = `활성 기기: ${Number(item.active_devices) || 0}/${Number(item.max_devices) || 1}`;
    const created = document.createElement("span");
    created.textContent = `발급: ${dateLabel(item.created_at)}`;
    meta.append(expiry, devices, created);

    const actions = document.createElement("div");
    actions.className = "actions";
    const devicePanel = document.createElement("div");
    devicePanel.className = "device-list";
    devicePanel.hidden = true;
    const deviceButton = createButton("기기 관리", "secondary", async () => {
      devicePanel.hidden = !devicePanel.hidden;
      if (!devicePanel.hidden) await loadActivations(item, devicePanel);
    });
    actions.append(deviceButton);
    if (status !== "revoked") {
      const extendButton = createButton("기간 연장", "secondary", async () => {
        const raw = prompt("연장할 기간을 일 단위로 입력하세요. (1~36500일)", "30");
        if (raw === null) return;
        const days = Number(raw.trim());
        if (!Number.isInteger(days) || days < 1 || days > 36500) {
          statusText("연장 기간은 1~36500일의 정수여야 합니다.", "error");
          return;
        }
        if (!confirm(`${days}일 연장할까요? 만료된 라이선스는 오늘부터 연장됩니다.`)) return;
        extendButton.disabled = true;
        const succeeded = await mutateAndReload(
          () => api(`/v1/admin/licenses/${encodeURIComponent(item.id)}/extend`, {
            method: "POST", body: JSON.stringify({ validDays: days })
          }),
          `라이선스를 ${days}일 연장했습니다.`
        );
        if (!succeeded) extendButton.disabled = false;
      });
      actions.append(extendButton);
    }
    if (status === "active") {
      const revokeButton = createButton("라이선스 정지", "danger", async () => {
        if (!confirm("이 라이선스를 정지하고 활성 기기를 모두 해제할까요?")) return;
        revokeButton.disabled = true;
        const succeeded = await mutateAndReload(
          () => api(`/v1/admin/licenses/${encodeURIComponent(item.id)}/revoke`, { method: "POST", body: "{}" }),
          "라이선스를 정지했습니다."
        );
        if (!succeeded) revokeButton.disabled = false;
      });
      actions.append(revokeButton);
    }
    card.append(top, meta, actions, devicePanel);
    root.append(card);
  }
}

function renderCustomers() {
  const root = $("customers");
  root.replaceChildren();
  const needle = $("customerSearch").value.trim().toLowerCase();
  const items = customerItems.filter(item => !needle || item.name.toLowerCase().includes(needle) || item.email.toLowerCase().includes(needle));
  if (!items.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = customerItems.length ? "검색 결과가 없습니다." : "등록된 고객이 없습니다. 고객 정보를 입력해 라이선스를 발급하면 여기에 표시됩니다.";
    root.append(empty);
    return;
  }
  for (const item of items) {
    const card = document.createElement("article");
    card.className = "license-card";
    const top = document.createElement("div");
    top.className = "license-top";
    const name = document.createElement("strong");
    name.textContent = item.name;
    const count = document.createElement("span");
    count.className = "pill active";
    count.textContent = `라이선스 ${Number(item.license_count) || 0}건`;
    top.append(name, count);
    const meta = document.createElement("div");
    meta.className = "license-meta";
    const email = document.createElement("span");
    email.textContent = item.email;
    const active = document.createElement("span");
    active.textContent = `사용 가능 ${Number(item.active_licenses) || 0}건`;
    const last = document.createElement("span");
    last.textContent = `최근 발급: ${item.last_license_issued ? dateLabel(item.last_license_issued) : "-"}`;
    meta.append(email, active, last);
    card.append(top, meta);
    root.append(card);
  }
}

function renderEvents() {
  const root = $("events");
  root.replaceChildren();
  if (!eventItems.length) {
    const empty = document.createElement("div");
    empty.className = "empty";
    empty.textContent = "감사 기록이 없습니다.";
    root.append(empty);
    return;
  }
  const labels = {
    issued: ["발급", "+"],
    activated: ["기기 활성화", "A"],
    deactivated: ["기기 비활성화", "−"],
    admin_device_deactivated: ["관리자 기기 해제", "−"],
    revoked: ["라이선스 정지", "!"]
  };
  for (const item of eventItems) {
    const row = document.createElement("div");
    row.className = "event-row";
    const icon = document.createElement("span");
    icon.className = "event-icon";
    icon.textContent = (labels[item.event_type] || [item.event_type, "·"])[1];
    const info = document.createElement("div");
    info.className = "event-info";
    const title = document.createElement("strong");
    title.className = "event-title";
    title.textContent = (labels[item.event_type] || [item.event_type])[0];
    const detail = document.createElement("div");
    detail.className = "event-detail muted";
    const ref = item.license_ref || "연결된 라이선스 없음";
    const metadata = item.metadata && Object.keys(item.metadata).length
      ? ` · ${JSON.stringify(item.metadata)}` : "";
    detail.textContent = `${dateLabel(item.created_at)} · ${ref} · ${item.actor || "system"}${metadata}`;
    info.append(title, detail);
    row.append(icon, info);
    root.append(row);
  }
}

function renderSupportTickets() {
  const root = $("supportTickets");
  root.replaceChildren();
  if (!supportItems.length) {
    const empty = document.createElement("div"); empty.className = "empty";
    empty.textContent = "접수된 고객 문의가 없습니다."; root.append(empty); return;
  }
  for (const item of supportItems) {
    const card = document.createElement("article"); card.className = "license-card";
    const title = document.createElement("strong"); title.textContent = "[" + item.ticket_ref + "] " + item.subject;
    const meta = document.createElement("p"); meta.className = "muted";
    meta.textContent = item.category + " · " + item.status + " · " + dateLabel(item.created_at) + " · " + (item.email || "회신 이메일 미제공");
    const message = document.createElement("p"); message.textContent = item.message; message.style.whiteSpace = "pre-wrap";
    card.append(title, meta, message);
    if (item.admin_reply) { const old = document.createElement('p'); old.className = 'notice'; old.textContent = '기존 답변: ' + item.admin_reply; old.style.whiteSpace = 'pre-wrap'; card.append(old); }
    const reply = document.createElement('textarea'); reply.rows = 3; reply.maxLength = 5000; reply.placeholder = '고객에게 전달할 답변'; reply.value = item.admin_reply || '';
    reply.style.cssText = 'width:100%;margin:8px 0;padding:12px;border-radius:10px;background:#0b1424;color:#f1f6ff;border:1px solid #344765;resize:vertical';
    const actions = document.createElement('div'); actions.className = 'actions';
    const status = document.createElement('select');
    for (const pair of [['answered','답변 완료'],['in_progress','처리 중'],['closed','종료']]) { const option = document.createElement('option'); option.value = pair[0]; option.textContent = pair[1]; status.append(option); }
    status.value = item.status === 'open' ? 'answered' : item.status;
    const send = createButton('답변 저장', 'secondary', async () => {
      if (reply.value.trim().length < 2) { statusText('답변을 2자 이상 입력하세요.', 'error'); return; }
      send.disabled = true;
      try { await api('/v1/admin/support/tickets/' + encodeURIComponent(item.id) + '/reply', { method: 'POST', body: JSON.stringify({ reply: reply.value.trim(), status: status.value }) }); await load(); statusText('고객 문의 답변을 저장했습니다.', 'ok'); }
      catch (error) { statusText(error.message, 'error'); send.disabled = false; }
    });
    actions.append(status, send); card.append(reply, actions); root.append(card);
  }
}

function statusText(message, kind = "neutral") {
  setStatus(message, kind);
}

async function mutateAndReload(action, successMessage) {
  if (!adminToken || loading) return false;
  setBusy(true);
  try {
    await action();
  } catch (error) {
    statusText(error.message, "error");
    return false;
  } finally {
    setBusy(false);
  }
  try {
    await load();
    statusText(successMessage, "ok");
    return true;
  } catch {
    return false;
  }
}

async function load() {
  if (!adminToken || loading) return;
  setBusy(true);
  try {
    const [licenses, audit, customers, support] = await Promise.all([
      api("/v1/admin/licenses"),
      api("/v1/admin/events"),
      api("/v1/admin/customers"),
      api("/v1/admin/support/tickets")
    ]);
    licenseItems = licenses.licenses || [];
    eventItems = audit.events || [];
    customerItems = customers.customers || [];
    supportItems = support.tickets || [];
    renderSummary();
    renderLicenses();
    renderEvents();
    renderCustomers();
    renderSupportTickets();
    $("dashboard").hidden = false;
    $("authPanel").hidden = true;
    setStatus("관리자 인증 성공", "ok");
  } catch (error) {
    statusText(error.message, "error");
    throw error;
  } finally {
    setBusy(false);
  }
}

$("connect").addEventListener("click", async () => {
  if (loading) return;
  adminToken = $("token").value.trim();
  $("token").value = "";
  if (!adminToken) {
    statusText("관리자 토큰을 입력하세요.", "error");
    return;
  }
  try {
    await load();
  } catch {
    adminToken = "";
    $("dashboard").hidden = true;
    $("authPanel").hidden = false;
  }
});

$("planPreset").addEventListener("change", () => {
  if ($("planPreset").value !== "custom") $("days").value = ({ annual_365d: 365, monthly_30d: 30, quarterly_90d: 90, short_7d: 7 })[$("planPreset").value];
});

$("issue").addEventListener("click", async () => {
  if (!adminToken || loading) {
    statusText("먼저 관리자 인증을 진행하세요.", "error");
    return;
  }
  let input;
  try {
    input = validateIssueInput($("days").value, $("devices").value, $("planPreset").value);
  } catch (error) {
    statusText(error.message, "error");
    return;
  }
  setBusy(true);
  let data;
  try {
    const name = $("customerName").value.trim();
    const email = $("customerEmail").value.trim();
    const orderRef = $("orderRef").value.trim();
    if ((name || email || orderRef) && (!name || !email)) {
      statusText("고객명과 이메일을 함께 입력하세요.", "error");
      return;
    }
    data = await api("/v1/admin/licenses", {
      method: "POST",
      body: JSON.stringify({ ...input, ...(name && email ? { customer: { name, email, orderRef } } : {}) })
    });
  } catch (error) {
    statusText(error.message, "error");
    return;
  } finally {
    setBusy(false);
  }
  $("key").textContent = data.licenseKey;
  $("issued").hidden = false;
  try {
    await load();
    statusText("발급 완료. 키는 지금 한 번만 표시됩니다.", "ok");
  } catch {
    statusText("라이선스는 발급됐지만 목록 새로고침에 실패했습니다. 발급 키를 안전하게 보관하세요.", "error");
  }
});

$("copy").addEventListener("click", async () => {
  const value = $("key").textContent;
  try {
    await navigator.clipboard.writeText(value);
    statusText("라이선스 키를 복사했습니다.", "ok");
  } catch {
    statusText("복사할 수 없습니다. 키를 직접 선택해 복사하세요.", "error");
  }
});

$("refresh").addEventListener("click", async () => {
  try { await load(); }
  catch { /* The status line already contains the API error. */ }
});
$("disconnect").addEventListener("click", () => {
  adminToken = "";
  licenseItems = [];
  eventItems = [];
  customerItems = [];
  supportItems = [];
  $("dashboard").hidden = true;
  $("authPanel").hidden = false;
  $("issued").hidden = true;
  $("key").textContent = "";
  $("search").value = "";
  $("statusFilter").value = "all";
  $("sortOrder").value = "newest";
  setStatus("관리자 연결이 종료되었습니다.");
});
$("search").addEventListener("input", renderLicenses);
$("customerSearch").addEventListener("input", renderCustomers);
$("refreshCustomers").addEventListener("click", async () => {
  try {
    const data = await api("/v1/admin/customers");
    customerItems = data.customers || [];
    renderCustomers();
    statusText("고객 목록을 새로고침했습니다.", "ok");
  } catch (error) { statusText(error.message, "error"); }
});
$("statusFilter").addEventListener("change", renderLicenses);
$("sortOrder").addEventListener("change", renderLicenses);
