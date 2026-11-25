import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomBytes, verify } from 'node:crypto';
import { adminTokenMatches, hashLicenseKey, issueEntitlementToken, newLicenseKey, validateDeviceBinding, validateLicenseIssueParameters, verifyLicenseKey } from '../src/license-core.mjs';

const device = randomBytes(32).toString('hex');
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const pepper = 'test-only-lookup-secret-32-chars-min';
const expiresAt = new Date(Date.now() + 86400000).toISOString();

test('license issuance parameters accept explicit defaults and fixed plans', () => {
  assert.deepEqual(validateLicenseIssueParameters(), {
    maxDevices: 1, validDays: 365, planCode: 'custom'
  });
  assert.deepEqual(validateLicenseIssueParameters({
    maxDevices: 3, validDays: 90, planCode: 'quarterly_90d'
  }), {
    maxDevices: 3, validDays: 90, planCode: 'quarterly_90d'
  });
});

test('license issuance rejects malformed values rather than silently defaulting', () => {
  for (const input of [
    { maxDevices: '3', validDays: 30, planCode: 'custom' },
    { maxDevices: 0, validDays: 30, planCode: 'custom' },
    { maxDevices: 21, validDays: 30, planCode: 'custom' },
    { maxDevices: 1, validDays: '30', planCode: 'custom' },
    { maxDevices: 1, validDays: 31, planCode: 'monthly_30d' },
    { maxDevices: 1, validDays: 30, planCode: 'lifetime' },
    { maxDevices: null, validDays: 30, planCode: 'custom' }
  ]) {
    assert.throws(() => validateLicenseIssueParameters(input), error => error.status === 400);
  }
});

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

test('entitlement payload uses the shared Guardian product claim and is RSA signed', async () => {
  const token = issueEntitlementToken({ licenseRef: '12345678-abcd', deviceBinding: device, expiresAt, privateKey });
  const payload = Buffer.from(token.payload, 'utf8');
  assert.equal(verify('RSA-SHA256', payload, publicKey, Buffer.from(token.signature, 'base64')), true);
  assert.match(token.payload, /^tokenVersion=1\nproduct=guardian\nentitlement=pro\n/);
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
  const issuedAt = Number(token.payload.match(/issuedAt=(\d+)/)?.[1]);
  const expires = Number(token.payload.match(/expiresAt=(\d+)/)?.[1]);
  assert.equal(expires - issuedAt, 24 * 60 * 60);
  assert.equal(verify('RSA-SHA256', Buffer.from(token.payload), publicKey, Buffer.from(token.signature, 'base64')), true);
});


test('signed entitlement never outlives an earlier license expiry', () => {
  const now = 1_800_000_000_000;
  const licenseExpiry = new Date(now + 15 * 60 * 1000).toISOString();
  const token = issueEntitlementToken({
    licenseRef: '12345678-abcd',
    deviceBinding: device,
    expiresAt: licenseExpiry,
    privateKey,
    now
  });
  const issuedAt = Number(token.payload.match(/issuedAt=(\d+)/)?.[1]);
  const expires = Number(token.payload.match(/expiresAt=(\d+)/)?.[1]);
  assert.equal(expires - issuedAt, 15 * 60);
  assert.equal(verify('RSA-SHA256', Buffer.from(token.payload), publicKey, Buffer.from(token.signature, 'base64')), true);
});

test('signed entitlement rejects an invalid maximum token lifetime', () => {
  for (const maxTtlSeconds of [0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY]) {
    assert.throws(() => issueEntitlementToken({
      licenseRef: '12345678-abcd',
      deviceBinding: device,
      expiresAt,
      privateKey,
      maxTtlSeconds
    }), /최대 유효기간/);
  }
});

test('invalid device binding is rejected', () => {
  assert.throws(() => validateDeviceBinding('bad'));
});

test('admin token comparison rejects mismatched and empty values', () => {
  assert.equal(adminTokenMatches('secret', 'secret'), true);
  assert.equal(adminTokenMatches('wrong', 'secret'), false);
  assert.equal(adminTokenMatches('secret', ''), false);
});


test('license key hashing rejects malformed persisted salt values', async () => {
  const key = newLicenseKey();
  await assert.rejects(hashLicenseKey(key, pepper, 'not-a-hex-salt'), /salt 형식이 올바르지 않습니다/);
  await assert.rejects(hashLicenseKey(key, pepper, 'a'.repeat(30)), /salt 형식이 올바르지 않습니다/);
});
