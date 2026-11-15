let adminToken = '';
const $ = (id) => document.getElementById(id);
const status = (message) => { $('status').textContent = message; };
async function api(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: { 'content-type': 'application/json', authorization: `Bearer ${adminToken}`, ...(options.headers || {}) },
    cache: 'no-store'
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}
async function loadActivations(license, root) {
  root.textContent = '기기 등록을 불러오는 중...';
  try {
    const data = await api(`/v1/admin/licenses/${encodeURIComponent(license.id)}/activations`);
    renderActivations(license, root, data.activations || []);
  } catch (error) {
    root.textContent = error.message;
  }
}
function renderActivations(license, root, items) {
  root.replaceChildren();
  const heading = document.createElement('strong');
  heading.textContent = '등록 기기';
  root.append(heading);
  if (!items.length) {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = '등록된 기기가 없습니다.';
    root.append(empty);
    return;
  }
  for (const item of items) {
    const row = document.createElement('div');
    row.className = 'device-row';
    const info = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = `기기 ····${item.device_hint} · ${item.deactivated_at ? '해제됨' : '활성'}`;
    const detail = document.createElement('div');
    detail.className = 'muted';
    const lastSeen = new Date(item.last_seen_at).toLocaleString();
    detail.textContent = `앱 버전: ${item.app_version || '-'} · 최근 확인: ${lastSeen}`;
    info.append(title, detail);
    row.append(info);
    if (!item.deactivated_at) {
      const button = document.createElement('button');
      button.className = 'danger';
      button.textContent = '기기 해제';
      button.addEventListener('click', async () => {
        if (!confirm('이 기기만 라이선스에서 해제할까요? 다른 등록 기기와 라이선스는 유지됩니다.')) return;
        button.disabled = true;
        try {
          await api(
            `/v1/admin/licenses/${encodeURIComponent(license.id)}/activations/${encodeURIComponent(item.id)}/deactivate`,
            { method: 'POST', body: '{}' }
          );
          await load();
          status('선택한 기기의 등록을 해제했습니다.');
        } catch (error) {
          button.disabled = false;
          status(error.message);
        }
      });
      row.append(button);
    }
    root.append(row);
  }
}
function renderLicenses(items) {
  const root = $('licenses');
  root.replaceChildren();
  if (!items.length) { root.textContent = '발급된 라이선스가 없습니다.'; return; }
  for (const item of items) {
    const row = document.createElement('div'); row.className = 'row';
    const info = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = `${item.license_ref} · ${item.status}`;
    const detail = document.createElement('div'); detail.className = 'muted';
    detail.textContent = `만료: ${new Date(item.expires_at).toLocaleString()} · 기기: ${item.active_devices}/${item.max_devices}`;
    info.append(title, detail); row.append(info);
    const actions = document.createElement('div');
    actions.className = 'license-actions';
    const devicesButton = document.createElement('button');
    devicesButton.className = 'secondary';
    devicesButton.textContent = '기기 관리';
    const devicePanel = document.createElement('div');
    devicePanel.className = 'device-list';
    devicePanel.hidden = true;
    devicesButton.addEventListener('click', async () => {
      devicePanel.hidden = !devicePanel.hidden;
      if (!devicePanel.hidden) await loadActivations(item, devicePanel);
    });
    actions.append(devicesButton);
    if (item.status === 'active') {
      const button = document.createElement('button'); button.className = 'danger'; button.textContent = '라이선스 정지';
      button.addEventListener('click', async () => {
        if (!confirm('이 라이선스를 정지하고 활성 기기를 모두 해제할까요?')) return;
        try { await api(`/v1/admin/licenses/${encodeURIComponent(item.id)}/revoke`, { method: 'POST', body: '{}' }); await load(); status('라이선스를 정지했습니다.'); }
        catch (error) { status(error.message); }
      });
      actions.append(button);
    }
    row.append(actions);
    root.append(row, devicePanel);
  }
}
function renderEvents(items) {
  const root = $('events');
  root.replaceChildren();
  if (!items.length) { root.textContent = '감사 기록이 없습니다.'; return; }
  const labels = {
    issued: '라이선스 발급', activated: '기기 활성화',
    deactivated: '기기 비활성화', admin_device_deactivated: '관리자 기기 해제', revoked: '라이선스 정지'
  };
  for (const item of items) {
    const row = document.createElement('div'); row.className = 'row';
    const info = document.createElement('div');
    const title = document.createElement('strong');
    title.textContent = labels[item.event_type] || item.event_type;
    const detail = document.createElement('div'); detail.className = 'muted';
    const ref = item.license_ref || '연결된 라이선스 없음';
    const when = new Date(item.created_at).toLocaleString();
    const metadata = item.metadata && Object.keys(item.metadata).length
      ? ` · ${JSON.stringify(item.metadata)}` : '';
    detail.textContent = `${when} · ${ref} · ${item.actor}${metadata}`;
    info.append(title, detail); row.append(info); root.append(row);
  }
}
async function load() {
  const [licenses, audit] = await Promise.all([
    api('/v1/admin/licenses'),
    api('/v1/admin/events')
  ]);
  renderLicenses(licenses.licenses || []);
  renderEvents(audit.events || []);
}
$('connect').addEventListener('click', async () => {
  adminToken = $('token').value.trim();
  if (!adminToken) { status('관리자 토큰을 입력하세요.'); return; }
  try { await load(); status('관리자 인증 성공'); }
  catch (error) { adminToken = ''; status(error.message); }
});
$('issue').addEventListener('click', async () => {
  if (!adminToken) { status('먼저 관리자 인증을 진행하세요.'); return; }
  const validDays = Number($('days').value), maxDevices = Number($('devices').value);
  try {
    const data = await api('/v1/admin/licenses', { method: 'POST', body: JSON.stringify({ validDays, maxDevices }) });
    $('key').textContent = data.licenseKey;
    $('issued').hidden = false;
    await load(); status('발급 완료. 라이선스 키를 안전한 곳에 보관하세요.');
  } catch (error) { status(error.message); }
});
$('copy').addEventListener('click', async () => {
  try { await navigator.clipboard.writeText($('key').textContent); status('라이선스 키를 복사했습니다.'); }
  catch { status('복사할 수 없습니다. 키를 직접 선택해 복사하세요.'); }
});
