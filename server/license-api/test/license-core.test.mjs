import test from 'node:test';
import assert from 'node:assert/strict';
import { generateKeyPairSync, randomBytes } from 'node:crypto';
import { adminTokenMatches, hashLicenseKey, issueEntitlementToken, newLicenseKey, validateDeviceBinding, verifyLicenseKey } from '../src/license-core.mjs';

const device = randomBytes(32).toString('hex');
const { privateKey, publicKey } = generateKeyPairSync('rsa', { modulusLength: 2048 });
const expiresAt = new Date(Date.now() + 86400000).toISOString();

test('license keys are random and formatted', () => {
  const a = newLicenseKey();
  const b = newLicenseKey();
  assert.match(a, /^GDN-(?:[A-F0-9]{8}-){2}[A-F0-9]{8}$/);
  assert.notEqual(a, b);
});

test('license key hashes verify without storing plaintext', async () => {
  const key = newLicenseKey();
  const hashed = await hashLicenseKey(key);
  assert.equal(await verifyLicenseKey(key, hashed.saltHex, hashed.hashHex), true);
  assert.equal(await verifyLicenseKey(newLicenseKey(), hashed.saltHex, hashed.hashHex), false);
  assert.equal(hashed.hashHex.includes(key), false);
});

test('entitlement payload matches Android verifier schema and is RSA signed', () => {
  const token = issueEntitlementToken({ licenseRef: '12345678-abcd', deviceBinding: device, expiresAt, privateKey });
  const payload = Buffer.from(token.payload, 'utf8');
  const { verify } = await import('node:crypto');
  assert.equal(verify('RSA-SHA256', payload, publicKey, Buffer.from(token.signature, 'base64')), true);
  assert.match(token.payload, /^tokenVersion=1\nproduct=guardian-android\nentitlement=pro\n/);
  assert.match(token.payload, new RegExp(`deviceBinding=${device}$`));
});

test('invalid device binding is rejected', () => {
  assert.throws(() => validateDeviceBinding('bad'));
});

test('admin token comparison rejects mismatched and empty values', () => {
  assert.equal(adminTokenMatches('secret', 'secret'), true);
  assert.equal(adminTokenMatches('wrong', 'secret'), false);
  assert.equal(adminTokenMatches('secret', ''), false);
});
