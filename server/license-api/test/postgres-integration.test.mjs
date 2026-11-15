import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  test('PostgreSQL license API integration suite', { skip: 'DATABASE_URL is not configured outside CI integration runs.' }, () => {});
} else {
  process.env.NODE_ENV = 'test';
  process.env.GUARDIAN_ADMIN_TOKEN = 'integration-admin-token-which-is-long-enough';
  process.env.GUARDIAN_LICENSE_LOOKUP_SECRET = 'integration-lookup-secret-at-least-32-characters';
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  process.env.GUARDIAN_LICENSE_PRIVATE_KEY = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();

  const { default: pg } = await import('pg');
  const pool = new pg.Pool({ connectionString: databaseUrl });
  const { server, pool: apiPool } = await import('../src/server.mjs');
  let baseUrl;
  const adminHeaders = {
    'content-type': 'application/json',
    authorization: `Bearer ${process.env.GUARDIAN_ADMIN_TOKEN}`
  };
  const post = async (path, body, headers = {}) => fetch(`${baseUrl}${path}`, {
    method: 'POST',
    headers: { 'content-type': 'application/json', ...headers },
    body: JSON.stringify(body)
  });
  const issue = async (maxDevices = 1) => {
    const response = await post('/v1/admin/licenses', { validDays: 30, maxDevices }, adminHeaders);
    assert.equal(response.status, 200);
    return response.json();
  };
  const assertSignedToken = token => {
    assert.equal(typeof token.payload, 'string');
    assert.equal(verify(
      'RSA-SHA256',
      Buffer.from(token.payload, 'utf8'),
      publicKey,
      Buffer.from(token.signature, 'base64')
    ), true);
    assert.match(token.payload, /entitlement=pro/);
    const issuedAt = Number(/^issuedAt=(\d+)$/m.exec(token.payload)?.[1]);
    const expiresAt = Number(/^expiresAt=(\d+)$/m.exec(token.payload)?.[1]);
    assert.equal(token.expiresIn, expiresAt - issuedAt);
    assert.ok(token.expiresIn > 0 && token.expiresIn <= 24 * 60 * 60);
  };

  before(async () => {
    await pool.query('TRUNCATE license_events, activations, licenses RESTART IDENTITY CASCADE');
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    baseUrl = `http://127.0.0.1:${server.address().port}`;
  });

  test('readiness endpoint requires a reachable database and valid RSA signer', async () => {
    const response = await fetch(`${baseUrl}/ready`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { ok: true, service: 'guardian-license-api', database: 'ready', signer: 'ready' });
  });

  after(async () => {
    await new Promise((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
    await apiPool.end();
    await pool.end();
  });

  test('admin routes reject requests without a valid bearer token', async () => {
    const response = await fetch(`${baseUrl}/v1/admin/licenses`);
    assert.equal(response.status, 401);
  });

  test('admin routes require the Bearer authorization scheme', async () => {
    const bare = await post('/v1/admin/licenses/not-a-uuid/revoke', {}, {
      authorization: process.env.GUARDIAN_ADMIN_TOKEN
    });
    assert.equal(bare.status, 401);
    const malformed = await post('/v1/admin/licenses/not-a-uuid/revoke', {}, adminHeaders);
    assert.equal(malformed.status, 400);
  });

  test('activation issues a valid signed entitlement and is idempotent per device', async () => {
    const created = await issue(1);
    assert.match(created.licenseKey, /^GDN-(?:[A-F0-9]{8}-){5}[A-F0-9]{8}$/);
    const device = 'a'.repeat(64);
    const activate = () => post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding: device, appVersion: 'integration-test'
    });
    const first = await activate();
    assert.equal(first.status, 200);
    const firstToken = await first.json();
    assert.equal(firstToken.licenseStatus, 'pro');
    assertSignedToken(firstToken);

    const second = await activate();
    assert.equal(second.status, 200);
    assertSignedToken(await second.json());
    const count = await pool.query('SELECT count(*)::int AS n FROM activations WHERE license_id=$1 AND deactivated_at IS NULL', [created.license.id]);
    assert.equal(count.rows[0].n, 1);

    const overflow = await post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding: 'b'.repeat(64), appVersion: 'second-device'
    });
    assert.equal(overflow.status, 409);
  });

  test('status, refresh and deactivation are scoped to the activated device and license', async () => {
    const rows = await pool.query(
      'SELECT l.license_ref FROM licenses l JOIN activations a ON a.license_id=l.id WHERE a.device_binding=$1 AND a.deactivated_at IS NULL LIMIT 1',
      ['a'.repeat(64)]
    );
    const licenseRef = rows.rows[0].license_ref;
    const status = await post('/v1/licenses/status', { deviceBinding: 'a'.repeat(64), licenseRef });
    assert.equal(status.status, 200);
    assert.equal((await status.json()).status, 'active');

    const refresh = await post('/v1/licenses/refresh', { deviceBinding: 'a'.repeat(64), licenseRef });
    assert.equal(refresh.status, 200);
    assertSignedToken(await refresh.json());

    const wrongDevice = await post('/v1/licenses/refresh', { deviceBinding: 'c'.repeat(64), licenseRef });
    assert.equal(wrongDevice.status, 403);

    const deactivate = await post('/v1/licenses/deactivate', { deviceBinding: 'a'.repeat(64), licenseRef });
    assert.equal(deactivate.status, 200);
    assert.equal((await deactivate.json()).deactivated, 1);
    const deactivationEvent = await pool.query(
      "SELECT count(*)::int AS n FROM license_events WHERE event_type='deactivated' AND license_id=(SELECT id FROM licenses WHERE license_ref=$1)",
      [licenseRef]
    );
    assert.equal(deactivationEvent.rows[0].n, 1);

    const afterDeactivate = await post('/v1/licenses/status', { deviceBinding: 'a'.repeat(64), licenseRef });
    assert.equal((await afterDeactivate.json()).status, 'free');
    const refreshAfterDeactivate = await post('/v1/licenses/refresh', { deviceBinding: 'a'.repeat(64), licenseRef });
    assert.equal(refreshAfterDeactivate.status, 403);
  });


  test('a refresh waiting behind deactivation cannot issue a token for a released device slot', async () => {
    const created = await issue(1);
    const device = '8'.repeat(64);
    const activation = await post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding: device, appVersion: 'refresh-deactivation-race'
    });
    assert.equal(activation.status, 200);

    const lockClient = await pool.connect();
    let pendingRefresh;
    try {
      await lockClient.query('BEGIN');
      await lockClient.query(
        'SELECT id FROM activations WHERE license_id=$1 AND device_binding=$2 FOR UPDATE',
        [created.license.id, device]
      );

      pendingRefresh = post('/v1/licenses/refresh', {
        deviceBinding: device, licenseRef: created.license.license_ref
      });

      // Wait until the API transaction has reached its SELECT ... FOR UPDATE
      // and is blocked by the deactivation transaction's row lock.
      let refreshWaitingOnLock = false;
      for (let attempt = 0; attempt < 100; attempt += 1) {
        const waiting = await lockClient.query(
          `SELECT count(*)::int AS n FROM pg_stat_activity
           WHERE datname=current_database() AND pid<>pg_backend_pid()
             AND wait_event_type='Lock' AND query LIKE '%FOR UPDATE OF l, a%'`
        );
        if (waiting.rows[0].n > 0) {
          refreshWaitingOnLock = true;
          break;
        }
        await new Promise(resolve => setTimeout(resolve, 25));
      }
      assert.equal(refreshWaitingOnLock, true, 'refresh request should wait on the activation row lock');

      await lockClient.query(
        'UPDATE activations SET deactivated_at=now() WHERE license_id=$1 AND device_binding=$2',
        [created.license.id, device]
      );
      await lockClient.query('COMMIT');

      const response = await pendingRefresh;
      assert.equal(response.status, 403);
      const remaining = await pool.query(
        'SELECT count(*)::int AS n FROM activations WHERE license_id=$1 AND device_binding=$2 AND deactivated_at IS NULL',
        [created.license.id, device]
      );
      assert.equal(remaining.rows[0].n, 0);
    } catch (error) {
      await lockClient.query('ROLLBACK').catch(() => {});
      throw error;
    } finally {
      lockClient.release();
    }
  });

  test('concurrent activations cannot exceed the license device limit', async () => {
    const created = await issue(1);
    const activate = deviceBinding => post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding, appVersion: 'concurrency-test'
    });
    const responses = await Promise.all([activate('e'.repeat(64)), activate('f'.repeat(64))]);
    assert.deepEqual(responses.map(response => response.status).sort(), [200, 409]);
    const count = await pool.query(
      'SELECT count(*)::int AS n FROM activations WHERE license_id=$1 AND deactivated_at IS NULL',
      [created.license.id]
    );
    assert.equal(count.rows[0].n, 1);
  });

  test('revocation disables all device activations and is visible in audit history', async () => {
    const created = await issue(2);
    const device = 'd'.repeat(64);
    const activation = await post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding: device, appVersion: 'integration-test'
    });
    assert.equal(activation.status, 200);

    const revoke = await post(
      `/v1/admin/licenses/${encodeURIComponent(created.license.id)}/revoke`,
      {},
      adminHeaders
    );
    assert.equal(revoke.status, 200);
    assert.equal((await revoke.json()).revoked, true);

    const denied = await post('/v1/licenses/refresh', {
      deviceBinding: device, licenseRef: created.license.license_ref
    });
    assert.equal(denied.status, 403);

    const audit = await fetch(`${baseUrl}/v1/admin/events`, { headers: adminHeaders });
    assert.equal(audit.status, 200);
    const events = (await audit.json()).events;
    assert.ok(events.some(event => event.event_type === 'issued' && event.license_ref === created.license.license_ref));
    assert.ok(events.some(event => event.event_type === 'activated' && event.license_ref === created.license.license_ref));
    assert.ok(events.some(event => event.event_type === 'revoked' && event.license_ref === created.license.license_ref));
  });
  test('status does not report Pro while the product is disabled', async () => {
    const created = await issue(1);
    const device = '9'.repeat(64);
    const activation = await post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding: device, appVersion: 'product-disable-test'
    });
    assert.equal(activation.status, 200);
    const licenseRef = created.license.license_ref;
    try {
      await pool.query("UPDATE products SET active=false WHERE code='guardian-android-pro'");
      const status = await post('/v1/licenses/status', { deviceBinding: device, licenseRef });
      assert.equal(status.status, 200);
      assert.equal((await status.json()).status, 'free');
      const refresh = await post('/v1/licenses/refresh', { deviceBinding: device, licenseRef });
      assert.equal(refresh.status, 403);
    } finally {
      await pool.query("UPDATE products SET active=true WHERE code='guardian-android-pro'");
    }
  });

}
