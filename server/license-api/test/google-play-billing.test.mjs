import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, verify } from 'node:crypto';
import { createGooglePlayBillingVerifier, GooglePlayBillingError } from '../src/google-play-billing.mjs';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const serviceAccountJson = JSON.stringify({
  type: 'service_account',
  client_email: 'guardian-billing@example.iam.gserviceaccount.com',
  private_key: privateKey.export({ type: 'pkcs8', format: 'pem' })
});
const now = () => Date.parse('2026-11-21T12:00:00.000Z');
const activeSubscription = (overrides = {}) => ({
  subscriptionState: 'SUBSCRIPTION_STATE_ACTIVE',
  acknowledgementState: 'ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED',
  lineItems: [{
    productId: 'guardian_pro_monthly',
    expiryTime: '2026-12-21T12:00:00.000Z',
    latestSuccessfulOrderId: 'GPA.1234-5678-9012-34567'
  }],
  ...overrides
});

function mockFetch(subscription, calls = []) {
  return async (url, init = {}) => {
    calls.push({ url: String(url), init });
    if (String(url) === 'https://oauth2.googleapis.com/token') {
      const form = new URLSearchParams(init.body);
      const assertion = form.get('assertion');
      const parts = assertion.split('.');
      assert.equal(parts.length, 3);
      const unsigned = parts.slice(0, 2).join('.');
      assert.equal(verify('RSA-SHA256', Buffer.from(unsigned), publicKey, Buffer.from(parts[2], 'base64url')), true);
      const claims = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
      assert.equal(claims.scope, 'https://www.googleapis.com/auth/androidpublisher');
      assert.equal(claims.iss, 'guardian-billing@example.iam.gserviceaccount.com');
      return Response.json({ access_token: 'publisher-test-token', expires_in: 3600 });
    }
    assert.equal(init.headers.authorization, 'Bearer publisher-test-token');
    return Response.json(subscription);
  };
}

test('verifies a subscription through the authenticated Publisher API and returns normalized entitlement', async () => {
  const calls = [];
  const verifier = createGooglePlayBillingVerifier({
    serviceAccountJson, fetchImpl: mockFetch(activeSubscription(), calls), now
  });
  const result = await verifier.verifySubscription({
    purchaseToken: 'purchase/token+with=special',
    productId: 'guardian_pro_monthly'
  });
  assert.equal(result.verified, true);
  assert.equal(result.entitled, true);
  assert.equal(result.productId, 'guardian_pro_monthly');
  assert.equal(result.subscriptionState, 'SUBSCRIPTION_STATE_ACTIVE');
  assert.equal(result.expiryTime, '2026-12-21T12:00:00.000Z');
  assert.equal(result.orderId, 'GPA.1234-5678-9012-34567');
  assert.ok(calls[1].url.includes(encodeURIComponent('purchase/token+with=special')));
  assert.equal(calls.length, 2);
});

test('does not grant entitlement to expired, pending or account-on-hold subscriptions', async () => {
  const cases = [
    activeSubscription({ lineItems: [{ productId: 'guardian_pro_monthly', expiryTime: '2026-11-20T12:00:00.000Z' }] }),
    activeSubscription({ subscriptionState: 'SUBSCRIPTION_STATE_PENDING' }),
    activeSubscription({ subscriptionState: 'SUBSCRIPTION_STATE_ON_HOLD' })
  ];
  for (const subscription of cases) {
    const verifier = createGooglePlayBillingVerifier({
      serviceAccountJson, fetchImpl: mockFetch(subscription), now
    });
    const result = await verifier.verifySubscription({ purchaseToken: 'valid-token', productId: 'guardian_pro_monthly' });
    assert.equal(result.verified, true);
    assert.equal(result.entitled, false);
  }
});

test('a canceled subscription remains entitled only until its paid expiry', async () => {
  const verifier = createGooglePlayBillingVerifier({
    serviceAccountJson,
    fetchImpl: mockFetch(activeSubscription({ subscriptionState: 'SUBSCRIPTION_STATE_CANCELED' })),
    now
  });
  const result = await verifier.verifySubscription({ purchaseToken: 'valid-token', productId: 'guardian_pro_monthly' });
  assert.equal(result.entitled, true);
});

test('rejects a purchase token whose returned product does not match the requested SKU', async () => {
  const verifier = createGooglePlayBillingVerifier({
    serviceAccountJson,
    fetchImpl: mockFetch(activeSubscription({ lineItems: [{ productId: 'other.product', expiryTime: '2026-12-21T12:00:00.000Z' }] })),
    now
  });
  await assert.rejects(
    verifier.verifySubscription({ purchaseToken: 'valid-token', productId: 'guardian_pro_monthly' }),
    error => error instanceof GooglePlayBillingError && error.code === 'purchase_product_mismatch' && error.status === 403
  );
});

test('rejects malformed credentials and untrusted request parameters before network access', async () => {
  assert.throws(
    () => createGooglePlayBillingVerifier({ serviceAccountJson: '{"type":"service_account"}', fetchImpl: async () => {} }),
    error => error instanceof GooglePlayBillingError && error.code === 'billing_credentials_invalid'
  );
  let requests = 0;
  const verifier = createGooglePlayBillingVerifier({
    serviceAccountJson, fetchImpl: async () => { requests++; }, now
  });
  await assert.rejects(verifier.verifySubscription({ purchaseToken: '', productId: 'guardian_pro_monthly' }));
  await assert.rejects(verifier.verifySubscription({ purchaseToken: 'valid-token', productId: '../wrong' }));
  assert.equal(requests, 0);
});

test('reuses a short-lived OAuth access token across purchase lookups', async () => {
  const calls = [];
  const verifier = createGooglePlayBillingVerifier({
    serviceAccountJson, fetchImpl: mockFetch(activeSubscription(), calls), now
  });
  await verifier.verifySubscription({ purchaseToken: 'token-one', productId: 'guardian_pro_monthly' });
  await verifier.verifySubscription({ purchaseToken: 'token-two', productId: 'guardian_pro_monthly' });
  assert.equal(calls.filter(call => call.url === 'https://oauth2.googleapis.com/token').length, 1);
});
