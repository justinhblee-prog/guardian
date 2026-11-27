import { createHash, createPrivateKey, randomBytes, randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import pg from 'pg';
import { createPostgresRateLimiter, createRateLimiter } from './rate-limit.mjs';
import {
  adminTokenMatches, hashLicenseKey, issueEntitlementToken, newLicenseKey,
  validateDeviceBinding, verifyLicenseKey
} from './license-core.mjs';

export function createLicenseApiRuntime(environment = process.env, options = {}) {
const { Pool } = pg;
const DATABASE_URL = environment.DATABASE_URL;
const ADMIN_TOKEN = environment.GUARDIAN_ADMIN_TOKEN;
const PRIVATE_KEY = environment.GUARDIAN_LICENSE_PRIVATE_KEY;
const LOOKUP_SECRET = environment.GUARDIAN_LICENSE_LOOKUP_SECRET;
const TOKEN_TTL_SECONDS = 24 * 60 * 60;
const BODY_LIMIT = 16 * 1024;
const rateLimitOptions = {
  windowMs: Number(environment.GUARDIAN_RATE_LIMIT_WINDOW_MS || 60_000),
  maxRequests: Number(environment.GUARDIAN_RATE_LIMIT_MAX || 30)
};
const RATE_LIMITED_PATHS = new Set([
  '/v1/licenses/activate', '/v1/licenses/refresh', '/v1/licenses/deactivate',
  '/v1/licenses/status', '/v1/admin/licenses', '/v1/admin/events', '/v1/admin/customers',
  '/v1/support/tickets', '/v1/admin/support/tickets', '/v1/support/tickets/lookup'
]);
const pool = DATABASE_URL ? new Pool({
  connectionString: DATABASE_URL,
  max: Number(environment.PG_POOL_MAX || 10),
  connectionTimeoutMillis: 5_000,
  idleTimeoutMillis: 30_000,
  statement_timeout: 10_000,
  query_timeout: 12_000,
  idle_in_transaction_session_timeout: 15_000,
  ssl: environment.PGSSLMODE === 'require' ? { rejectUnauthorized: true } : undefined
}) : null;
const rateLimit = pool
  ? createPostgresRateLimiter(pool, rateLimitOptions)
  : createRateLimiter(rateLimitOptions);

function json(res, status, value) {
  const body = JSON.stringify(value);
  res.writeHead(status, {
    'content-type': 'application/json; charset=utf-8',
    'cache-control': 'no-store',
    'x-content-type-options': 'nosniff',
    'x-frame-options': 'DENY',
    'referrer-policy': 'no-referrer',
    'permissions-policy': 'camera=(), microphone=(), geolocation=()',
    'content-length': Buffer.byteLength(body)
  });
  res.end(body);
}
async function readBody(req) {
  let size = 0;
  const chunks = [];
  for await (const chunk of req) {
    size += chunk.length;
    if (size > BODY_LIMIT) throw Object.assign(new Error('요청 크기가 너무 큽니다.'), { status: 413 });
    chunks.push(chunk);
  }
  let requestText;
  try {
    requestText = new TextDecoder('utf-8', { fatal: true }).decode(Buffer.concat(chunks));
  } catch {
    throw Object.assign(new Error('요청 본문은 올바른 UTF-8이어야 합니다.'), { status: 400 });
  }
  let parsed;
  try {
    parsed = JSON.parse(requestText);
  } catch {
    throw Object.assign(new Error('JSON 요청 형식이 올바르지 않습니다.'), { status: 400 });
  }
  // Every API route consumes named request fields. Reject null, arrays and
  // primitive JSON values here so malformed shapes never become server 500s.
  if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw Object.assign(new Error('JSON 요청 본문은 객체여야 합니다.'), { status: 400 });
  }
  return parsed;
}
function requireEnv() {
  if (!pool) throw Object.assign(new Error('서버 데이터베이스가 구성되지 않았습니다.'), { status: 503 });
  if (!LOOKUP_SECRET || LOOKUP_SECRET.length < 32) throw Object.assign(new Error('라이선스 조회용 서버 비밀값이 구성되지 않았습니다.'), { status: 503 });
  if (!ADMIN_TOKEN || ADMIN_TOKEN.length < 32) throw Object.assign(new Error('관리자 인증 토큰이 구성되지 않았습니다.'), { status: 503 });
}
function requireAdmin(req) {
  const authorization = req.headers.authorization || '';
  const match = /^Bearer\s+([^\s]+)$/i.exec(authorization);
  const supplied = match?.[1] || '';
  if (!adminTokenMatches(supplied, ADMIN_TOKEN)) {
    throw Object.assign(new Error('관리자 인증에 실패했습니다.'), { status: 401 });
  }
}
function validateUuid(value, label) {
  if (typeof value !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    throw Object.assign(new Error(`${label} 값이 올바르지 않습니다.`), { status: 400 });
  }
  return value.toLowerCase();
}
function validateLicenseRef(value) {
  // license_ref values are generated with crypto.randomUUID(); reject malformed
  // or oversized references before they reach PostgreSQL queries.
  if (typeof value !== 'string' ||
      !/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(value)) {
    throw Object.assign(new Error('라이선스 참조 값이 올바르지 않습니다.'), { status: 400 });
  }
  return value.toLowerCase();
}
async function listActivations(licenseId, req) {
  requireEnv();
  requireAdmin(req);
  const id = validateUuid(licenseId, '라이선스 ID');
  const license = await pool.query('SELECT id FROM licenses WHERE id=$1', [id]);
  if (!license.rowCount) throw Object.assign(new Error('라이선스를 찾을 수 없습니다.'), { status: 404 });
  const result = await pool.query(
    `SELECT id,
       right(device_binding, 8) AS device_hint,
       app_version, activated_at, last_seen_at, deactivated_at
     FROM activations
     WHERE license_id=$1
     ORDER BY activated_at DESC
     LIMIT 50`,
    [id]
  );
  return { activations: result.rows };
}
async function deactivateActivation(licenseId, activationId, req) {
  requireEnv();
  requireAdmin(req);
  const id = validateUuid(licenseId, '라이선스 ID');
  const activation = validateUuid(activationId, '기기 등록 ID');
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    // Match the lock order used by refresh so admin deactivation cannot race
    // with a renewal and issue a token after this device slot is released.
    const found = await client.query(
      `SELECT a.id, a.license_id, right(a.device_binding, 8) AS device_hint
       FROM activations a JOIN licenses l ON l.id=a.license_id
       WHERE l.id=$1 AND a.id=$2
       FOR UPDATE OF l, a`,
      [id, activation]
    );
    if (!found.rowCount) throw Object.assign(new Error('기기 등록을 찾을 수 없습니다.'), { status: 404 });
    const updated = await client.query(
      'UPDATE activations SET deactivated_at=now() WHERE id=$1 AND license_id=$2 AND deactivated_at IS NULL RETURNING id',
      [activation, id]
    );
    if (!updated.rowCount) throw Object.assign(new Error('이미 해제된 기기 등록입니다.'), { status: 409 });
    await recordEvent(client, id, 'admin_device_deactivated', 'admin', {
      activationId: activation,
      deviceHint: found.rows[0].device_hint
    });
    await client.query('COMMIT');
    return { deactivated: true };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}
function requireSigner() {
  if (!PRIVATE_KEY) throw Object.assign(new Error('라이선스 서명 키가 서버에 구성되지 않았습니다.'), { status: 503 });
  try {
    const key = createPrivateKey(PRIVATE_KEY.replace(/\\n/g, '\n'));
    if (key.asymmetricKeyType !== 'rsa' || (key.asymmetricKeyDetails?.modulusLength || 0) < 2048 || (key.asymmetricKeyDetails?.modulusLength || 0) > 4096) {
      throw new Error('weak_or_non_rsa_key');
    }
    return key;
  } catch {
    throw Object.assign(new Error('RSA 2048~4096비트 범위의 라이선스 서명 키 구성이 올바르지 않습니다.'), { status: 503 });
  }
}
function tokenFor(row, deviceBinding) {
  const token = issueEntitlementToken({
    licenseRef: row.license_ref,
    deviceBinding,
    expiresAt: row.expires_at,
    privateKey: requireSigner(),
    maxTtlSeconds: TOKEN_TTL_SECONDS
  });
  const issuedAt = Number(/^issuedAt=(\d+)$/m.exec(token.payload)?.[1]);
  const expiresAt = Number(/^expiresAt=(\d+)$/m.exec(token.payload)?.[1]);
  if (!Number.isFinite(issuedAt) || !Number.isFinite(expiresAt) || expiresAt <= issuedAt) {
    throw new Error('entitlement_token_expiry_invalid');
  }
  return { ...token, expiresIn: expiresAt - issuedAt };
}
async function recordEvent(client, licenseId, type, actor, metadata = {}) {
  await client.query(
    'INSERT INTO license_events(license_id,event_type,actor,metadata) VALUES($1,$2,$3,$4::jsonb)',
    [licenseId, type, actor, JSON.stringify(metadata)]
  );
}
async function activate(body) {
  requireEnv();
  const device = validateDeviceBinding(body.deviceBinding);
  const appVersion = typeof body.appVersion === 'string' ? body.appVersion.slice(0, 40) : '';
  if (typeof body.licenseKey !== 'string') throw Object.assign(new Error('라이선스 키가 필요합니다.'), { status: 400 });
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const candidate = await hashLicenseKey(body.licenseKey, LOOKUP_SECRET, '00000000000000000000000000000000');
    const found = await client.query(
      `SELECT l.*, p.code AS product_code, p.active AS product_active
       FROM licenses l JOIN products p ON p.id=l.product_id
       WHERE l.key_lookup=$1 AND l.status='active' AND l.expires_at>now() AND p.active=true
       FOR UPDATE OF l`, [candidate.keyLookup]
    );
    let license = null;
    if (found.rowCount && await verifyLicenseKey(body.licenseKey, found.rows[0].key_salt, found.rows[0].key_hash, LOOKUP_SECRET)) license = found.rows[0];
    if (!license) throw Object.assign(new Error('라이선스 키를 찾을 수 없거나 만료·정지되었습니다.'), { status: 403 });
    const existing = await client.query(
      'SELECT id FROM activations WHERE license_id=$1 AND device_binding=$2 AND deactivated_at IS NULL FOR UPDATE',
      [license.id, device]
    );
    if (existing.rowCount === 0) {
      const count = await client.query(
        'SELECT count(*)::int AS n FROM activations WHERE license_id=$1 AND deactivated_at IS NULL',
        [license.id]
      );
      if (count.rows[0].n >= license.max_devices) throw Object.assign(new Error('허용된 활성화 기기 수를 초과했습니다.'), { status: 409 });
      await client.query(
        'INSERT INTO activations(license_id,device_binding,app_version) VALUES($1,$2,$3) ON CONFLICT (license_id,device_binding) DO UPDATE SET app_version=EXCLUDED.app_version,activated_at=now(),last_seen_at=now(),deactivated_at=NULL',
        [license.id, device, appVersion]
      );
      await recordEvent(client, license.id, 'activated', 'device', { appVersion });
    } else {
      await client.query('UPDATE activations SET last_seen_at=now(),app_version=$3 WHERE license_id=$1 AND device_binding=$2 AND deactivated_at IS NULL', [license.id, device, appVersion]);
    }
    const token = tokenFor(license, device);
    await client.query('COMMIT');
    return { ...token, licenseStatus: 'pro', licenseRef: license.license_ref };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}
async function refresh(body) {
  requireEnv();
  const device = validateDeviceBinding(body.deviceBinding);
  const licenseRef = validateLicenseRef(body.licenseRef);
  const client = await pool.connect();
  try {
    // Serialize refresh against deactivation/revocation. Without the row lock,
    // a refresh could read an active slot, race with deactivation, then issue
    // a fresh signed token after the slot had already been released.
    await client.query('BEGIN');
    const result = await client.query(
      `/* guardian_license_refresh_lock */
       SELECT l.* FROM licenses l JOIN activations a ON a.license_id=l.id
       JOIN products p ON p.id=l.product_id
       WHERE a.device_binding=$1 AND l.license_ref=$2 AND a.deactivated_at IS NULL
       AND l.status='active' AND l.expires_at>now() AND p.active=true
       LIMIT 1 FOR UPDATE OF l, a`,
      [device, licenseRef]
    );
    if (!result.rowCount) throw Object.assign(new Error('활성 라이선스를 찾을 수 없습니다.'), { status: 403 });
    const touched = await client.query(
      'UPDATE activations SET last_seen_at=now() WHERE license_id=$1 AND device_binding=$2 AND deactivated_at IS NULL',
      [result.rows[0].id, device]
    );
    if (touched.rowCount !== 1) throw Object.assign(new Error('활성 라이선스를 찾을 수 없습니다.'), { status: 403 });
    const token = tokenFor(result.rows[0], device);
    await client.query('COMMIT');
    return { ...token, licenseStatus: 'pro', licenseRef: result.rows[0].license_ref };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally { client.release(); }
}
async function deactivate(body) {
  requireEnv();
  const device = validateDeviceBinding(body.deviceBinding);
  const licenseRef = validateLicenseRef(body.licenseRef);
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await client.query(
      'UPDATE activations a SET deactivated_at=now() FROM licenses l WHERE a.license_id=l.id AND a.device_binding=$1 AND l.license_ref=$2 AND a.deactivated_at IS NULL RETURNING a.license_id',
      [device, licenseRef]
    );
    for (const row of result.rows) {
      await recordEvent(client, row.license_id, 'deactivated', 'device');
    }
    await client.query('COMMIT');
    return { deactivated: result.rowCount };
  } catch (error) {
    await client.query('ROLLBACK');
    throw error;
  } finally {
    client.release();
  }
}
function supportTokenHash(token) {
  return createHash('sha256').update(token, 'utf8').digest('hex');
}
function validateSupportText(value, label, min, max) {
  if (typeof value !== 'string') throw Object.assign(new Error(`${label}을(를) 입력하세요.`), { status: 400 });
  const normalized = value.trim();
  if (normalized.length < min || normalized.length > max) {
    throw Object.assign(new Error(`${label}은(는) ${min}~${max}자여야 합니다.`), { status: 400 });
  }
  return normalized;
}
async function createSupportTicket(body) {
  requireEnv();
  const category = ['question','bug','suggestion','billing','license'].includes(body.category) ? body.category : 'question';
  const subject = validateSupportText(body.subject, '제목', 3, 120);
  const message = validateSupportText(body.message, '문의 내용', 10, 5000);
  const email = typeof body.email === 'string' ? body.email.trim().slice(0, 254) : '';
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw Object.assign(new Error('이메일 주소 형식을 확인하세요.'), { status: 400 });
  }
  const ticketRef = `GDN-${randomBytes(6).toString('hex').toUpperCase()}`;
  const accessToken = randomBytes(32).toString('base64url');
  await pool.query(
    `INSERT INTO support_tickets(ticket_ref,access_token_hash,email,category,subject,message)
     VALUES($1,$2,$3,$4,$5,$6)`,
    [ticketRef, supportTokenHash(accessToken), email, category, subject, message]
  );
  return { ticketRef, accessToken, status: 'open', message: '문의가 접수되었습니다. 접수 번호와 조회용 비밀 코드를 안전하게 보관하세요.' };
}
async function getSupportTicket(ticketRef, token) {
  requireEnv();
  if (typeof ticketRef !== 'string' || !/^GDN-[A-F0-9]{12}$/.test(ticketRef) ||
      typeof token !== 'string' || token.length < 32 || token.length > 100) {
    throw Object.assign(new Error('문의 조회 정보가 올바르지 않습니다.'), { status: 400 });
  }
  const result = await pool.query(
    `SELECT ticket_ref,category,subject,message,status,admin_reply,created_at,updated_at,replied_at
     FROM support_tickets WHERE ticket_ref=$1 AND access_token_hash=$2`,
    [ticketRef, supportTokenHash(token)]
  );
  if (!result.rowCount) throw Object.assign(new Error('문의를 찾을 수 없거나 조회 코드가 올바르지 않습니다.'), { status: 404 });
  return { ticket: result.rows[0] };
}
async function listSupportTickets(req) {
  requireEnv();
  requireAdmin(req);
  const result = await pool.query(
    `SELECT id,ticket_ref,email,category,subject,message,status,admin_reply,created_at,updated_at,replied_at
     FROM support_tickets ORDER BY CASE status WHEN 'open' THEN 0 WHEN 'in_progress' THEN 1 WHEN 'answered' THEN 2 ELSE 3 END,
     created_at DESC LIMIT 300`
  );
  return { tickets: result.rows };
}
async function replySupportTicket(id, body, req) {
  requireEnv();
  requireAdmin(req);
  if (typeof id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) {
    throw Object.assign(new Error('문의 ID 형식이 올바르지 않습니다.'), { status: 400 });
  }
  const reply = validateSupportText(body.reply, '답변', 2, 5000);
  const status = ['answered','in_progress','closed'].includes(body.status) ? body.status : 'answered';
  const result = await pool.query(
    `UPDATE support_tickets SET admin_reply=$2,status=$3,updated_at=now(),replied_at=now()
     WHERE id=$1 RETURNING ticket_ref,status,updated_at,replied_at`,
    [id, reply, status]
  );
  if (!result.rowCount) throw Object.assign(new Error('문의를 찾을 수 없습니다.'), { status: 404 });
  return { ticket: result.rows[0] };
}
async function createLicense(body) {
  requireEnv();
  requireAdmin(body.req);
  let customer = null;
  if (body.customer !== undefined && body.customer !== null) {
    const name = typeof body.customer.name === 'string' ? body.customer.name.trim() : '';
    const email = typeof body.customer.email === 'string' ? body.customer.email.trim().toLowerCase() : '';
    const orderRef = typeof body.customer.orderRef === 'string' ? body.customer.orderRef.trim() : '';
    if (!name || name.length > 120 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || email.length > 254 ||
        orderRef.length > 120 || /[\r\n\0]/.test(name + email + orderRef)) {
      throw Object.assign(new Error('고객명, 올바른 이메일, 주문번호를 확인하세요.'), { status: 400 });
    }
    customer = { name, email, orderRef: orderRef || null };
  }
  const maxDevices = Number.isInteger(body.maxDevices) ? body.maxDevices : 1;
  const days = Number.isInteger(body.validDays) ? body.validDays : 365;
  const planDurations = { monthly_30d: 30, quarterly_90d: 90, annual_365d: 365, short_7d: 7 };
  const planCode = typeof body.planCode === 'string' ? body.planCode : 'custom';
  const expectedDays = planDurations[planCode];
  if (!['custom', ...Object.keys(planDurations)].includes(planCode) ||
      (expectedDays !== undefined && days !== expectedDays) ||
      maxDevices < 1 || maxDevices > 20 || days < 1 || days > 36500) {
    throw Object.assign(new Error('기기 수 또는 유효 기간 설정이 범위를 벗어났습니다.'), { status: 400 });
  }
  const key = newLicenseKey();
  const hashed = await hashLicenseKey(key, LOOKUP_SECRET);
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const product = await client.query("SELECT id FROM products WHERE code='guardian-pro' AND active=true");
    if (!product.rowCount) throw new Error('활성 제품을 찾을 수 없습니다.');
    let customerId = null;
    if (customer) {
      const saved = await client.query(
        `INSERT INTO customers(name,email)
         VALUES($1,$2)
         ON CONFLICT (lower(email)) DO UPDATE SET name=EXCLUDED.name,updated_at=now()
         RETURNING id`,
        [customer.name, customer.email]
      );
      customerId = saved.rows[0].id;
    }
    const ref = randomUUID();
    const inserted = await client.query(
      `INSERT INTO licenses(license_ref,product_id,customer_id,order_ref,key_salt,key_hash,key_lookup,expires_at,max_devices)
       VALUES($1,$2,$3,$4,$5,$6,$7,now()+($8::int * interval '1 day'),$9)
       RETURNING id,license_ref,status,expires_at,max_devices,created_at`,
      [ref, product.rows[0].id, customerId, customer?.orderRef || null, hashed.saltHex, hashed.hashHex, hashed.keyLookup, days, maxDevices]
    );
    await recordEvent(client, inserted.rows[0].id, 'issued', 'admin', { planCode, maxDevices, days, customerLinked: Boolean(customerId) });
    await client.query('COMMIT');
    return { license: inserted.rows[0], licenseKey: key };
  } catch (error) { await client.query('ROLLBACK'); throw error; }
  finally { client.release(); }
}
async function listLicenses(req) {
  requireEnv();
  requireAdmin(req);
  const result = await pool.query(
    `SELECT l.id,l.license_ref,l.status,l.expires_at,l.max_devices,l.created_at,
      count(a.id) FILTER (WHERE a.deactivated_at IS NULL)::int AS active_devices
     FROM licenses l LEFT JOIN activations a ON a.license_id=l.id
     GROUP BY l.id ORDER BY l.created_at DESC LIMIT 200`
  );
  return { licenses: result.rows };
}
async function listCustomers(req, search = '') {
  requireEnv();
  requireAdmin(req);
  const q = String(search).trim().slice(0, 100);
  const result = await pool.query(
    `SELECT c.id,c.name,c.email,c.created_at,c.updated_at,
       count(DISTINCT l.id)::int AS license_count,
       count(DISTINCT l.id) FILTER (WHERE l.status='active' AND l.expires_at>now())::int AS active_licenses,
       max(l.created_at) AS last_license_issued
     FROM customers c LEFT JOIN licenses l ON l.customer_id=c.id
     WHERE ($1='' OR c.name ILIKE '%' || $1 || '%' OR c.email ILIKE '%' || $1 || '%')
     GROUP BY c.id ORDER BY c.created_at DESC LIMIT 200`,
    [q]
  );
  return { customers: result.rows };
}
async function listAuditEvents(req) {
  requireEnv();
  requireAdmin(req);
  const result = await pool.query(
    `SELECT e.id,e.event_type,e.actor,e.metadata,e.created_at,l.license_ref
     FROM license_events e LEFT JOIN licenses l ON l.id=e.license_id
     ORDER BY e.created_at DESC,e.id DESC LIMIT 100`
  );
  return { events: result.rows };
}
async function revokeLicense(id, req) {
  requireEnv();
  requireAdmin(req);
  if (typeof id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id)) {
    throw Object.assign(new Error('라이선스 ID 형식이 올바르지 않습니다.'), { status: 400 });
  }
  const client = await pool.connect();
  try {
    await client.query('BEGIN');
    const result = await client.query(
      "UPDATE licenses SET status='revoked',revoked_at=now() WHERE id=$1 AND status<>'revoked' RETURNING id",
      [id]
    );
    if (!result.rowCount) throw Object.assign(new Error('라이선스를 찾을 수 없습니다.'), { status: 404 });
    await client.query('UPDATE activations SET deactivated_at=now() WHERE license_id=$1 AND deactivated_at IS NULL', [id]);
    await recordEvent(client, id, 'revoked', 'admin');
    await client.query('COMMIT');
    return { revoked: true };
  } catch (error) { await client.query('ROLLBACK'); throw error; }
  finally { client.release(); }
}



  async function handleNodeRequest(req, res) {
  try {
    const url = new URL(req.url, 'http://localhost');
    if (RATE_LIMITED_PATHS.has(url.pathname) || /^\/v1\/admin\/(?:licenses|support\/tickets)(?:\/|$)/.test(url.pathname)) {
      // Use the socket peer address; do not trust caller-supplied X-Forwarded-For.
      const peer = req.socket.remoteAddress || 'unknown';
      const rateLimitPath = url.pathname.startsWith('/v1/admin/licenses/')
        ? '/v1/admin/licenses/*'
        : /^\/v1\/admin\/support\/tickets\/[^/]+\/reply$/.test(url.pathname)
          ? '/v1/admin/support/tickets/*/reply'
          : url.pathname;
      // Shared PostgreSQL buckets keep limits consistent across API replicas.
      // The process-local limiter remains available only for isolated DB-less tests.
      let decision;
      try {
        decision = await rateLimit(`${peer}:${rateLimitPath}`);
      } catch (cause) {
        // Do not bypass limits when the shared store is unavailable; fail closed
        // and report service unavailability instead of an internal server error.
        throw Object.assign(new Error('요청 제한 저장소를 사용할 수 없습니다.'), {
          status: 503,
          cause
        });
      }
      res.setHeader('RateLimit-Limit', String(decision.limit));
      res.setHeader('RateLimit-Remaining', String(decision.remaining));
      if (!decision.allowed) {
        res.setHeader('Retry-After', String(decision.retryAfterSeconds));
        return json(res, 429, { error: '요청이 너무 많습니다. 잠시 후 다시 시도하세요.' });
      }
    }
    if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { ok: true, service: 'guardian-license-api' });
    if (req.method === 'GET' && url.pathname === '/ready') {
      try {
        requireEnv();
        requireSigner();
        const schema = await pool.query(
          `SELECT
             to_regclass('public.products') AS products,
             to_regclass('public.licenses') AS licenses,
             to_regclass('public.activations') AS activations,
             to_regclass('public.license_events') AS license_events,
             to_regclass('public.api_rate_limit_buckets') AS api_rate_limit_buckets,
             to_regclass('public.support_tickets') AS support_tickets,
             to_regclass('public.customers') AS customers`
        );
        const requiredTables = ['products', 'licenses', 'activations', 'license_events', 'api_rate_limit_buckets', 'support_tickets', 'customers'];
        if (requiredTables.some(table => !schema.rows[0]?.[table])) {
          throw new Error('license_database_schema_not_initialized');
        }
        await pool.query('SELECT 1');
        return json(res, 200, { ok: true, service: 'guardian-license-api', database: 'ready', signer: 'ready' });
      } catch {
        return json(res, 503, { ok: false, error: 'not_ready' });
      }
    }
    if (req.method === 'GET' && url.pathname === '/admin.js') {
      const script = options.adminAssets?.js ?? await readFile(new URL('../admin/admin.js', import.meta.url));
      res.writeHead(200, { 'content-type': 'text/javascript; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', 'x-frame-options': 'DENY', 'referrer-policy': 'no-referrer', 'permissions-policy': 'camera=(), microphone=(), geolocation=()' });
      return res.end(script);
    }
    if (req.method === 'GET' && url.pathname === '/admin-model.mjs') {
      const model = options.adminAssets?.model ?? await readFile(new URL('../admin/admin-model.mjs', import.meta.url));
      res.writeHead(200, { 'content-type': 'text/javascript; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', 'x-frame-options': 'DENY', 'referrer-policy': 'no-referrer' });
      return res.end(model);
    }
    if (req.method === 'GET' && url.pathname === '/admin') {
      const html = options.adminAssets?.html ?? await readFile(new URL('../admin/index.html', import.meta.url));
      res.writeHead(200, { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store', 'x-content-type-options': 'nosniff', 'content-security-policy': "default-src 'self'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'" });
      return res.end(html);
    }
    if (req.method === 'GET' && url.pathname === '/v1/admin/licenses') return json(res, 200, await listLicenses(req));
    if (req.method === 'GET' && url.pathname === '/v1/admin/support/tickets') return json(res, 200, await listSupportTickets(req));
    if (req.method === 'GET' && url.pathname === '/v1/admin/events') return json(res, 200, await listAuditEvents(req));
    if (req.method === 'GET' && url.pathname === '/v1/admin/customers') return json(res, 200, await listCustomers(req, url.searchParams.get('q') || ''));
    const activationListMatch = /^\/v1\/admin\/licenses\/([^/]+)\/activations$/.exec(url.pathname);
    if (req.method === 'GET' && activationListMatch) {
      return json(res, 200, await listActivations(activationListMatch[1], req));
    }
    if (req.method !== 'POST') return json(res, 405, { error: 'method_not_allowed' });
    const body = await readBody(req);
    let result;
    if (url.pathname === '/v1/support/tickets') result = await createSupportTicket(body);
    else if (url.pathname === '/v1/support/tickets/lookup') result = await getSupportTicket(body.ticketRef, body.accessToken);
    else if (/^\/v1\/admin\/support\/tickets\/[^/]+\/reply$/.test(url.pathname)) {
      const id = url.pathname.split('/')[5];
      result = await replySupportTicket(id, body, req);
    }
    else if (url.pathname === '/v1/licenses/activate') result = await activate(body);
    else if (url.pathname === '/v1/licenses/refresh') result = await refresh(body);
    else if (url.pathname === '/v1/licenses/deactivate') result = await deactivate(body);
    else if (url.pathname === '/v1/licenses/status') {
      const device = validateDeviceBinding(body.deviceBinding);
      const licenseRef = validateLicenseRef(body.licenseRef);
      requireEnv();
      const r = await pool.query(
        `SELECT l.status,l.expires_at,l.license_ref FROM licenses l
         JOIN activations a ON a.license_id=l.id
         JOIN products p ON p.id=l.product_id AND p.active=true
         WHERE a.device_binding=$1 AND l.license_ref=$2 AND a.deactivated_at IS NULL
           AND l.status='active' AND l.expires_at>now()
         LIMIT 1`, [device, licenseRef]);
      result = r.rowCount ? { status: r.rows[0].status, expiresAt: r.rows[0].expires_at, licenseRef: r.rows[0].license_ref } : { status: 'free' };
    } else if (url.pathname === '/v1/admin/licenses' ) result = await createLicense({ ...body, req });
    else if (url.pathname.startsWith('/v1/admin/licenses/') && url.pathname.endsWith('/revoke')) {
      const id = url.pathname.split('/')[4];
      result = await revokeLicense(id, req);
    } else {
      const activationMatch = /^\/v1\/admin\/licenses\/([^/]+)\/activations\/([^/]+)\/deactivate$/.exec(url.pathname);
      if (activationMatch) {
        result = await deactivateActivation(activationMatch[1], activationMatch[2], req);
      } else return json(res, 404, { error: 'not_found' });
    }
    return json(res, 200, result);
  } catch (error) {
    const status = Number.isInteger(error.status) ? error.status : 500;
    if (status >= 500) console.error('license_api_error', { name: error.name, message: error.message });
    return json(res, status, { error: status >= 500 ? 'internal_error' : error.message });
  }
  }

  return { handleNodeRequest, pool };
}
