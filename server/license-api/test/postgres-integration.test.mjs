import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { createHash, generateKeyPairSync, randomUUID, verify } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createPostgresRateLimiter } from '../src/rate-limit.mjs';

const databaseUrl = process.env.DATABASE_URL;
if (!databaseUrl) {
  test('PostgreSQL license API integration suite', { skip: 'DATABASE_URL is not configured outside CI integration runs.' }, () => {});
} else {
  process.env.NODE_ENV = 'test';
  process.env.GUARDIAN_ADMIN_TOKEN = 'integration-admin-token-which-is-long-enough';
  process.env.GUARDIAN_LICENSE_LOOKUP_SECRET = 'integration-lookup-secret-at-least-32-characters';
  const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
  process.env.GUARDIAN_LICENSE_PRIVATE_KEY = privateKey.export({ type: 'pkcs8', format: 'pem' }).toString();
  process.env.AWS_ACCESS_KEY_ID = 'integration-storage-access-key';
  process.env.AWS_SECRET_ACCESS_KEY = 'integration-storage-secret-key-long-enough';
  process.env.AWS_ENDPOINT_URL_S3 = 'https://storage.integration.example/';
  process.env.AWS_REGION = 'ap-southeast-1';

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
    assert.ok(token.payload.startsWith(["tokenVersion=1", "product=guardian", "entitlement=pro"].join(String.fromCharCode(10))));
    const issuedAt = Number(/^issuedAt=(\d+)$/m.exec(token.payload)?.[1]);
    const expiresAt = Number(/^expiresAt=(\d+)$/m.exec(token.payload)?.[1]);
    assert.equal(token.expiresIn, expiresAt - issuedAt);
    assert.ok(token.expiresIn > 0 && token.expiresIn <= 24 * 60 * 60);
  };

  before(async () => {
    await pool.query('TRUNCATE guardian_update_manifests, api_rate_limit_buckets, support_tickets, license_events, activations, licenses RESTART IDENTITY CASCADE');
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    baseUrl = `http://127.0.0.1:${server.address().port}`;
  });

  test('PostgreSQL rate limiter prunes expired buckets during bounded cleanup', async () => {
    const cleanup = createPostgresRateLimiter(pool, {
      windowMs: 1_000,
      maxRequests: 2,
      cleanupEvery: 1
    });
    const staleKey = `expired-rate-limit-test:${randomUUID()}`;
    try {
      await pool.query(
        `INSERT INTO api_rate_limit_buckets(bucket_key, window_started_at, request_count)
         VALUES($1, clock_timestamp() - interval '2 seconds', 1)`,
        [staleKey]
      );
      await cleanup(`active-rate-limit-test:${randomUUID()}`);
      const stale = await pool.query(
        'SELECT bucket_key FROM api_rate_limit_buckets WHERE bucket_key=$1',
        [staleKey]
      );
      assert.equal(stale.rowCount, 0);
    } finally {
      await pool.query(
        'DELETE FROM api_rate_limit_buckets WHERE bucket_key LIKE $1',
        ['%rate-limit-test:%']
      );
    }
  });

  test('license issuance stores only salted hashes and an HMAC lookup index, never the plaintext key', async () => {
    const issued = await issue(1);
    const stored = await pool.query(
      `SELECT key_salt, key_hash, key_lookup, to_jsonb(l)::text AS serialized
       FROM licenses l
       WHERE id=$1`,
      [issued.license.id]
    );
    assert.equal(stored.rowCount, 1);
    const row = stored.rows[0];
    assert.match(row.key_salt, /^[a-f0-9]{32}$/i);
    assert.match(row.key_hash, /^[a-f0-9]{128}$/i);
    assert.match(row.key_lookup, /^[a-f0-9]{64}$/i);
    assert.equal(row.serialized.includes(issued.licenseKey), false);
    assert.notEqual(row.key_lookup, issued.licenseKey);
    assert.equal(await (async () => {
      const lookup = await pool.query(
        'SELECT id FROM licenses WHERE key_lookup=$1',
        [row.key_lookup]
      );
      return lookup.rowCount;
    })(), 1);
  });

  test('database pool applies bounded connection, statement and transaction timeouts', () => {
    assert.equal(apiPool.options.max, 10);
    assert.equal(apiPool.options.connectionTimeoutMillis, 5_000);
    assert.equal(apiPool.options.idleTimeoutMillis, 30_000);
    assert.equal(apiPool.options.statement_timeout, 10_000);
    assert.equal(apiPool.options.query_timeout, 12_000);
    assert.equal(apiPool.options.idle_in_transaction_session_timeout, 15_000);
  });

  test('PostgreSQL rate limits are shared across API instances and concurrent requests', async () => {
    const firstInstance = createPostgresRateLimiter(pool, { windowMs: 60_000, maxRequests: 3 });
    const secondInstance = createPostgresRateLimiter(pool, { windowMs: 60_000, maxRequests: 3 });
    const key = `shared-rate-limit-test:${randomUUID()}`;
    try {
      const decisions = await Promise.all(
        Array.from({ length: 12 }, (_, index) =>
          (index % 2 === 0 ? firstInstance : secondInstance)(key)
        )
      );
      assert.equal(decisions.filter(item => item.allowed).length, 3);
      assert.equal(decisions.filter(item => !item.allowed).length, 9);
      assert.ok(decisions.every(item => item.limit === 3));
    } finally {
      await pool.query('DELETE FROM api_rate_limit_buckets WHERE bucket_key=$1', [key]);
    }
  });

  test('stable is public while beta/dev updates require admin and APK publication verifies bytes', async () => {
    const apkBytes = Buffer.from('guardian-private-update-apk-test-fixture');
    const sha256 = createHash('sha256').update(apkBytes).digest('hex');
    const metadata = {
      channel: 'dev',
      versionCode: 876,
      versionName: '0.8.76-dev',
      sha256,
      signerSha256: 'b'.repeat(64),
      sizeBytes: apkBytes.length,
      minSupportedVersionCode: 1,
      releaseNotes: 'private development test'
    };
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (input, init = {}) => {
      const target = new URL(typeof input === 'string' ? input : input.url);
      if (target.host === 'storage.integration.example') {
        assert.equal(init.method, undefined);
        return new Response(apkBytes, {
          status: 200,
          headers: { 'content-length': String(apkBytes.length) }
        });
      }
      return originalFetch(input, init);
    };
    try {
      const upload = await post('/v1/admin/updates/upload-url', metadata, adminHeaders);
      assert.equal(upload.status, 200);
      const uploadInfo = await upload.json();
      assert.equal(uploadInfo.method, 'PUT');
      assert.equal(new URL(uploadInfo.uploadUrl).searchParams.get('X-Amz-Expires'), '600');
      assert.match(uploadInfo.objectKey, /^android\/dev\/876-[a-f0-9]{64}\.apk$/);

      const published = await post('/v1/admin/updates/publish', {
        ...metadata, objectKey: uploadInfo.objectKey
      }, adminHeaders);
      assert.equal(published.status, 200);
      assert.equal((await published.json()).update.versionCode, 876);

      const publicStable = await fetch(`${baseUrl}/v1/updates/stable`);
      assert.equal(publicStable.status, 200);
      assert.deepEqual(await publicStable.json(), { update: null });

      const privateWithoutToken = await fetch(`${baseUrl}/v1/updates/dev`);
      assert.equal(privateWithoutToken.status, 401);
      const privateWithToken = await fetch(`${baseUrl}/v1/updates/dev`, { headers: adminHeaders });
      assert.equal(privateWithToken.status, 200);
      const privateUpdate = (await privateWithToken.json()).update;
      assert.equal(privateUpdate.versionCode, 876);
      assert.equal(new URL(privateUpdate.apkUrl).searchParams.get('X-Amz-Expires'), '300');
    } finally {
      globalThis.fetch = originalFetch;
    }
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

  test('customer directory never discloses customer PII without admin authentication', async () => {
    const response = await fetch(`${baseUrl}/v1/admin/customers`);
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { error: '관리자 인증에 실패했습니다.' });
  });

  test('admin routes require the Bearer authorization scheme', async () => {
    const bare = await post('/v1/admin/licenses/not-a-uuid/revoke', {}, {
      authorization: process.env.GUARDIAN_ADMIN_TOKEN
    });
    assert.equal(bare.status, 401);
    const malformed = await post('/v1/admin/licenses/not-a-uuid/revoke', {}, adminHeaders);
    assert.equal(malformed.status, 400);
  });

  test('admin customer directory stores and searches validated customer details', async () => {
    const email = `customer-${randomUUID()}@example.test`;
    const response = await post('/v1/admin/licenses', {
      validDays: 30,
      maxDevices: 2,
      customer: { name: 'Integration Customer', email, orderRef: 'ORDER-TEST-001' }
    }, adminHeaders);
    assert.equal(response.status, 200);
    const issued = await response.json();
    assert.match(issued.licenseKey, /^GDN-/);
    const listed = await fetch(`${baseUrl}/v1/admin/customers?q=${encodeURIComponent(email)}`, { headers: adminHeaders });
    assert.equal(listed.status, 200);
    const data = await listed.json();
    assert.equal(data.customers.length, 1);
    assert.equal(data.customers[0].name, 'Integration Customer');
    assert.equal(data.customers[0].email, email);
    assert.equal(data.customers[0].license_count, 1);
    assert.equal(data.customers[0].active_licenses, 1);
    const linked = await pool.query('SELECT customer_id,order_ref FROM licenses WHERE id=$1', [issued.license.id]);
    assert.ok(linked.rows[0].customer_id);
    assert.equal(linked.rows[0].order_ref, 'ORDER-TEST-001');
  });

  test('customer issuance rejects malformed email and overlong fields', async () => {
    const invalidEmail = await post('/v1/admin/licenses', {
      validDays: 30, maxDevices: 1, customer: { name: 'Test', email: 'not-an-email' }
    }, adminHeaders);
    assert.equal(invalidEmail.status, 400);
    const invalidName = await post('/v1/admin/licenses', {
      validDays: 30, maxDevices: 1, customer: { name: 'x'.repeat(121), email: 'valid@example.test' }
    }, adminHeaders);
    assert.equal(invalidName.status, 400);
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

      // Start the refresh while the deactivation transaction owns the
      // activation row lock. Give the HTTP request a short opportunity to
      // enter the API before releasing the lock; correctness is asserted by
      // the response and final database state below, not pg_stat_activity
      // polling (which is timing-sensitive on shared CI runners).
      await new Promise(resolve => setTimeout(resolve, 150));

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

  test('expired licenses cannot refresh Pro but can still release their device activation', async () => {
    const created = await issue(1);
    const device = '7'.repeat(64);
    const activation = await post('/v1/licenses/activate', {
      licenseKey: created.licenseKey, deviceBinding: device, appVersion: 'expired-license-deactivate'
    });
    assert.equal(activation.status, 200);

    await pool.query(
      "UPDATE licenses SET expires_at=now()-interval '1 second' WHERE id=$1",
      [created.license.id]
    );

    const status = await post('/v1/licenses/status', {
      deviceBinding: device, licenseRef: created.license.license_ref
    });
    assert.equal(status.status, 200);
    assert.equal((await status.json()).status, 'free');

    const refresh = await post('/v1/licenses/refresh', {
      deviceBinding: device, licenseRef: created.license.license_ref
    });
    assert.equal(refresh.status, 403);

    const deactivate = await post('/v1/licenses/deactivate', {
      deviceBinding: device, licenseRef: created.license.license_ref
    });
    assert.equal(deactivate.status, 200);
    assert.equal((await deactivate.json()).deactivated, 1);

    const active = await pool.query(
      'SELECT count(*)::int AS n FROM activations WHERE license_id=$1 AND deactivated_at IS NULL',
      [created.license.id]
    );
    assert.equal(active.rows[0].n, 0);
  });

  test('admin can inspect activations and release one device without revoking the license', async () => {
    const created = await issue(2);
    const firstDevice = 'c'.repeat(64);
    const secondDevice = 'd'.repeat(64);
    for (const [deviceBinding, appVersion] of [
      [firstDevice, 'pixel-test'],
      [secondDevice, 'tablet-test']
    ]) {
      const response = await post('/v1/licenses/activate', {
        licenseKey: created.licenseKey, deviceBinding, appVersion
      });
      assert.equal(response.status, 200);
    }

    const path = `/v1/admin/licenses/${encodeURIComponent(created.license.id)}/activations`;
    const unauthorized = await fetch(`${baseUrl}${path}`);
    assert.equal(unauthorized.status, 401);

    const listing = await fetch(`${baseUrl}${path}`, { headers: adminHeaders });
    assert.equal(listing.status, 200);
    const activations = (await listing.json()).activations;
    assert.equal(activations.length, 2);
    const first = activations.find(item => item.device_hint === firstDevice.slice(-8));
    assert.ok(first);
    assert.equal(first.app_version, 'pixel-test');

    const invalidId = await post(
      `/v1/admin/licenses/${encodeURIComponent(created.license.id)}/activations/not-a-uuid/deactivate`,
      {},
      adminHeaders
    );
    assert.equal(invalidId.status, 400);

    const released = await post(
      `${path}/${encodeURIComponent(first.id)}/deactivate`,
      {},
      adminHeaders
    );
    assert.equal(released.status, 200);
    assert.deepEqual(await released.json(), { deactivated: true });

    const firstRefresh = await post('/v1/licenses/refresh', {
      deviceBinding: firstDevice, licenseRef: created.license.license_ref
    });
    assert.equal(firstRefresh.status, 403);
    const secondRefresh = await post('/v1/licenses/refresh', {
      deviceBinding: secondDevice, licenseRef: created.license.license_ref
    });
    assert.equal(secondRefresh.status, 200);
    assertSignedToken(await secondRefresh.json());

    const audit = await fetch(`${baseUrl}/v1/admin/events`, { headers: adminHeaders });
    assert.equal(audit.status, 200);
    const events = (await audit.json()).events;
    assert.ok(events.some(event =>
      event.event_type === 'admin_device_deactivated' &&
      event.license_ref === created.license.license_ref &&
      event.metadata.deviceHint === firstDevice.slice(-8)
    ));

    const repeated = await post(
      `${path}/${encodeURIComponent(first.id)}/deactivate`,
      {},
      adminHeaders
    );
    assert.equal(repeated.status, 409);
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
      await pool.query("UPDATE products SET active=false WHERE code='guardian-pro'");
      const status = await post('/v1/licenses/status', { deviceBinding: device, licenseRef });
      assert.equal(status.status, 200);
      assert.equal((await status.json()).status, 'free');
      const refresh = await post('/v1/licenses/refresh', { deviceBinding: device, licenseRef });
      assert.equal(refresh.status, 403);
    } finally {
      await pool.query("UPDATE products SET active=true WHERE code='guardian-pro'");
    }
  });


  test('existing license databases can apply migrations 002 through 006 repeatedly', async () => {
    await pool.query('DROP TABLE IF EXISTS guardian_update_manifests; DROP TABLE IF EXISTS support_tickets; DROP TABLE IF EXISTS api_rate_limit_buckets;');
    for (const migrationPath of [
      new URL('../migrations/002_shared_rate_limit_buckets.sql', import.meta.url),
      new URL('../migrations/003_support_tickets.sql', import.meta.url),
      new URL('../migrations/004_customers.sql', import.meta.url),
      new URL('../migrations/005_shared_guardian_product.sql', import.meta.url),
      new URL('../migrations/006_private_update_channels.sql', import.meta.url)
    ]) {
      const sql = readFileSync(migrationPath, 'utf8');
      await pool.query(sql);
      await pool.query(sql);
    }
    const tables = await pool.query(
      "SELECT to_regclass('public.api_rate_limit_buckets') AS rate_limits, to_regclass('public.support_tickets') AS support_tickets, to_regclass('public.customers') AS customers, to_regclass('public.guardian_update_manifests') AS update_manifests, (SELECT code FROM products WHERE code='guardian-pro' AND active=true LIMIT 1) AS shared_product"
    );
    assert.equal(tables.rows[0].rate_limits, 'api_rate_limit_buckets');
    assert.equal(tables.rows[0].support_tickets, 'support_tickets');
    assert.equal(tables.rows[0].customers, 'customers');
    assert.equal(tables.rows[0].update_manifests, 'guardian_update_manifests');
    assert.equal(tables.rows[0].shared_product, 'guardian-pro');
  });

  test('admin license issuance enforces plan duration and records the selected plan', async () => {
    const valid = await post('/v1/admin/licenses', {
      planCode: 'monthly_30d', validDays: 30, maxDevices: 2
    }, adminHeaders);
    assert.equal(valid.status, 200);
    const issued = await valid.json();
    assert.match(issued.licenseKey, /^GDN-(?:[A-F0-9]{8}-){5}[A-F0-9]{8}$/);
    const event = await pool.query(
      `SELECT metadata FROM license_events WHERE license_id=$1 AND event_type='issued'`,
      [issued.license.id]
    );
    assert.equal(event.rowCount, 1);
    assert.equal(event.rows[0].metadata.planCode, 'monthly_30d');
    assert.equal(event.rows[0].metadata.days, 30);

    const mismatch = await post('/v1/admin/licenses', {
      planCode: 'monthly_30d', validDays: 31, maxDevices: 2
    }, adminHeaders);
    assert.equal(mismatch.status, 400);
    assert.match((await mismatch.json()).error, /기간 설정/);
  });


  test('customers can submit and privately track support tickets while admins reply securely', async () => {
    const submitted = await post('/v1/support/tickets', {
      category: 'suggestion',
      subject: '광고 차단 규칙 제안',
      message: '특정 앱에서 광고 도메인이 계속 노출됩니다. 확인 부탁드립니다.',
      email: 'customer@example.com'
    });
    assert.equal(submitted.status, 200);
    const receipt = await submitted.json();
    assert.match(receipt.ticketRef, /^GDN-[A-F0-9]{12}$/);
    assert.equal(typeof receipt.accessToken, 'string');
    assert.ok(receipt.accessToken.length >= 40);

    const stored = await pool.query(
      'SELECT id,access_token_hash,status FROM support_tickets WHERE ticket_ref=$1',
      [receipt.ticketRef]
    );
    assert.equal(stored.rowCount, 1);
    assert.equal(stored.rows[0].status, 'open');
    assert.notEqual(stored.rows[0].access_token_hash, receipt.accessToken);
    assert.equal(stored.rows[0].access_token_hash.length, 64);

    const unauthorized = await fetch(`${baseUrl}/v1/admin/support/tickets`);
    assert.equal(unauthorized.status, 401);
    const inbox = await fetch(`${baseUrl}/v1/admin/support/tickets`, { headers: adminHeaders });
    assert.equal(inbox.status, 200);
    const tickets = (await inbox.json()).tickets;
    const item = tickets.find(ticket => ticket.ticket_ref === receipt.ticketRef);
    assert.ok(item);
    assert.equal(Object.hasOwn(item, 'access_token_hash'), false);

    const reply = await post(`/v1/admin/support/tickets/${item.id}/reply`, {
      reply: '제보 감사합니다. 필터 규칙을 확인하고 다음 업데이트에 반영하겠습니다.',
      status: 'answered'
    }, adminHeaders);
    assert.equal(reply.status, 200);
    assert.equal((await reply.json()).ticket.status, 'answered');

    const lookup = await post('/v1/support/tickets/lookup', {
      ticketRef: receipt.ticketRef,
      accessToken: receipt.accessToken
    });
    assert.equal(lookup.status, 200);
    const ticket = (await lookup.json()).ticket;
    assert.equal(ticket.status, 'answered');
    assert.match(ticket.admin_reply, /제보 감사합니다/);

    const wrongToken = await post('/v1/support/tickets/lookup', {
      ticketRef: receipt.ticketRef,
      accessToken: 'x'.repeat(43)
    });
    assert.equal(wrongToken.status, 404);
  });

}
