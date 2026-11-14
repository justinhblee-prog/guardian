import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomBytes, verify } from 'node:crypto';
import { adminTokenMatches, hashLicenseKey, issueEntitlementToken, newLicenseKey, validateDeviceBinding, verifyLicenseKey } from '../src/license-core.mjs';

const device = randomBytes(32).toString('hex');
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const pepper = 'test-only-lookup-secret-32-chars-min';
const expiresAt = new Date(Date.now() + 86400000).toISOString();

test('license keys are random and formatted', () => {
  const a = newLicenseKey();
  const b = newLicenseKey();
  assert.match(a, /^GDN-(?:[A-F0-9]{8}-){5}[A-F0-9]{8}$/);
  assert.notEqual(a, b);
});

test('license key hashes verify without storing plaintext', async () => {
  const key = newLicenseKey();
  const hashed = await hashLicenseKey(key, pepper);
  assert.equal(await verifyLicenseKey(key, hashed.saltHex, hashed.hashHex, pepper), true);
  assert.equal(await verifyLicenseKey(newLicenseKey(), hashed.saltHex, hashed.hashHex, pepper), false);
  assert.equal(hashed.hashHex.includes(key), false);
});

test('entitlement payload matches Android verifier schema and is RSA signed', async () => {
  const token = issueEntitlementToken({ licenseRef: '12345678-abcd', deviceBinding: device, expiresAt, privateKey });
  const payload = Buffer.from(token.payload, 'utf8');
  assert.equal(verify('RSA-SHA256', payload, publicKey, Buffer.from(token.signature, 'base64')), true);
  assert.match(token.payload, /^tokenVersion=1\nproduct=guardian-android\nentitlement=pro\n/);
  assert.match(token.payload, new RegExp(`deviceBinding=${device}$`));
});

test('signed entitlement is capped at 24 hours even for a long-lived license', () => {
  const now = 1_800_000_000_000;
  const longLicenseExpiry = new Date(now + 365 * 24 * 60 * 60 * 1000).toISOString();
  const token = issueEntitlementToken({
    licenseRef: '12345678-abcd',
    deviceBinding: device,
    expiresAt: longLicenseExpiry,
    privateKey,
    now
  });
  const issuedAt = Number(token.payload.match(/issuedAt=(\\d+)/)?.[1]);
  const expires = Number(token.payload.match(/expiresAt=(\\d+)/)?.[1]);
  assert.equal(expires - issuedAt, 24 * 60 * 60);
  const { verify } = await import('node:crypto');
  assert.equal(verify('RSA-SHA256', Buffer.from(token.payload), publicKey, Buffer.from(token.signature, 'base64')), true);
});

test('invalid device binding is rejected', () => {
  assert.throws(() => validateDeviceBinding('bad'));
});

test('admin token comparison rejects mismatched and empty values', () => {
  assert.equal(adminTokenMatches('secret', 'secret'), true);
  assert.equal(adminTokenMatches('wrong', 'secret'), false);
  assert.equal(adminTokenMatches('secret', ''), false);
});
