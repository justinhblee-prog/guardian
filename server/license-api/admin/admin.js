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
    if (item.status === 'active') {
      const button = document.createElement('button'); button.className = 'danger'; button.textContent = '정지';
      button.addEventListener('click', async () => {
        if (!confirm('이 라이선스를 정지하고 활성 기기를 모두 해제할까요?')) return;
        try { await api(`/v1/admin/licenses/${encodeURIComponent(item.id)}/revoke`, { method: 'POST', body: '{}' }); await load(); status('라이선스를 정지했습니다.'); }
        catch (error) { status(error.message); }
      });
      row.append(button);
    }
    root.append(row);
  }
}
function renderEvents(items) {
  const root = $('events');
  root.replaceChildren();
  if (!items.length) { root.textContent = '감사 기록이 없습니다.'; return; }
  const labels = {
    issued: '라이선스 발급', activated: '기기 활성화',
    deactivated: '기기 비활성화', revoked: '라이선스 정지'
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
