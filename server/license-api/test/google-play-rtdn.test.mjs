import test from 'node:test';
import assert from 'node:assert/strict';
import { createSign, generateKeyPairSync } from 'node:crypto';
import { createGooglePlayRtdnVerifier, GooglePlayRtdnAuthError } from '../src/google-play-rtdn.mjs';

const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const publicPem = publicKey.export({ type: 'spki', format: 'pem' }).toString();
const now = () => Date.parse('2026-11-21T12:00:00.000Z');
const audience = 'https://guardian.example.com/v1/billing/google-play/rtdn';
const email = 'guardian-rtdn-push@example.iam.gserviceaccount.com';

function base64url(value) {
  return Buffer.from(value).toString('base64url');
}

function signedToken(overrides = {}, signer = privateKey) {
  const header = base64url(JSON.stringify({ alg: 'RS256', kid: 'google-test-key-1', typ: 'JWT' }));
  const claims = base64url(JSON.stringify({
    iss: 'https://accounts.google.com',
    aud: audience,
    email,
    email_verified: true,
    iat: Math.floor(now() / 1000),
    exp: Math.floor(now() / 1000) + 3600,
    ...overrides
  }));
  const unsigned = header + '.' + claims;
  const sign = createSign('RSA-SHA256');
  sign.update(unsigned);
  sign.end();
  return unsigned + '.' + sign.sign(signer).toString('base64url');
}

function makeVerifier(fetchCalls = []) {
  return createGooglePlayRtdnVerifier({
    audience,
    serviceAccountEmail: email,
    now,
    fetchImpl: async url => {
      fetchCalls.push(String(url));
      return new Response(JSON.stringify({ 'google-test-key-1': publicPem }), {
        status: 200,
        headers: { 'content-type': 'application/json', 'cache-control': 'public, max-age=300' }
      });
    }
  });
}

test('validates Google Pub/Sub OIDC signature, audience, email and verified identity', async () => {
  const calls = [];
  const verifier = makeVerifier(calls);
  const result = await verifier.verifyAuthorization('Bearer ' + signedToken());
  assert.equal(result.email, email);
  assert.equal(result.audience, audience);
  assert.equal(calls.length, 1);
});

test('caches Google public signing certificates across push requests', async () => {
  const calls = [];
  const verifier = makeVerifier(calls);
  await verifier.verifyAuthorization('Bearer ' + signedToken());
  await verifier.verifyAuthorization('Bearer ' + signedToken());
  assert.equal(calls.length, 1);
});

test('rejects wrong audience, email, issuer and unverified service account claims', async () => {
  const verifier = makeVerifier();
  for (const overrides of [
    { aud: 'https://attacker.example.com' },
    { email: 'other@example.iam.gserviceaccount.com' },
    { iss: 'https://attacker.example.com' },
    { email_verified: false }
  ]) {
    await assert.rejects(
      verifier.verifyAuthorization('Bearer ' + signedToken(overrides)),
      error => error instanceof GooglePlayRtdnAuthError && error.code === 'rtdn_claims_invalid'
    );
  }
});

test('rejects expired and future-issued tokens', async () => {
  const verifier = makeVerifier();
  await assert.rejects(
    verifier.verifyAuthorization('Bearer ' + signedToken({ exp: Math.floor(now() / 1000) - 1 })),
    error => error instanceof GooglePlayRtdnAuthError && error.code === 'rtdn_claims_invalid'
  );
  await assert.rejects(
    verifier.verifyAuthorization('Bearer ' + signedToken({ iat: Math.floor(now() / 1000) + 120 })),
    error => error instanceof GooglePlayRtdnAuthError && error.code === 'rtdn_claims_invalid'
  );
});

test('rejects tampered signatures and malformed authorization headers', async () => {
  const verifier = makeVerifier();
  const token = signedToken();
  const parts = token.split('.');
  const tampered = parts[0] + '.' + parts[1] + '.' + Buffer.from('not-a-signature').toString('base64url');
  await assert.rejects(
    verifier.verifyAuthorization('Bearer ' + tampered),
    error => error instanceof GooglePlayRtdnAuthError && error.code === 'rtdn_signature_invalid'
  );
  await assert.rejects(
    verifier.verifyAuthorization('Basic ' + token),
    error => error instanceof GooglePlayRtdnAuthError && error.code === 'rtdn_authorization_invalid'
  );
});

test('fails closed when the expected audience or service account is not configured', async () => {
  const verifier = createGooglePlayRtdnVerifier({ audience: '', serviceAccountEmail: email, now });
  await assert.rejects(
    verifier.verifyAuthorization('Bearer ' + signedToken()),
    error => error instanceof GooglePlayRtdnAuthError && error.code === 'rtdn_not_configured' && error.status === 503
  );
});
